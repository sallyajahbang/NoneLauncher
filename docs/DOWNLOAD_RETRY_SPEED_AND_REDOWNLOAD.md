# Download speed, retry, resume, and redownload

## Download pipeline

`data/download/ResilientDownloader.kt` is the shared streaming downloader for Minecraft files and Android Java runtime archives. It uses a 128 KiB buffer, disables content-encoding transformations, enables HTTPS redirects for CDN URLs, applies connect/read timeouts, checks file size and the published SHA-1/SHA-256 digest, and retries transient IO errors plus HTTP 408/425/429/5xx responses up to four attempts with backoff.

For a `.part` file, the downloader uses `Range: bytes=<offset>-` when a partial file exists and the server supports partial responses. If the server answers with a normal 200 response instead, the partial file is replaced from byte zero. A `.part` file is not promoted until size/checksum validation passes. Small metadata requests without a published size/checksum restart from byte zero because they cannot be safely marked complete after a process interruption.

`VersionInstaller.kt` now runs six dependency downloads concurrently, reports aggregated progress in MiB, and cancels sibling work if one dependency fails after retries. It waits for interrupted workers to exit before returning failure so a manual retry does not immediately race writes to the same `.part` files. Download parts are preserved so a retry can resume.

`RuntimeManager.kt` uses the same retry/resume path for Java archives and retries metadata requests. Failed archive parts are retained. A forced runtime redownload stages and validates the replacement before swapping it into place; if final validation fails after activation, the previous runtime is restored when available. If a forced runtime redownload fails, Retry keeps the force-redownload path active while preserving the partial archive, so it resumes instead of incorrectly reporting the old installed runtime as the retry result.

## UI actions

- **Version Manager → Redownload** force-fetches metadata, the client JAR, and the libraries/assets referenced by that version. A confirmation dialog warns that shared content-addressed assets may be downloaded again and could consume substantial mobile data. Forced redownload clears stale partial files for the requested version before starting, then keeps newly downloaded files in `.part` until verification succeeds.
- **Play → Runtime → Redownload Java runtime** forces a new Java runtime download. The old runtime is retained until the replacement validates.
- Failed Minecraft/runtime downloads show a dialog with **Retry** and **Redownload**. Retry uses cached valid files and resumes incomplete `.part` files; Redownload starts a forced fetch.

## Validation performed

- Compiled `ResilientDownloader.kt` with the local Kotlin compiler.
- Compiled `VersionInstaller.kt` and `RuntimeManager.kt` with lightweight dependency stubs to catch Kotlin type/syntax issues in changed code.
- Compiled `VersionManifestRepository.kt` with lightweight dependency stubs.
- Ran a local HTTPS test harness where the first response ended halfway; the downloader retried, sent a Range request, completed the remainder, and passed SHA-256 integrity verification.
- Full Android Gradle build could not run here: the Gradle wrapper distribution is not cached and `services.gradle.org` fails DNS resolution. The UI wiring therefore still needs confirmation in GitHub Actions / Android build environment.
