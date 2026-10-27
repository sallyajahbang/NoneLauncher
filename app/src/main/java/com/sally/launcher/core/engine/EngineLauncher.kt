package com.sally.launcher.core.engine

/** Legacy boundary retained for earlier stage diagnostics; the current game path uses PojavGameActivity. */
interface EngineLauncher {
    fun launch(versionId: String): EngineLaunchResult
}

sealed class EngineLaunchResult {
    data class Started(val versionId: String) : EngineLaunchResult()
    data class NotReady(val explanation: String) : EngineLaunchResult()
    data class Failed(val explanation: String) : EngineLaunchResult()
}

/** Legacy fallback used by any older caller that does not provide a launch session or game Activity. */
@Deprecated("Use the authenticated PojavGameActivity launch route instead.")
class EngineLauncherNotIntegrated : EngineLauncher {
    override fun launch(versionId: String): EngineLaunchResult = EngineLaunchResult.NotReady(
        "Minecraft $versionId cannot be launched from this legacy adapter. Use PojavGameActivity with a validated Microsoft/Minecraft Java session and a bound Android surface."
    )
}
