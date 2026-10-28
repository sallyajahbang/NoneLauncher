package com.sally.nonelauncher.core.auth

import java.util.UUID

/**
 * A player identity used to launch the game.
 *
 * Only offline accounts are implemented. Microsoft login is planned as a
 * second implementation of this interface (OAuth device flow -> Xbox Live
 * -> XSTS -> Minecraft token) and is not included yet.
 */
sealed interface Account {
    val username: String
    val uuid: String
    val accessToken: String
    /** Passed to the game as `user_type`: "legacy" for offline, "msa" for Microsoft. */
    val userType: String
}

data class OfflineAccount(override val username: String) : Account {
    init {
        require(username.isNotBlank()) { "username must not be blank" }
        require(username.length <= 16) { "Minecraft usernames are at most 16 characters" }
    }

    override val uuid: String = offlineUuid(username)
    override val accessToken: String = "0"
    override val userType: String = "legacy"

    companion object {
        /** Same derivation the vanilla server uses for offline-mode players. */
        fun offlineUuid(name: String): String =
            UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8)).toString()
    }
}
