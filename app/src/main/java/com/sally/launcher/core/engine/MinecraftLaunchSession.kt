package com.sally.launcher.core.engine

import org.json.JSONObject
import java.util.Locale

/**
 * A short-lived Minecraft Java session returned by a successful Microsoft/Xbox/Minecraft auth flow.
 * The access token is intentionally kept only in memory and must never be logged or placed in an Intent.
 */
data class MinecraftLaunchSession(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userPropertiesJson: String = "{}",
    val clientId: String = "0",
    val xuid: String = "0",
    val userType: String = "msa",
) {
    val normalizedUuid: String
        get() = uuid.replace("-", "").lowercase(Locale.ROOT)

    /** Returns a safe, user-readable reason when the session cannot be used to launch the game. */
    fun validationError(): String? {
        if (!username.matches(Regex("[A-Za-z0-9_]{3,16}"))) {
            return "The authenticated Minecraft profile has an invalid Java username."
        }
        if (!normalizedUuid.matches(Regex("[a-f0-9]{32}"))) {
            return "The authenticated Minecraft profile UUID is invalid."
        }
        if (accessToken.isBlank() || accessToken == "0" || accessToken.any { it.isWhitespace() || it == '\u0000' }) {
            return "A real Minecraft access token is required. Offline placeholder tokens are not accepted."
        }
        if (userType != "msa") {
            return "Only a Microsoft-authenticated Java account can be used by this launch path."
        }
        if (runCatching { JSONObject(userPropertiesJson) }.isFailure) {
            return "Authenticated profile properties are not valid JSON."
        }
        if (!clientId.matches(Regex("[A-Fa-f0-9-]{16,64}"))) {
            return "The Microsoft OAuth application client ID is missing or invalid."
        }
        if (!xuid.matches(Regex("[0-9]{1,20}"))) {
            return "The authenticated Xbox profile ID is invalid."
        }
        return null
    }
}

/** In-memory only: access tokens are not persisted and are never sent through an Android Intent. */
object MinecraftLaunchSessionStore {
    @Volatile
    private var activeSession: MinecraftLaunchSession? = null

    fun current(): MinecraftLaunchSession? = activeSession

    /** Call only after the complete Microsoft -> Xbox Live -> XSTS -> Minecraft login chain succeeds. */
    @Synchronized
    fun installAfterAuthentication(session: MinecraftLaunchSession) {
        val problem = session.validationError()
        require(problem == null) { problem ?: "Invalid Minecraft session" }
        activeSession = session
    }

    @Synchronized
    fun clear() {
        activeSession = null
    }
}
