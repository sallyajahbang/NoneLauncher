package com.sally.launcher.core.engine

import android.content.Context
import android.os.Looper
import android.system.Os
import com.oracle.dalvik.VMLauncher
import net.kdt.pojavlaunch.utils.JREUtils
import java.io.File
import java.io.IOException

/**
 * Executes the prepared launch inside the Android process using the upstream Pojav JNI/JLI entry.
 * Call only from a dedicated worker thread, after PojavGameSurfaceView reports a bound surface.
 * JLI is process-global and may not be safely restarted after it returns.
 */
class PojavJliLaunchExecutor(context: Context) {
    private val appContext = context.applicationContext

    /** Returns the Java launcher exit code. A successful return does not by itself prove gameplay. */
    fun launch(
        profile: LaunchProfileBuilder.LaunchProfile,
        spec: JliLaunchSpec,
        session: MinecraftLaunchSession,
        surfaceReady: Boolean,
    ): Int {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw IllegalStateException("JLI_Launch must run on a dedicated worker thread, never the UI thread.")
        }
        session.validationError()?.let { throw IOException(it) }
        if (!profile.hasValidAccountSession || profile.accountType != session.accountType) {
            throw IOException("The launch profile was not built with this validated account type.")
        }
        if (profile.sessionUsername != session.username || profile.sessionUuid != session.normalizedUuid) {
            throw IOException("The account session changed after the launch profile was built. Prepare the profile again.")
        }
        if (!surfaceReady) {
            throw IOException("The Pojav Android surface is not bound yet. Wait until the landscape game surface is ready.")
        }
        if (spec.blockers.isNotEmpty()) {
            throw IOException("Minecraft launch is blocked:\n${spec.blockers.joinToString("\n") { "• $it" }}")
        }
        if (spec.argv.isEmpty() || spec.argv.first() != "java") {
            throw IOException("The native launch request has an invalid argv[0].")
        }
        if (spec.argv.any { it.indexOf('\u0000') >= 0 }) {
            throw IOException("The native launch request contains a NUL character.")
        }
        if (spec.runtimeHome.canonicalFile != profile.javaHome.canonicalFile) {
            throw IOException("The launch specification points to a different Java runtime than the profile.")
        }

        val minecraftRoot = File(appContext.filesDir, "minecraft").canonicalFile
        val payload = PojavEnginePayloadInstaller(appContext).inspect(minecraftRoot)
        if (!payload.ready) {
            throw IOException(
                "The Android Pojav payload is incomplete. Missing native libraries: ${payload.missingNativeLibraries.joinToString().ifBlank { "none" }}; " +
                    "missing runtime components: ${payload.missingComponents.joinToString().ifBlank { "none" }}. " +
                    "Reinstall the application package or repair the Minecraft runtime components before trying again."
            )
        }
        if (!spec.workingDirectory.isDirectory) {
            throw IOException("Minecraft working directory is missing: ${spec.workingDirectory.absolutePath}")
        }

        // Os.setenv alone does not refresh Android's linker search namespace. The native helper
        // updates the runtime linker's LD_LIBRARY_PATH before libjli.so and libjvm.so are loaded.
        for ((name, value) in spec.environment) {
            if (!name.matches(ENVIRONMENT_NAME) || value.indexOf('\u0000') >= 0) {
                throw IOException("Rejected an invalid native environment entry.")
            }
            try {
                Os.setenv(name, value, true)
            } catch (error: Exception) {
                throw IOException("Could not configure the runtime environment variable $name.", error)
            }
        }
        val linkerPath = spec.environment["LD_LIBRARY_PATH"]
            ?: throw IOException("The launch request does not define LD_LIBRARY_PATH.")
        try {
            JREUtils.setLdLibraryPath(linkerPath)
        } catch (error: Throwable) {
            throw IOException("Could not update Android's native library search path: ${error.message ?: error.javaClass.simpleName}", error)
        }

        // jre_launcher.c routes a fatal JVM signal through stdio_is.c. Register the host
        // callback first so that failure path does not dereference an unset JavaVM/method ID.
        try {
            JREUtils.setupExitMethod(appContext)
            JREUtils.initializeHooks()
        } catch (error: Throwable) {
            throw IOException("Could not initialize the native JVM exit/termination hooks: ${error.message ?: error.javaClass.simpleName}", error)
        }

        // Upstream Pojav (JREUtils.launchJavaVM) dlopen()s the renderer and the JRE's native
        // libraries RTLD_GLOBAL before JLI_Launch so that LWJGL/AWT/NIO can resolve them.
        PojavRuntimeLoader.preload(spec.runtimeHome, spec.environment["POJAV_NATIVEDIR"], linkerPath)

        // libpojavexec's Logger JNI endpoint redirects native/JVM stdout and stderr to a stable
        // file. Create it first because the native implementation truncates but does not create.
        val gameLog = MinecraftGameLog.beginCapture(appContext)
        // Do not print argv/session data: it includes the temporary Minecraft access token.
        android.util.Log.i("SallyLauncher", "Minecraft stdout/stderr capture is ready at ${gameLog.absolutePath}")

        val changeDirectoryResult = try {
            JREUtils.chdir(spec.workingDirectory.canonicalPath)
        } catch (error: Throwable) {
            throw IOException("Could not change to the Minecraft game directory: ${error.message ?: error.javaClass.simpleName}", error)
        }
        if (changeDirectoryResult != 0) {
            throw IOException("The native bridge could not change to the Minecraft game directory.")
        }

        // Never log argv or the session: the access token is part of Minecraft's game arguments.
        return try {
            VMLauncher.launchJVM(spec.argv.toTypedArray())
        } catch (error: Throwable) {
            throw IOException("The embedded Java launcher failed before returning an exit code: ${error.message ?: error.javaClass.simpleName}", error)
        }
    }

    private companion object {
        val ENVIRONMENT_NAME = Regex("[A-Z_][A-Z0-9_]*")
    }
}
