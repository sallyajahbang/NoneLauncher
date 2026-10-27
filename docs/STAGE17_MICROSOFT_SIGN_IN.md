# Sally Launcher — Stage 17: Microsoft device-code authentication

Stage 17 connects an explicit Microsoft device-code sign-in screen to the existing in-memory `MinecraftLaunchSessionStore`. The flow is:

1. Microsoft Identity Platform device authorization (`/consumers/oauth2/v2.0/devicecode` and `/token`).
2. Xbox Live user authentication.
3. Xbox Security Token Service (XSTS) validation for Minecraft Services.
4. Minecraft Services `login_with_xbox` followed by `GET /minecraft/profile`.
5. Create a session only after a Java profile name and UUID validate. Tokens are never logged or sent in Android intents and are not written to disk. The selected OAuth client ID is saved as a non-secret setting only.

## Required Microsoft application setup

Sally deliberately does not ship a made-up or third-party client ID. Create/register an app in Microsoft Entra for personal Microsoft accounts, configure it as a public client/device-code-capable application, and provide its **Application (client) ID** in the sign-in screen. Do not create or enter a client secret in this Android app. The requested OAuth scope is `XboxLive.signin`; Sally does not request a refresh token because token refresh is not implemented yet.

Microsoft/Xbox account eligibility and Minecraft Services access are separate requirements. A successful Microsoft OAuth sign-in alone does not prove that an account owns Minecraft: Java Edition. Xbox Live/XSTS or Minecraft Services may reject the request; in particular, Minecraft Services can reject an unapproved OAuth application or an account without a Java profile. The UI shows the stage that failed rather than silently switching to offline mode.

## Security and limitations

- The OAuth client ID is a public identifier and is stored separately from the session.
- Microsoft, Xbox, XSTS and Minecraft access tokens are in-memory only. Closing the process signs the user out; the refresh token returned by OAuth is intentionally not persisted or used yet.
- Network requests use HTTPS, finite connect/read timeouts, bounded response size, and the device-code polling interval. The browser URI is restricted to HTTPS Microsoft-owned domains.
- Sign-out clears the in-memory session. No credential is put in a log, saved preference, or Intent.
- This stage does not implement token refresh. If an access token expires, sign in again.
- A valid account session is necessary, not sufficient, for gameplay. The bundled native JLI/GLFW/rendering path still needs a successful GitHub Actions APK build and device test.

## Validation

Local JUnit tests cover client-ID validation and device-code response parsing/rejection. Verify the full Android build using `./gradlew --no-daemon --stacktrace :app:testDebugUnitTest :app:assembleDebug` in GitHub Actions or another environment with Android SDK/NDK and Gradle available. No live sign-in test is run by unit tests.

## References

- Microsoft Learn, OAuth 2.0 device authorization grant: https://learn.microsoft.com/azure/active-directory/develop/v2-oauth2-device-code
- Minecraft Wiki, Microsoft authentication flow and Xbox/XSTS/Minecraft exchange: https://minecraft.wiki/w/Microsoft_authentication
