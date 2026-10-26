package com.sally.launcher.core.engine

/** Stable boundary between the launcher UI and a future Minecraft Java runtime implementation. */
interface EngineLauncher {
    fun launch(versionId: String): EngineLaunchResult
}

sealed class EngineLaunchResult {
    data class Started(val versionId: String) : EngineLaunchResult()
    data class NotReady(val explanation: String) : EngineLaunchResult()
    data class Failed(val explanation: String) : EngineLaunchResult()
}

/** Stage 1 intentionally does not bundle or launch a JVM/native renderer yet. */
class EngineLauncherNotIntegrated : EngineLauncher {
    override fun launch(versionId: String): EngineLaunchResult = EngineLaunchResult.NotReady(
        "Version $versionId is selected, but the Java runtime, game libraries, renderer bridge and input bridge have not been integrated in this stage."
    )
}
