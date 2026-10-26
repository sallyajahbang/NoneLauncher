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

/** The runtime and game-file installers are separate from the not-yet-integrated JVM/native engine. */
class EngineLauncherNotIntegrated : EngineLauncher {
    override fun launch(versionId: String): EngineLaunchResult = EngineLaunchResult.NotReady(
        "Minecraft $versionId is selected. Sally can now probe whether an installed Java runtime exposes its native JLI launcher, but it does not yet execute JLI_Launch or connect Android-compatible LWJGL/GLFW, renderer, and input callbacks. Installing a runtime alone cannot launch the game."
    )
}
