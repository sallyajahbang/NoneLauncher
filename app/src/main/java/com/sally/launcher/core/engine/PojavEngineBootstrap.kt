package com.sally.launcher.core.engine

import android.content.Context
import org.lwjgl.glfw.CallbackBridge
import java.io.File

/** Loads the Android VM half of the Pojav bridge and installs matching patched GLFW classes. */
object PojavEngineBootstrap {
    data class Result(
        val ready: Boolean,
        val details: String,
        val payload: PojavEnginePayloadInstaller.PayloadReport?,
    )

    @Synchronized
    fun prepare(context: Context, minecraftRoot: File = File(context.filesDir, "minecraft")): Result {
        val appContext = context.applicationContext
        val installer = PojavEnginePayloadInstaller(appContext)
        return try {
            CallbackBridge.installContext(appContext)
            val runtimeComponents = installer.installCaciocavalloComponents(minecraftRoot)
            val classesJar = installer.installPatchedGlfwClasses(minecraftRoot)
            AwtBridgeHost.install(appContext)
            System.loadLibrary("pojavexec")
            System.loadLibrary("exithook")
            System.loadLibrary("pojavexec_awt")
            CallbackBridge.initializeNativeBuffers()
            val report = installer.inspect(minecraftRoot)
            if (!report.ready) {
                Result(
                    ready = false,
                    details = buildString {
                        append("Patched GLFW classes installed at ${classesJar.absolutePath}. ")
                        if (report.missingNativeLibraries.isNotEmpty()) {
                            append("Missing native libraries: ${report.missingNativeLibraries.joinToString()}. ")
                        }
                        if (report.missingComponents.isNotEmpty()) {
                            append("Missing runtime components: ${report.missingComponents.joinToString()}. ")
                        }
                    }.trim(),
                    payload = report,
                )
            } else {
                Result(
                    ready = true,
                    details = "Pojav JNI and AWT bridges loaded; patched GLFW classes and ${runtimeComponents.size} Caciocavallo runtime components are installed.",
                    payload = report,
                )
            }
        } catch (error: Throwable) {
            Result(
                ready = false,
                details = "Could not prepare the Pojav engine payload: ${error.message ?: error.javaClass.simpleName}",
                payload = runCatching { installer.inspect(minecraftRoot) }.getOrNull(),
            )
        }
    }
}
