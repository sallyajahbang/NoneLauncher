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
            val classesJar = installer.installPatchedGlfwClasses(minecraftRoot)
            System.loadLibrary("pojavexec")
            CallbackBridge.initializeNativeBuffers()
            val report = installer.inspect(minecraftRoot)
            if (!report.ready) {
                Result(
                    ready = false,
                    details = "Patched GLFW classes installed at ${classesJar.absolutePath}, but required engine libraries are missing: ${report.missingNativeLibraries.joinToString()}.",
                    payload = report,
                )
            } else {
                Result(
                    ready = true,
                    details = "Pojav native bridge loaded; patched GLFW classes and matching Android engine libraries are present.",
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
