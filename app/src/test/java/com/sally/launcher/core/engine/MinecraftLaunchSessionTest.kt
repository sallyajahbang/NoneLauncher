package com.sally.launcher.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class MinecraftLaunchSessionTest {
    @Test
    fun validMicrosoftSessionPassesStructuralValidation() {
        val session = MinecraftLaunchSession(
            username = "SallyPlayer",
            uuid = "01234567-89ab-cdef-0123-456789abcdef",
            accessToken = "eyJhbGciOiJub25lIn0.payload.signature",
            userPropertiesJson = "{}",
            clientId = "00000000402b5328",
            xuid = "1234567890123456",
        )
        assertNull(session.validationError())
        assertEquals("0123456789abcdef0123456789abcdef", session.normalizedUuid)
    }

    @Test
    fun offlinePlaceholderTokenIsRejected() {
        val session = validSession().copy(accessToken = "0")
        assertNotNull(session.validationError())
    }

    @Test
    fun malformedUuidIsRejected() {
        val session = validSession().copy(uuid = "not-a-profile-id")
        assertNotNull(session.validationError())
    }

    @Test
    fun nonMicrosoftUserTypeIsRejected() {
        val session = validSession().copy(userType = "legacy")
        assertNotNull(session.validationError())
    }

    @Test
    fun malformedUserPropertiesAreRejected() {
        val session = validSession().copy(userPropertiesJson = "not-json")
        assertNotNull(session.validationError())
    }

    @Test
    fun malformedXboxProfileIdIsRejected() {
        val session = validSession().copy(xuid = "xuid with spaces")
        assertNotNull(session.validationError())
    }

    private fun validSession() = MinecraftLaunchSession(
        username = "SallyPlayer",
        uuid = "0123456789abcdef0123456789abcdef",
        accessToken = "test-token-with-no-whitespace",
        userPropertiesJson = "{}",
        clientId = "00000000402b5328",
    )
}
