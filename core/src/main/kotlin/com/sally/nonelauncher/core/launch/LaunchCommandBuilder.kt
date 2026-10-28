package com.sally.nonelauncher.core.launch

import com.sally.nonelauncher.core.LauncherPaths
import com.sally.nonelauncher.core.auth.Account
import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.mojang.ArgumentEntry
import com.sally.nonelauncher.core.mojang.VersionMeta
import com.sally.nonelauncher.core.mojang.RuleEvaluator
import java.io.File

/** Everything that is known at launch time and not part of the version metadata. */
data class InstalledVersion(
    val meta: VersionMeta,
    val clientJar: File,
    /** Absolute paths of the non-native libraries, in classpath order. */
    val libraryClasspath: List<String>,
    val nativesDir: File,
)

data class LaunchOptions(
    val account: Account,
    val gameDir: File,
    /** Path to the java binary that will run the game. */
    val javaExecutable: String,
    val maxMemoryMb: Int = 2048,
    val width: Int = 1280,
    val height: Int = 720,
    val extraJvmArgs: List<String> = emptyList(),
    val extraGameArgs: List<String> = emptyList(),
)

data class LaunchCommand(
    val executable: String,
    val args: List<String>,
    val workingDir: File,
)

/**
 * Turns installed version metadata + options into a concrete command line.
 *
 * Pure function: no I/O besides reading [LauncherPaths.libraries] paths as strings,
 * so it is easy to unit-test.
 */
class LaunchCommandBuilder(
    private val paths: LauncherPaths,
    private val platform: RuntimeContext,
) {

    fun build(installed: InstalledVersion, options: LaunchOptions): LaunchCommand {
        val meta = installed.meta
        val classpath = (installed.libraryClasspath + installed.clientJar.absolutePath)
            .joinToString(File.pathSeparator)

        val vars = mapOf(
            "auth_player_name" to options.account.username,
            "auth_uuid" to options.account.uuid,
            "auth_access_token" to options.account.accessToken,
            "auth_session" to options.account.accessToken,
            "user_type" to options.account.userType,
            "user_properties" to "{}",
            "auth_xuid" to "",
            "clientid" to "",
            "version_name" to meta.id,
            "version_type" to meta.type,
            "game_directory" to options.gameDir.absolutePath,
            "assets_root" to paths.assets.absolutePath,
            "assets_index_name" to meta.assetIndex.id,
            "game_assets" to paths.assets.absolutePath,
            "natives_directory" to installed.nativesDir.absolutePath,
            "library_directory" to paths.libraries.absolutePath,
            "classpath" to classpath,
            "classpath_separator" to File.pathSeparator,
            "launcher_name" to "NoneLauncher",
            "launcher_version" to "0.1.0",
            "resolution_width" to options.width.toString(),
            "resolution_height" to options.height.toString(),
        )

        val jvm = mutableListOf<String>()
        jvm += "-Xmx${options.maxMemoryMb}M"
        jvm += options.extraJvmArgs

        if (meta.jvmArguments.isEmpty()) {
            // Very old metadata has no argument list; these are what the vanilla launcher adds.
            jvm += "-Djava.library.path=${installed.nativesDir.absolutePath}"
            jvm += "-cp"
            jvm += classpath
        } else {
            jvm += expand(meta.jvmArguments, vars)
        }

        val game = if (meta.legacyGameArguments != null) {
            meta.legacyGameArguments.split(" ").filter { it.isNotBlank() }
                .map { substitute(it, vars) }
        } else {
            expand(meta.gameArguments, vars)
        }

        val args = jvm + listOf(meta.mainClass) + game + options.extraGameArgs

        options.gameDir.mkdirs()
        return LaunchCommand(
            executable = options.javaExecutable,
            args = args,
            workingDir = options.gameDir,
        )
    }

    private fun expand(entries: List<ArgumentEntry>, vars: Map<String, String>): List<String> {
        val out = mutableListOf<String>()
        for (entry in entries) {
            if (!RuleEvaluator.isAllowed(entry.rules, platform)) continue
            entry.values.forEach { out += substitute(it, vars) }
        }
        return out
    }

    companion object {
        /**
         * Replaces `${name}` with the value from [vars]; unknown names are left as-is.
         *
         * Plain string replacement on purpose: a regex here broke on Android's regex
         * engine (PatternSyntaxException) and took down the whole launcher core.
         */
        fun substitute(template: String, vars: Map<String, String>): String {
            var out = template
            for ((key, value) in vars) {
                out = out.replace("\${" + key + "}", value)
            }
            return out
        }
    }
}
