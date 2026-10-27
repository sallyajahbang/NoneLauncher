package com.sally.launcher.core.engine

import java.nio.charset.StandardCharsets
import java.util.UUID

/** A local/offline Minecraft identity; it is not an authenticated Mojang/Microsoft profile. */
data class OfflineAccountProfile private constructor(val username: String, val uuid: String) {
    fun toSession(): MinecraftLaunchSession = MinecraftLaunchSession(
        username = username,
        uuid = uuid,
        accessToken = "0",
        userPropertiesJson = "{}",
        clientId = "0",
        xuid = "0",
        userType = "legacy",
        accountType = MinecraftAccountType.LOCAL,
    )

    companion object {
        private val VALID_NAME = Regex("[A-Za-z0-9_]{3,16}")

        fun create(rawName: String): OfflineAccountProfile {
            val name = rawName.trim()
            require(VALID_NAME.matches(name)) {
                "Minecraft usernames must be 3–16 characters using only A–Z, a–z, 0–9, and underscore."
            }
            return OfflineAccountProfile(name, offlineUuid(name))
        }

        fun offlineUuid(username: String): String = UUID.nameUUIDFromBytes(
            "OfflinePlayer:$username".toByteArray(StandardCharsets.UTF_8),
        ).toString().replace("-", "")
    }
}
