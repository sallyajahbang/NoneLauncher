package com.sally.launcher.core.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineCompatibilityInspectorTest {
    @Test
    fun normalizesCommonArchitectureNames() {
        assertEquals("arm64-v8a", EngineCompatibilityInspector.normalizeArchitecture("aarch64"))
        assertEquals("arm64-v8a", EngineCompatibilityInspector.normalizeArchitecture("arm64-v8a"))
        assertEquals("armeabi-v7a", EngineCompatibilityInspector.normalizeArchitecture("armv7l"))
        assertEquals("x86_64", EngineCompatibilityInspector.normalizeArchitecture("amd64"))
        assertEquals("x86", EngineCompatibilityInspector.normalizeArchitecture("i686"))
        assertEquals(null, EngineCompatibilityInspector.normalizeArchitecture("mips"))
    }

    @Test
    fun reportsMissingAndroidEngineBundleAsBlockers() {
        val root = createTempDir(prefix = "sally-engine-check-")
        try {
            val runtime = File(root, "runtime").apply { mkdirs() }
            File(runtime, "release").writeText("OS_ARCH=\"aarch64\"\nJAVA_VERSION=\"17\"\n")
            File(runtime, "bin").mkdirs()
            File(runtime, "bin/java").writeText("test")
            File(runtime, "lib/server").mkdirs()
            File(runtime, "lib/server/libjvm.so").writeText("test")
            File(runtime, "lib/jli").mkdirs()
            File(runtime, "lib/jli/libjli.so").writeText("test")
            val minecraft = File(root, "minecraft").apply { mkdirs() }
            val appNative = File(root, "app-native").apply { mkdirs() }

            val report = EngineCompatibilityInspector.inspect(
                runtimeHome = runtime,
                minecraftRoot = minecraft,
                appNativeLibraryDir = appNative,
                deviceArchitecture = "aarch64",
            )

            assertFalse("The engine bundle is intentionally absent in this stage", report.ready)
            assertTrue(report.blockers.any { it.contains("libpojavexec.so") })
            assertTrue(report.blockers.any { it.contains("patched GLFW classes") })
            assertFalse(report.blockers.any { it.contains("ABI mismatch") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun blocksJavaVersionsWithoutMatchingCaciocavalloToolkit() {
        val root = createTempDir(prefix = "sally-engine-awt-version-")
        try {
            val runtime = File(root, "runtime").apply { mkdirs() }
            File(runtime, "release").writeText("OS_ARCH=\"aarch64\"\nJAVA_VERSION=\"21\"\n")
            File(runtime, "bin").mkdirs()
            File(runtime, "bin/java").writeText("test")
            File(runtime, "lib/server").mkdirs()
            File(runtime, "lib/server/libjvm.so").writeText("test")
            File(runtime, "lib/jli").mkdirs()
            File(runtime, "lib/jli/libjli.so").writeText("test")
            val minecraft = File(root, "minecraft").apply { mkdirs() }
            File(minecraft, "caciocavallo17").mkdirs()
            File(minecraft, "caciocavallo17/version").writeText("1")
            File(minecraft, "caciocavallo17/cacio-tta-1.18-SNAPSHOT.jar").writeText("test")
            File(minecraft, "caciocavallo17/cacio-shared-1.18-SNAPSHOT.jar").writeText("test")

            val report = EngineCompatibilityInspector.inspect(
                runtimeHome = runtime,
                minecraftRoot = minecraft,
                appNativeLibraryDir = File(root, "app-native").apply { mkdirs() },
                deviceArchitecture = "aarch64",
            )

            assertTrue(report.blockers.any { it.contains("supports Java 8 and 17 only") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun reportsRuntimeAbiMismatch() {
        val root = createTempDir(prefix = "sally-engine-abi-")
        try {
            val runtime = File(root, "runtime").apply { mkdirs() }
            File(runtime, "release").writeText("OS_ARCH=\"x86_64\"\n")
            val report = EngineCompatibilityInspector.inspect(
                runtimeHome = runtime,
                minecraftRoot = File(root, "minecraft"),
                appNativeLibraryDir = null,
                deviceArchitecture = "aarch64",
            )
            assertTrue(report.blockers.any { it.contains("ABI mismatch") })
        } finally {
            root.deleteRecursively()
        }
    }
}
