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
        "Minecraft $versionId is selected, but Sally's JVM/JNI launch bridge, Android-native renderer, and keyboard/mouse/touch input pipeline are not integrated yet. Installing a Java runtime alone cannot launch the game."
    )
}
