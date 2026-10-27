package com.sally.launcher.core.engine

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLibraryDirectoriesTest {
    @Test
    fun includesJava8ArchitectureParentEvenWhenItContainsNoSharedObjects() {
        val root = createTempDir(prefix = "sally-java8-library-path-")
        try {
            val archRoot = File(root, "lib/aarch64").apply { mkdirs() }
            File(archRoot, "jli/libjli.so").apply { parentFile?.mkdirs(); writeText("jli") }
            File(archRoot, "server/libjvm.so").apply { parentFile?.mkdirs(); writeText("jvm") }

            val directories = RuntimeLibraryDirectories.discover(File(root, "lib"))
            assertTrue("JLI directory should be present", directories.contains(File(archRoot, "jli").canonicalFile))
            assertTrue("JVM directory should be present", directories.contains(File(archRoot, "server").canonicalFile))
            assertTrue("JRE architecture parent is required by OpenJDK 8", directories.contains(archRoot.canonicalFile))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun includesJava17LibraryParentForStandardLayout() {
        val root = createTempDir(prefix = "sally-java17-library-path-")
        try {
            val lib = File(root, "lib").apply { mkdirs() }
            File(lib, "jli/libjli.so").apply { parentFile?.mkdirs(); writeText("jli") }
            File(lib, "server/libjvm.so").apply { parentFile?.mkdirs(); writeText("jvm") }

            val directories = RuntimeLibraryDirectories.discover(lib)
            assertTrue("Java 17 JRE lib directory should be included", directories.contains(lib.canonicalFile))
        } finally {
            root.deleteRecursively()
        }
    }
}
