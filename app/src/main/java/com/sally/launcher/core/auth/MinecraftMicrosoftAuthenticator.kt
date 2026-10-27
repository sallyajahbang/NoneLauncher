package com.sally.launcher.core.auth

import com.sally.launcher.core.engine.MinecraftLaunchSession
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Microsoft device-code -> Xbox Live -> XSTS -> Minecraft Java authentication.
 *
 * Tokens are transient values: this class never logs them, persists them, or includes them in
 * exception messages. Call every network method from a worker thread.
 */
class MinecraftMicrosoftAuthenticator {
    data class DeviceCodeChallenge(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresInSeconds: Long,
        val intervalSeconds: Long,
    )

    private data class HttpJson(val status: Int, val body: JSONObject)

    private class RemoteAuthException(
        val operation: String,
        val httpStatus: Int,
        val errorCode: String,
        val description: String,
        val xErr: Long,
    ) : IOException()

    fun validateClientId(clientId: String): String? = when {
        !CLIENT_ID.matches(clientId.trim()) -> "Enter the Microsoft application (client) ID as a GUID. Do not enter a client secret."
        else -> null
    }

    fun requestDeviceCode(clientId: String): DeviceCodeChallenge {
        validateClientId(clientId)?.let { throw IOException(it) }
        val response = try {
            postForm(
                DEVICE_CODE_ENDPOINT,
                "Microsoft device authorization",
                linkedMapOf(
                    "client_id" to clientId.trim(),
                    "scope" to "XboxLive.signin",
                ),
            )
        } catch (error: RemoteAuthException) {
            throw IOException(oauthFailureMessage(error), error)
        }
        return parseDeviceCodeResponse(response)
    }

    internal fun parseDeviceCodeResponse(response: JSONObject): DeviceCodeChallenge {
        val deviceCode = response.optString("device_code").takeIf { it.isNotBlank() }
            ?: throw IOException("Microsoft returned an incomplete device authorization response.")
        val userCode = response.optString("user_code").takeIf { it.isNotBlank() }
            ?: throw IOException("Microsoft did not return a sign-in code.")
        val verificationUri = response.optString("verification_uri").takeIf(::isAllowedVerificationUri)
            ?: throw IOException("Microsoft returned an unsupported verification URL. Open https://microsoft.com/devicelogin manually.")
        val expiresIn = response.optLong("expires_in", 0L)
        val interval = response.optLong("interval", 5L)
        if (expiresIn !in 1L..3600L || interval !in 1L..60L) {
            throw IOException("Microsoft returned an invalid device-code lifetime or polling interval.")
        }
        return DeviceCodeChallenge(deviceCode, userCode, verificationUri, expiresIn, interval)
    }

    /**
     * Waits for Microsoft OAuth completion and exchanges the result through Xbox Live/XSTS into a
     * Minecraft Java access token. onStatus is called on the calling worker thread.
     */
    @Throws(IOException::class, InterruptedException::class)
    fun completeSignIn(
        clientId: String,
        challenge: DeviceCodeChallenge,
        isCancelled: () -> Boolean = { Thread.currentThread().isInterrupted },
        onStatus: (String) -> Unit = {},
    ): MinecraftLaunchSession {
        validateClientId(clientId)?.let { throw IOException(it) }
        val microsoftAccessToken = pollMicrosoftToken(clientId.trim(), challenge, isCancelled, onStatus)
        checkNotCancelled(isCancelled)
        onStatus("Connecting the Microsoft account to Xbox Live…")

        val xboxResponse = postJson(
            XBOX_AUTH_ENDPOINT,
            "Xbox Live authentication",
            JSONObject()
                .put("Properties", JSONObject()
                    .put("AuthMethod", "RPS")
                    .put("SiteName", "user.auth.xboxlive.com")
                    .put("RpsTicket", "d=$microsoftAccessToken"))
                .put("RelyingParty", "http://auth.xboxlive.com")
                .put("TokenType", "JWT"),
        )
        val xboxToken = xboxResponse.optString("Token").takeIf { it.isNotBlank() }
            ?: throw IOException("Xbox Live did not return an authentication token.")
        val xboxClaims = firstXui(xboxResponse)
        val xboxUserHash = xboxClaims?.optString("uhs").orEmpty()
        if (xboxUserHash.isBlank()) throw IOException("Xbox Live did not return the required user-hash claim.")

        checkNotCancelled(isCancelled)
        onStatus("Validating the Xbox security token…")
        val xstsResponse = try {
            postJson(
                XSTS_AUTH_ENDPOINT,
                "Xbox security-token validation",
                JSONObject()
                    .put("Properties", JSONObject()
                        .put("SandboxId", "RETAIL")
                        .put("UserTokens", JSONArray().put(xboxToken)))
                    .put("RelyingParty", "rp://api.minecraftservices.com/")
                    .put("TokenType", "JWT"),
            )
        } catch (error: RemoteAuthException) {
            throw IOException(xstsFailureMessage(error), error)
        }
        val xstsToken = xstsResponse.optString("Token").takeIf { it.isNotBlank() }
            ?: throw IOException("Xbox security-token service did not return a token.")
        val xstsClaims = firstXui(xstsResponse)
        val userHash = xstsClaims?.optString("uhs")?.takeIf { it.isNotBlank() } ?: xboxUserHash
        val xuid = sequenceOf(xstsClaims?.optString("xid"), xboxClaims?.optString("xid"))
            .filterNotNull()
            .map { it.trim() }
            .firstOrNull { it.matches(NUMERIC_ID) }
            ?: "0"

        checkNotCancelled(isCancelled)
        onStatus("Checking Minecraft Java Edition ownership and profile…")
        val minecraftResponse = try {
            postJson(
                MINECRAFT_LOGIN_ENDPOINT,
                "Minecraft Services login",
                JSONObject().put("identityToken", "XBL3.0 x=$userHash;$xstsToken"),
            )
        } catch (error: RemoteAuthException) {
            throw IOException(minecraftLoginFailureMessage(error), error)
        }
        val minecraftAccessToken = minecraftResponse.optString("access_token").takeIf { it.isNotBlank() }
            ?: throw IOException("Minecraft Services did not return a Java access token.")

        checkNotCancelled(isCancelled)
        val profile = try {
            getJson(PROFILE_ENDPOINT, "Minecraft Java profile", "Bearer $minecraftAccessToken")
        } catch (error: RemoteAuthException) {
            throw IOException(profileFailureMessage(error), error)
        }
        val username = profile.optString("name").takeIf { it.isNotBlank() }
            ?: throw IOException("The Microsoft account has no Minecraft Java profile name.")
        val uuid = profile.optString("id").takeIf { normalizeUuid(it).matches(NORMALIZED_UUID) }
            ?: throw IOException("Minecraft Services returned an invalid Java profile UUID.")

        val session = MinecraftLaunchSession(
            username = username,
            uuid = normalizeUuid(uuid),
            accessToken = minecraftAccessToken,
            userPropertiesJson = "{}",
            clientId = clientId.trim().lowercase(Locale.ROOT),
            xuid = xuid,
            userType = "msa",
        )
        session.validationError()?.let { throw IOException("Minecraft returned an unusable Java profile: $it") }
        return session
    }

    private fun pollMicrosoftToken(
        clientId: String,
        challenge: DeviceCodeChallenge,
        isCancelled: () -> Boolean,
        onStatus: (String) -> Unit,
    ): String {
        val expiresAt = System.currentTimeMillis() + challenge.expiresInSeconds * 1_000L
        var intervalSeconds = challenge.intervalSeconds.coerceAtLeast(1L)
        onStatus("Waiting for approval in your browser. Keep this screen open.")
        while (System.currentTimeMillis() < expiresAt) {
            checkNotCancelled(isCancelled)
            try {
                Thread.sleep(intervalSeconds * 1_000L)
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw interrupted
            }
            checkNotCancelled(isCancelled)
            try {
                val tokenResponse = postForm(
                    TOKEN_ENDPOINT,
                    "Microsoft token exchange",
                    linkedMapOf(
                        "client_id" to clientId,
                        "grant_type" to DEVICE_CODE_GRANT,
                        "device_code" to challenge.deviceCode,
                    ),
                )
                return tokenResponse.optString("access_token").takeIf { it.isNotBlank() }
                    ?: throw IOException("Microsoft approved the sign-in but did not return an access token.")
            } catch (error: RemoteAuthException) {
                when (error.errorCode) {
                    "authorization_pending" -> onStatus("Waiting for Microsoft approval…")
                    "slow_down" -> {
                        intervalSeconds = (intervalSeconds + 5L).coerceAtMost(60L)
                        onStatus("Microsoft asked the launcher to poll less frequently…")
                    }
                    "authorization_declined" -> throw IOException("Microsoft sign-in was declined. Start sign-in again to retry.")
                    "expired_token", "bad_verification_code" -> throw IOException("The Microsoft sign-in code expired or is invalid. Start sign-in again.")
                    else -> throw IOException(oauthFailureMessage(error), error)
                }
            }
        }
        throw IOException("The Microsoft sign-in code expired. Start sign-in again.")
    }

    private fun postForm(endpoint: String, operation: String, fields: Map<String, String>): JSONObject {
        val body = fields.entries.joinToString("&") { (key, value) ->
            "${formEncode(key)}=${formEncode(value)}"
        }
        return execute(endpoint, operation, "application/x-www-form-urlencoded", body, null, "POST")
    }

    private fun postJson(endpoint: String, operation: String, body: JSONObject): JSONObject =
        execute(endpoint, operation, "application/json; charset=utf-8", body.toString(), null, "POST")

    private fun getJson(endpoint: String, operation: String, authorization: String): JSONObject =
        execute(endpoint, operation, null, null, authorization, "GET")

    private fun execute(
        endpoint: String,
        operation: String,
        contentType: String?,
        body: String?,
        authorization: String?,
        method: String,
    ): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val activeConnection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "SallyLauncher/0.1 Android")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (authorization != null) setRequestProperty("Authorization", authorization)
                if (body != null) doOutput = true
            }
            connection = activeConnection
            if (body != null) {
                activeConnection.outputStream.use { stream ->
                    stream.write(body.toByteArray(StandardCharsets.UTF_8))
                }
            }
            val status = activeConnection.responseCode
            val source = if (status in 200..299) activeConnection.inputStream else activeConnection.errorStream
            val text = source?.bufferedReader(StandardCharsets.UTF_8)?.use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(4_096)
                var total = 0
                while (true) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_RESPONSE_CHARS) throw IOException("Authentication service returned an unexpectedly large response.")
                    output.append(buffer, 0, read)
                }
                output.toString()
            }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse {
                if (status in 200..299) throw IOException("$operation returned an unreadable response.")
                JSONObject()
            }
            if (status !in 200..299) {
                throw RemoteAuthException(
                    operation = operation,
                    httpStatus = status,
                    errorCode = json.optString("error"),
                    description = json.optString("error_description").replace(Regex("[\\r\\n\\t]+"), " ").take(280),
                    xErr = json.optLong("XErr", 0L),
                )
            }
            return json
        } catch (error: RemoteAuthException) {
            throw error
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("Could not complete $operation. Check the network connection and try again.", error)
        } finally {
            connection?.disconnect()
        }
    }

    private fun oauthFailureMessage(error: RemoteAuthException): String {
        val detail = error.description.takeIf { it.isNotBlank() && !it.contains("access_token", ignoreCase = true) }
        return buildString {
            append("Microsoft sign-in failed (HTTP ${error.httpStatus}")
            if (error.errorCode.isNotBlank()) append(", ${error.errorCode}")
            append(").")
            if (detail != null) append(" ").append(detail)
            append(" Check that the app registration supports public-client/device-code sign-in and the XboxLive.signin permission.")
        }
    }

    private fun xstsFailureMessage(error: RemoteAuthException): String = when (error.xErr) {
        2_148_916_233L -> "This Microsoft account does not appear to have an Xbox profile. Create/enable an Xbox profile, then try again."
        2_148_916_234L -> "Xbox Live could not complete this account's sign-in. Check account region and Xbox service availability."
        2_148_916_238L -> "This account has age/family restrictions for Xbox authentication. Review Microsoft Family/Xbox settings."
        else -> "Xbox security-token validation failed (HTTP ${error.httpStatus}${error.errorCode.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}). Check the Xbox profile and app permissions."
    }

    private fun minecraftLoginFailureMessage(error: RemoteAuthException): String = when (error.httpStatus) {
        403 -> "Minecraft Services denied this app or account (HTTP 403). Confirm this Microsoft account owns Minecraft: Java Edition and that the registered OAuth app is allowed to use Minecraft Services."
        401 -> "Minecraft Services rejected the Xbox token. Sign in again and verify the Xbox profile is active."
        else -> "Minecraft Services login failed (HTTP ${error.httpStatus}${error.errorCode.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""})."
    }

    private fun profileFailureMessage(error: RemoteAuthException): String = when (error.httpStatus) {
        404 -> "This Microsoft account has no Minecraft Java Edition profile. Confirm Java Edition ownership, then retry."
        403 -> "Minecraft Java profile access was denied (HTTP 403). The account may not own Java Edition or this OAuth application may need Minecraft Services access."
        401 -> "The Minecraft Java profile token was rejected. Sign in again."
        else -> "Could not retrieve the Minecraft Java profile (HTTP ${error.httpStatus})."
    }

    private fun firstXui(json: JSONObject): JSONObject? =
        json.optJSONObject("DisplayClaims")?.optJSONArray("xui")?.optJSONObject(0)

    private fun checkNotCancelled(isCancelled: () -> Boolean) {
        if (isCancelled() || Thread.currentThread().isInterrupted) throw InterruptedException("Microsoft sign-in cancelled")
    }

    private fun isAllowedVerificationUri(raw: String): Boolean = try {
        val uri = java.net.URI(raw)
        val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
        uri.scheme.equals("https", ignoreCase = true) &&
            (host == "microsoft.com" || host.endsWith(".microsoft.com") || host == "aka.ms") &&
            uri.userInfo == null && uri.port in listOf(-1, 443)
    } catch (_: Exception) {
        false
    }

    private fun normalizeUuid(value: String): String = value.replace("-", "").lowercase(Locale.ROOT)

    private fun formEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val DEVICE_CODE_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        const val TOKEN_ENDPOINT = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
        const val XBOX_AUTH_ENDPOINT = "https://user.auth.xboxlive.com/user/authenticate"
        const val XSTS_AUTH_ENDPOINT = "https://xsts.auth.xboxlive.com/xsts/authorize"
        const val MINECRAFT_LOGIN_ENDPOINT = "https://api.minecraftservices.com/authentication/login_with_xbox"
        const val PROFILE_ENDPOINT = "https://api.minecraftservices.com/minecraft/profile"
        const val DEVICE_CODE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_CHARS = 1_048_576
        val CLIENT_ID = Regex("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
        val NORMALIZED_UUID = Regex("^[a-f0-9]{32}$")
        val NUMERIC_ID = Regex("^[0-9]{1,20}$")
    }
}
