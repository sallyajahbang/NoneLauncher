package com.sally.nonelauncher.core.mojang

import com.sally.nonelauncher.core.model.RuntimeContext

/** A library file that must exist on disk, relative to the libraries directory. */
data class ResolvedLibrary(
    val relativePath: String,
    val url: String,
    val sha1: String?,
    val size: Long?,
    /** True for native jars that must be extracted instead of put on the classpath. */
    val isNative: Boolean,
)

object LibraryResolver {

    const val DEFAULT_MAVEN = "https://libraries.minecraft.net/"

    /**
     * Returns the libraries that apply to [ctx], in declaration order.
     * Libraries whose rules disallow them on this platform are dropped.
     */
    fun resolve(libraries: List<LibrarySpec>, ctx: RuntimeContext): List<ResolvedLibrary> {
        val result = mutableListOf<ResolvedLibrary>()
        for (lib in libraries) {
            if (!RuleEvaluator.isAllowed(lib.rules, ctx)) continue

            val nativeKey = lib.natives[ctx.osName]
            if (nativeKey != null) {
                val classifier = nativeKey.replace("\${arch}", ctx.archBits)
                val artifact = lib.classifiers[classifier] ?: continue
                result += artifact.toResolved(
                    fallbackPath = mavenPath(lib.name, classifierOverride = classifier),
                    isNative = true,
                )
                continue
            }

            if (lib.artifact != null) {
                result += lib.artifact.toResolved(
                    fallbackPath = mavenPath(lib.name),
                    isNative = false,
                )
            } else {
                // Libraries without a `downloads` block (e.g. some modloader entries).
                val base = lib.baseUrl ?: DEFAULT_MAVEN
                val path = mavenPath(lib.name)
                result += ResolvedLibrary(
                    relativePath = path,
                    url = base + path,
                    sha1 = null,
                    size = null,
                    isNative = false,
                )
            }
        }
        return result
    }

    private fun DownloadArtifact.toResolved(fallbackPath: String, isNative: Boolean) = ResolvedLibrary(
        relativePath = path ?: fallbackPath,
        url = url,
        sha1 = sha1,
        size = size,
        isNative = isNative,
    )

    /**
     * Converts a Maven coordinate into a repository-relative path.
     * `group:artifact:version[:classifier]` -> `group/as/artifact/version/artifact-version[-classifier].jar`
     */
    fun mavenPath(coordinate: String, classifierOverride: String? = null): String {
        val parts = coordinate.split(":")
        require(parts.size >= 3) { "invalid maven coordinate: $coordinate" }
        val group = parts[0].replace('.', '/')
        val artifact = parts[1]
        val version = parts[2]
        val classifier = classifierOverride ?: parts.getOrNull(3)
        val fileName = buildString {
            append(artifact).append('-').append(version)
            if (classifier != null) append('-').append(classifier)
            append(".jar")
        }
        return "$group/$artifact/$version/$fileName"
    }
}
