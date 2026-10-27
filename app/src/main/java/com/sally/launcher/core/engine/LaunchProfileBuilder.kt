package com.sally.launcher.core.engine

import android.content.Context
import com.sally.launcher.data.model.MinecraftVersion
import com.sally.launcher.data.storage.LauncherStorage
import com.sally.launcher.data.runtime.RuntimeManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

/**
 * Resolves the launch data from Mojang's version JSON without starting a JVM.
 * The result is a validated launch profile. The Pojav JLI executor is a separate layer and
 * refuses to execute until a validated account profile, runtime payload, surface and native bridge
 * are all ready.
 */
class LaunchProfileBuilder(context: Context) {
    private val appContext = context.applicationContext
    private val minecraftRoot = LauncherStorage.minecraftRoot(appContext)
    private val versionsRoot = File(minecraftRoot, "versions")
    private val librariesRoot = File(minecraftRoot, "libraries")
    private val assetsRoot = File(minecraftRoot, "assets")
    private val runtimeManager = RuntimeManager(appContext)

    data class LaunchProfile(
        val versionId: String,
        val requiredJavaMajor: Int,
        val runtimeMajor: Int,
        val javaHome: File,
        val mainClass: String,
        val clientJar: File,
        val classpathEntries: List<File>,
        val jvmArguments: List<String>,
        val gameArguments: List<String>,
        val workingDirectory: File,
        val assetIndexId: String,
        val hasValidAccountSession: Boolean = false,
        val accountType: MinecraftAccountType? = null,
        val sessionUsername: String? = null,
        val sessionUuid: String? = null,
    ) {
        val classpath: String get() = classpathEntries.joinToString(File.pathSeparator) { it.absolutePath }
    }

    fun prepare(
        version: MinecraftVersion,
        playerName: String = DEFAULT_PLAYER_NAME,
        session: MinecraftLaunchSession? = null,
    ): LaunchProfile {
        session?.validationError()?.let { throw IOException(it) }
        val resolvedPlayerName = session?.username ?: playerName
        validatePlayerName(resolvedPlayerName)
        val id = safeId(version.id)
        val versionDirectory = File(versionsRoot, id)
        val metadataFile = File(versionDirectory, "$id.json")
        val clientJar = File(versionDirectory, "$id.jar")
        if (!metadataFile.isFile || metadataFile.length() <= 0L) {
            throw IOException("Version metadata is missing. Install version files first.")
        }
        if (!clientJar.isFile || clientJar.length() <= 0L) {
            throw IOException("Minecraft client JAR is missing. Install version files first.")
        }

        val metadata = try {
            JSONObject(metadataFile.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            throw IOException("Version metadata is unreadable", e)
        }
        val actualId = metadata.optString("id")
        if (actualId.isNotBlank() && actualId != version.id) {
            throw IOException("Version metadata mismatch: expected ${version.id}, got $actualId")
        }
        if (metadata.has("inheritsFrom")) {
            val parent = safeId(metadata.optString("inheritsFrom"))
            val parentFile = File(File(versionsRoot, parent), "$parent.json")
            if (!parentFile.isFile) {
                throw IOException("This profile inherits from Minecraft $parent, but its parent metadata is not installed")
            }
            // The vanilla catalog normally ships complete metadata. Mod-loader inheritance requires
            // a separate merge strategy, so this builder refuses to silently create a wrong profile.
            throw IOException("Inherited/mod-loader profiles are not supported by this profile builder yet")
        }

        val requiredJava = metadata.optJSONObject("javaVersion")?.optInt("majorVersion", 8)
            ?.takeIf { it in 1..99 } ?: 8
        val runtime = runtimeManager.installedRuntimes()
            .filter { it.majorVersion >= requiredJava }
            .minByOrNull { it.majorVersion }
            ?: throw IOException(
                if (requiredJava > 25) {
                    "Minecraft ${version.id} requires Java $requiredJava, but this build's Android AWT toolkit supports Java 8, 17, 21, and 25 only."
                } else {
                    "No compatible Java runtime is installed. Install Java $requiredJava or newer first."
                }
            )

        val mainClass = metadata.optString("mainClass").takeIf { it.isNotBlank() }
            ?: throw IOException("Version metadata does not declare a mainClass")
        val libraryResult = resolveClasspath(metadata.optJSONArray("libraries"))
        if (libraryResult.missing.isNotEmpty()) {
            val examples = libraryResult.missing.take(5).joinToString(", ") { it.name }
            val suffix = if (libraryResult.missing.size > 5) " and ${libraryResult.missing.size - 5} more" else ""
            throw IOException("${libraryResult.missing.size} required library files are missing: $examples$suffix. Install version files again.")
        }
        // The Android-patched GLFW API must win class resolution over vanilla LWJGL jars.
        // Keep this entry first; otherwise the desktop GLFW class can load desktop natives and crash.
        val patchedGlfwJar = PojavEnginePayloadInstaller(appContext)
            .installPatchedGlfwClasses(minecraftRoot)
        val classpathEntries = (listOf(patchedGlfwJar) + libraryResult.present + clientJar)
            .distinctBy { it.canonicalPath }
        val classpath = classpathEntries.joinToString(File.pathSeparator) { it.absolutePath }
        val assetIndexMetadata = metadata.optJSONObject("assetIndex")
        val rawAssetIndexId = assetIndexMetadata?.optString("id")
            ?.takeIf { it.isNotBlank() }
            ?: metadata.optString("assets", "legacy").ifBlank { "legacy" }
        val assetIndexId = safeId(rawAssetIndexId)
        if (assetIndexMetadata != null) {
            val indexFile = File(File(assetsRoot, "indexes"), "$assetIndexId.json")
            if (!indexFile.isFile || indexFile.length() <= 0L) {
                throw IOException("Asset index $assetIndexId is missing. Install version files again.")
            }
            try {
                JSONObject(indexFile.readText(Charsets.UTF_8))
            } catch (e: Exception) {
                throw IOException("Asset index $assetIndexId is invalid. Install version files again.", e)
            }
        }
        val workingDirectory = File(minecraftRoot, "game").apply {
            if (!exists() && !mkdirs()) throw IOException("Cannot create Minecraft game directory")
            if (!isDirectory) throw IOException("Minecraft game path is not a directory")
        }

        val uuid = session?.normalizedUuid
            ?: UUID.nameUUIDFromBytes("OfflinePlayer:$resolvedPlayerName".toByteArray(StandardCharsets.UTF_8)).toString().replace("-", "")
        val nativesDirectory = File(minecraftRoot, "natives")
        val placeholders = mapOf(
            "auth_player_name" to resolvedPlayerName,
            "version_name" to version.id,
            "game_directory" to workingDirectory.absolutePath,
            "assets_root" to assetsRoot.absolutePath,
            "assets_index_name" to assetIndexId,
            "auth_uuid" to uuid,
            "auth_access_token" to (session?.accessToken ?: "0"),
            "user_type" to (session?.userType ?: "legacy"),
            "user_properties" to (session?.userPropertiesJson ?: "{}"),
            "clientid" to (session?.clientId?.ifBlank { "0" } ?: "0"),
            "auth_xuid" to (session?.xuid?.ifBlank { "0" } ?: "0"),
            "version_type" to (metadata.optString("type").ifBlank { version.type }),
            "launcher_name" to "SallyLauncher",
            "launcher_version" to "0.5.0",
            "launcher_brand" to "SallyLauncher",
            "classpath" to classpath,
            "classpath_separator" to File.pathSeparator,
            "library_directory" to librariesRoot.absolutePath,
            "natives_directory" to nativesDirectory.absolutePath,
            "auth_session" to "0",
            "game_assets" to assetsRoot.absolutePath,
            "resolution_width" to GameDisplaySize.resolve(appContext).first.toString(),
            "resolution_height" to GameDisplaySize.resolve(appContext).second.toString(),
            "quickPlayPath" to "",
        )

        val jvmArguments = resolveModernArguments(
            metadata.optJSONObject("arguments")?.optJSONArray("jvm"),
            placeholders,
        ).ifEmpty {
            listOf(
                "-Djava.library.path=${nativesDirectory.absolutePath}",
                "-Djava.home=${runtime.home.absolutePath}",
                "-Djava.io.tmpdir=${File(minecraftRoot, "tmp").absolutePath}",
                "-Dfile.encoding=UTF-8",
                "-cp",
                classpath,
            )
        }

        val modernGameArguments = metadata.optJSONObject("arguments")?.optJSONArray("game")
        val gameArguments = if (modernGameArguments != null) {
            resolveModernArguments(modernGameArguments, placeholders)
        } else {
            val legacy = metadata.optString("minecraftArguments")
            if (legacy.isBlank()) {
                throw IOException("Version metadata contains neither modern game arguments nor minecraftArguments")
            }
            tokenizeLegacyArguments(legacy).map { expand(it, placeholders) }
        }

        return LaunchProfile(
            versionId = version.id,
            requiredJavaMajor = requiredJava,
            runtimeMajor = runtime.majorVersion,
            javaHome = runtime.home,
            mainClass = mainClass,
            clientJar = clientJar,
            classpathEntries = classpathEntries,
            jvmArguments = jvmArguments,
            gameArguments = gameArguments,
            workingDirectory = workingDirectory,
            assetIndexId = assetIndexId,
            hasValidAccountSession = session != null && session.validationError() == null,
            accountType = session?.accountType,
            sessionUsername = session?.username,
            sessionUuid = session?.normalizedUuid,
        )
    }

    private data class ClasspathResolution(val present: List<File>, val missing: List<File>)

    private fun resolveClasspath(libraries: JSONArray?): ClasspathResolution {
        val present = mutableListOf<File>()
        val missing = mutableListOf<File>()
        if (libraries == null) return ClasspathResolution(present, missing)
        for (i in 0 until libraries.length()) {
            val library = libraries.optJSONObject(i) ?: continue
            if (!rulesAllow(library.optJSONArray("rules"))) continue
            val downloads = library.optJSONObject("downloads")
            val artifact = downloads?.optJSONObject("artifact")
            // Native-only entries (a `natives` map, or downloads that list only classifiers) are not Java
            // classpath entries. The installer never fetches their base JAR, so skip them here too.
            if (artifact == null && (downloads != null || library.has("natives"))) continue
            val relativePath = artifact?.optString("path")?.takeIf { it.isNotBlank() }
                ?: mavenPath(library.optString("name"))
                ?: continue // Classifier-only coordinates are native payloads, not Java classpath entries.
            val safePath = safeRelativePath(relativePath) ?: throw IOException("Unsafe library path in version metadata")
            val file = File(librariesRoot, safePath)
            val canonicalRoot = librariesRoot.canonicalFile
            if (!file.canonicalPath.startsWith(canonicalRoot.path + File.separator)) {
                throw IOException("Library path escapes the library directory")
            }
            if (file.isFile && file.length() > 0L) present += file else missing += file
        }
        return ClasspathResolution(present.distinctBy { it.canonicalPath }, missing.distinctBy { it.canonicalPath })
    }

    private fun mavenPath(coordinate: String): String? {
        val parts = coordinate.split(':')
        if (parts.size !in 3..4) return null
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.contains('/') || it.contains('\\') }) return null
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val versionWithExtension = parts[2]
        val version = versionWithExtension.substringBefore('@')
        val extension = versionWithExtension.substringAfter('@', "jar")
        if (version.isBlank() || extension.isBlank() || !extension.matches(Regex("[A-Za-z0-9]+"))) return null
        val classifier = parts.getOrNull(3)?.let { "-$it" }.orEmpty()
        return "$group/$artifact/$version/$artifact-$version$classifier.$extension"
    }

    private fun rulesAllow(rules: JSONArray?): Boolean {
        if (rules == null) return true
        var allowed = false
        for (index in 0 until rules.length()) {
            val rule = rules.optJSONObject(index) ?: continue
            if (!ruleMatches(rule)) continue
            allowed = rule.optString("action") == "allow"
        }
        return allowed
    }

    private fun ruleMatches(rule: JSONObject): Boolean {
        val os = rule.optJSONObject("os")
        if (os != null) {
            val name = os.optString("name")
            if (name.isNotBlank() && name != "linux") return false // Android presents a Linux kernel to Mojang metadata.
            val requiredArch = os.optString("arch")
            if (requiredArch.isNotBlank() && !archMatches(requiredArch, System.getProperty("os.arch").orEmpty())) return false
            val versionRegex = os.optString("version")
            if (versionRegex.isNotBlank() && !runCatching {
                    Regex(versionRegex).containsMatchIn(System.getProperty("os.version").orEmpty())
                }.getOrDefault(false)
            ) return false
        }
        val features = rule.optJSONObject("features")
        if (features != null) {
            val keys = features.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val expected = features.optBoolean(key, false)
                if ((featureValues[key] ?: false) != expected) return false
            }
        }
        return true
    }

    private fun archMatches(required: String, actualRaw: String): Boolean {
        val requiredNormalized = normalizeArch(required)
        val actualNormalized = normalizeArch(actualRaw)
        return requiredNormalized == actualNormalized
    }

    private fun normalizeArch(value: String): String = when (value.lowercase(Locale.ROOT)) {
        "aarch64", "arm64", "arm64-v8a" -> "aarch64"
        "x86_64", "amd64" -> "x86_64"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        else -> value.lowercase(Locale.ROOT)
    }

    private fun resolveModernArguments(array: JSONArray?, placeholders: Map<String, String>): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (index in 0 until array.length()) {
            when (val item = array.opt(index)) {
                is String -> result += expand(item, placeholders)
                is JSONObject -> {
                    if (!rulesAllow(item.optJSONArray("rules"))) continue
                    when (val value = item.opt("value")) {
                        is String -> result += expand(value, placeholders)
                        is JSONArray -> for (valueIndex in 0 until value.length()) {
                            val valueItem = value.optString(valueIndex, "")
                            if (valueItem.isNotEmpty()) result += expand(valueItem, placeholders)
                        }
                    }
                }
            }
        }
        return result
    }

    private fun expand(argument: String, placeholders: Map<String, String>): String {
        val pattern = Regex("\\$\\{([^}]+)\\}")
        return pattern.replace(argument) { match -> placeholders[match.groupValues[1]] ?: match.value }
    }

    private fun tokenizeLegacyArguments(input: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false
        for (character in input) {
            if (escaping) {
                current.append(character)
                escaping = false
                tokenStarted = true
                continue
            }
            when {
                character == '\\' && quote != null -> escaping = true
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> {
                    quote = character
                    tokenStarted = true
                }
                quote == null && character.isWhitespace() -> {
                    if (tokenStarted) {
                        tokens += current.toString()
                        current.setLength(0)
                        tokenStarted = false
                    }
                }
                else -> {
                    current.append(character)
                    tokenStarted = true
                }
            }
        }
        if (escaping) current.append('\\')
        if (quote != null) throw IOException("Unterminated quote in legacy Minecraft arguments")
        if (tokenStarted) tokens += current.toString()
        return tokens
    }

    private fun safeRelativePath(value: String): String? {
        if (value.isBlank() || value.startsWith('/') || value.startsWith('\\')) return null
        val normalized = value.replace('\\', '/')
        if (normalized.split('/').any { it.isBlank() || it == "." || it == ".." }) return null
        return normalized
    }

    private fun safeId(value: String): String {
        val safe = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        require(safe.isNotBlank() && safe != "." && safe != "..") { "Invalid Minecraft version id" }
        return safe
    }

    private fun validatePlayerName(value: String) {
        if (!value.matches(Regex("[A-Za-z0-9_]{1,16}"))) {
            throw IOException("Offline player names must be 1–16 letters, digits, or underscores")
        }
    }

    private companion object {
        const val DEFAULT_PLAYER_NAME = "Sally"
        val featureValues = mapOf(
            "is_demo_user" to false,
            "has_custom_resolution" to false,
            "has_quick_plays_support" to false,
            "is_quick_play_singleplayer" to false,
            "is_quick_play_multiplayer" to false,
            "is_quick_play_realms" to false,
        )
    }
}
