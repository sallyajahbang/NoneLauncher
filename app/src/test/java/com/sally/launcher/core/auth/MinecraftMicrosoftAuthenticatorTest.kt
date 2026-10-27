package com.sally.launcher.core.auth

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException

class MinecraftMicrosoftAuthenticatorTest {
    private val auth = MinecraftMicrosoftAuthenticator()

    @Test
    fun acceptsGuidClientIdAndRejectsSecretsOrMalformedValues() {
        assertNull(auth.validateClientId("00000000-1111-2222-3333-444444444444"))
        assertNotNull(auth.validateClientId("not-a-guid"))
        assertNotNull(auth.validateClientId("client-id client-secret"))
        assertNotNull(auth.validateClientId(""))
    }

    @Test
    fun parsesValidDeviceCodeResponse() {
        val challenge = auth.parseDeviceCodeResponse(JSONObject("""
            {
              "device_code":"device-secret-value",
              "user_code":"ABCD-EFGH",
              "verification_uri":"https://microsoft.com/devicelogin",
              "expires_in":900,
              "interval":5
            }
        """))
        assertEquals("ABCD-EFGH", challenge.userCode)
        assertEquals("https://microsoft.com/devicelogin", challenge.verificationUri)
        assertEquals(900L, challenge.expiresInSeconds)
        assertEquals(5L, challenge.intervalSeconds)
    }

    @Test
    fun rejectsNonMicrosoftVerificationHost() {
        val json = JSONObject("""
            {
              "device_code":"opaque",
              "user_code":"ABCD",
              "verification_uri":"https://microsoft.com.attacker.example/devicelogin",
              "expires_in":900,
              "interval":5
            }
        """)
        assertThrows(IOException::class.java) { auth.parseDeviceCodeResponse(json) }
    }

    @Test
    fun rejectsInvalidDeviceCodeLifetime() {
        val json = JSONObject("""
            {
              "device_code":"opaque",
              "user_code":"ABCD",
              "verification_uri":"https://microsoft.com/devicelogin",
              "expires_in":0,
              "interval":5
            }
        """)
        assertThrows(IOException::class.java) { auth.parseDeviceCodeResponse(json) }
    }
}
