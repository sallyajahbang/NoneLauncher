package com.sally.launcher.core.engine

import org.json.JSONObject
import java.util.Locale

enum class MinecraftAccountType { MICROSOFT, LOCAL }

/**
 * A Minecraft Java launch identity. Microsoft tokens are transient. Local accounts have no token
 * and are suitable only for single-player/local play and servers that explicitly allow offline mode.
 */
data class MinecraftLaunchSession(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userPropertiesJson: String = "{}",
    val clientId: String = "0",
    val xuid: String = "0",
    val userType: String = "msa",
    val accountType: MinecraftAccountType = MinecraftAccountType.MICROSOFT,
) {
    val normalizedUuid: String
        get() = uuid.replace("-", "").lowercase(Locale.ROOT)

    val isLocalAccount: Boolean get() = accountType == MinecraftAccountType.LOCAL
    val isMicrosoftAccount: Boolean get() = accountType == MinecraftAccountType.MICROSOFT

    /** Returns a safe, user-readable reason when the session cannot be used to launch the game. */
    fun validationError(): String? {
        if (!username.matches(Regex("[A-Za-z0-9_]{3,16}"))) {
            return "The Minecraft Java username must contain 3–16 letters, numbers, or underscores."
        }
        if (!normalizedUuid.matches(Regex("[a-f0-9]{32}"))) {
            return "The Minecraft profile UUID is invalid."
        }
        if (runCatching { JSONObject(userPropertiesJson) }.isFailure) {
            return "Profile properties are not valid JSON."
        }

        return when (accountType) {
            MinecraftAccountType.MICROSOFT -> {
                if (userType != "msa") {
                    return "The Microsoft account session has an invalid account type."
                }
                if (accessToken.isBlank() || accessToken == "0" || accessToken.any { it.isWhitespace() || it == '\u0000' }) {
                    return "A real Minecraft access token is required for a Microsoft account."
                }
                if (!clientId.matches(Regex("[A-Fa-f0-9-]{16,64}"))) {
                    return "The Microsoft OAuth application client ID is missing or invalid."
                }
                if (!xuid.matches(Regex("[0-9]{1,20}"))) {
                    return "The authenticated Xbox profile ID is invalid."
                }
                null
            }
            MinecraftAccountType.LOCAL -> {
                if (userType != "legacy" || accessToken != "0" || clientId != "0" || xuid != "0") {
                    return "A local account must use the legacy offline identity fields and must not contain online credentials."
                }
                val expectedUuid = OfflineAccountProfile.offlineUuid(username)
                if (normalizedUuid != expectedUuid) {
                    return "The local account UUID does not match its offline profile name."
                }
                null
            }
        }
    }
}

/** In-memory only: Microsoft access tokens are never persisted or sent through an Android Intent. */
object MinecraftLaunchSessionStore {
    @Volatile
    private var activeSession: MinecraftLaunchSession? = null

    fun current(): MinecraftLaunchSession? = activeSession

    /** Call only after the complete Microsoft -> Xbox Live -> XSTS -> Minecraft login chain succeeds. */
    @Synchronized
    fun installAfterAuthentication(session: MinecraftLaunchSession) {
        require(session.accountType == MinecraftAccountType.MICROSOFT) { "Expected a Microsoft account session." }
        val problem = session.validationError()
        require(problem == null) { problem ?: "Invalid Minecraft session" }
        activeSession = session
    }

    /** Install a deliberately offline identity. This never represents Microsoft authentication. */
    @Synchronized
    fun installLocalAccount(session: MinecraftLaunchSession) {
        require(session.accountType == MinecraftAccountType.LOCAL) { "Expected a local account session." }
        val problem = session.validationError()
        require(problem == null) { problem ?: "Invalid local account" }
        activeSession = session
    }

    @Synchronized
    fun clear() {
        activeSession = null
    }
}
