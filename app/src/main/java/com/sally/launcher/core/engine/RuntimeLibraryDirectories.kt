package com.sally.launcher.core.engine

import java.io.File
import java.util.LinkedHashSet

/** Discovers JRE library directories for Android linker setup and safe JLI environment construction. */
internal object RuntimeLibraryDirectories {
    private const val MAX_SCANNED_ENTRIES = 5_000

    fun discover(root: File): List<File> {
        if (!root.isDirectory) return emptyList()
        val rootCanonical = runCatching { root.canonicalFile }.getOrNull() ?: return emptyList()
        val found = LinkedHashSet<File>()
        val visited = HashSet<String>()
        var budget = 0

        fun visit(directory: File, depth: Int) {
            if (depth > 7 || budget >= MAX_SCANNED_ENTRIES) return
            val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return
            val rootPath = rootCanonical.path
            if (canonical.path != rootPath && !canonical.path.startsWith(rootPath + File.separator)) return
            if (!visited.add(canonical.path)) return
            budget++
            val children = canonical.listFiles().orEmpty()
            if (children.any { it.isFile && it.name.endsWith(".so") }) found += canonical
            for (child in children) {
                if (budget >= MAX_SCANNED_ENTRIES) break
                if (child.isDirectory) visit(child, depth + 1)
            }
        }

        visit(rootCanonical, 0)

        // Build the full Android internal linker search path. Java 8 on Android uses
        // lib/<arch>/{jli,server}; Java 17 generally uses lib/{jli,server}. Parent directories
        // are useful for resolving sibling dependencies, but this full list must never be
        // copied wholesale into the process LD_LIBRARY_PATH passed to OpenJDK JLI.
        val withRequiredParents = LinkedHashSet<File>()
        for (directory in found) {
            withRequiredParents += directory
            val parent = directory.parentFile?.let { runCatching { it.canonicalFile }.getOrNull() }
            if (parent != null &&
                (parent.path == rootCanonical.path || parent.path.startsWith(rootCanonical.path + File.separator))
            ) {
                withRequiredParents += parent
            }
        }
        return withRequiredParents.toList().sortedBy { it.absolutePath }
    }

    /**
     * LD_LIBRARY_PATH must not contain the directory that directly contains libjvm.so.
     * OpenJDK 8/17 JLI treats that as a signal to re-exec $JAVA_HOME/bin/java to refresh
     * the environment. That exec is not usable for an embedded JVM in Android app storage.
     * Upstream Pojav keeps the JLI directory and its architecture/lib parent in the process
     * environment, and adds the server/client directory only to Android's internal linker path.
     */
    fun discoverEnvironmentDirectories(root: File): List<File> {
        val libraryDirectories = discover(root)
        val jliDirectory = libraryDirectories.firstOrNull { File(it, "libjli.so").isFile }
            ?: return emptyList()
        val candidates = LinkedHashSet<File>()
        candidates += jliDirectory.canonicalFile
        jliDirectory.parentFile?.let { candidates += it.canonicalFile }
        return candidates.filterNot { File(it, "libjvm.so").isFile }
    }
}
