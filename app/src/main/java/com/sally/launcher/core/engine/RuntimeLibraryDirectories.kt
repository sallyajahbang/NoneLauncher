package com.sally.launcher.core.engine

import java.io.File
import java.util.LinkedHashSet

/** Finds JRE library directories and their immediate parents for OpenJDK JLI compatibility. */
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

        // JRE 8 on Android uses lib/<arch>/{jli,server}; its launcher expects lib/<arch>
        // in LD_LIBRARY_PATH even if that parent contains no .so directly. JDK 17 generally
        // uses lib/{jli,server}, so the same rule adds lib/ as the required parent.
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
}
