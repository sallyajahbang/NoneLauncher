package com.sally.nonelauncher.core.runtime

import com.sally.nonelauncher.core.LauncherPaths
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Installs a Java runtime from a tar stream into runtime/java-<major>/.
 *
 * The caller provides the (already decompressed) tar stream, so this class does
 * not depend on any particular compression library. On Android the app decodes
 * the bundled `.tar.xz` and passes the result in.
 */
object RuntimeInstaller {

    private const val MARKER = ".installed"

    /**
     * Installs runtime [major] unless the same [version] is already installed.
     *
     * @param version bundled runtime revision; changing it forces a reinstall.
     * @param openTar opens a fresh decompressed tar stream of the runtime.
     * @return the runtime home directory (contains bin/ and lib/).
     */
    fun install(major: Int, version: String, paths: LauncherPaths, openTar: () -> InputStream): File {
        val home = paths.runtimeHome(major)
        val marker = File(home, MARKER)
        if (marker.exists() && marker.readText() == version) return home

        home.deleteRecursively()
        home.mkdirs()
        openTar().use { extractTar(it, home) }

        File(home, "bin/java").let { if (it.exists()) it.setExecutable(true, false) }
        marker.writeText(version)
        return home
    }

    /**
     * Extracts [input] (an uncompressed tar) into [dest].
     * Entries that would land outside [dest] are rejected.
     *
     * @return the number of entries written.
     */
    fun extractTar(input: InputStream, dest: File): Int {
        dest.mkdirs()
        val root = dest.canonicalFile
        var count = 0

        TarArchiveInputStream(input).use { tar ->
            var entry = tar.nextEntry
            while (entry != null) {
                val target = File(root, entry.name).canonicalFile
                require(target == root || target.path.startsWith(root.path + File.separator)) {
                    "unsafe path in archive: ${entry.name}"
                }

                when {
                    entry.isDirectory -> target.mkdirs()
                    entry.isSymbolicLink -> {
                        target.parentFile?.mkdirs()
                        if (target.exists() || Files.isSymbolicLink(target.toPath())) target.delete()
                        Files.createSymbolicLink(target.toPath(), Paths.get(entry.linkName))
                    }
                    entry.isFile -> {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { tar.copyTo(it) }
                        // Keep the executable bit from the archive (any of owner/group/other x).
                        if (entry.mode and 0x49 != 0) target.setExecutable(true, false)
                    }
                }
                count++
                entry = tar.nextEntry
            }
        }
        return count
    }
}
