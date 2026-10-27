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
    fun environmentPathExcludesJvmServerDirectoryToPreventJliReexec() {
        val root = createTempDir(prefix = "sally-jli-env-path-")
        try {
            val archRoot = File(root, "lib/aarch64").apply { mkdirs() }
            val jli = File(archRoot, "jli/libjli.so").apply { parentFile?.mkdirs(); writeText("jli") }
            val server = File(archRoot, "server/libjvm.so").apply { parentFile?.mkdirs(); writeText("jvm") }

            val environmentDirectories = RuntimeLibraryDirectories.discoverEnvironmentDirectories(File(root, "lib"))
            val internalDirectories = RuntimeLibraryDirectories.discover(File(root, "lib"))

            assertTrue("JLI directory should be in LD_LIBRARY_PATH", environmentDirectories.contains(jli.parentFile.canonicalFile))
            assertTrue("Java 8 architecture parent should be in LD_LIBRARY_PATH", environmentDirectories.contains(archRoot.canonicalFile))
            assertTrue("JVM server directory must be excluded from process LD_LIBRARY_PATH", !environmentDirectories.contains(server.parentFile.canonicalFile))
            assertTrue("JVM server directory must remain in the internal linker path", internalDirectories.contains(server.parentFile.canonicalFile))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun java17EnvironmentPathExcludesServerDirectoryToo() {
        val root = createTempDir(prefix = "sally-jli17-env-path-")
        try {
            val lib = File(root, "lib").apply { mkdirs() }
            val jli = File(lib, "jli/libjli.so").apply { parentFile?.mkdirs(); writeText("jli") }
            val server = File(lib, "server/libjvm.so").apply { parentFile?.mkdirs(); writeText("jvm") }

            val environmentDirectories = RuntimeLibraryDirectories.discoverEnvironmentDirectories(lib)
            assertTrue(environmentDirectories.contains(jli.parentFile.canonicalFile))
            assertTrue(environmentDirectories.contains(lib.canonicalFile))
            assertTrue(!environmentDirectories.contains(server.parentFile.canonicalFile))
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
