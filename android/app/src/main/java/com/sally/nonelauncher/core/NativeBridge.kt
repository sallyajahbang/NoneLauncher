package com.sally.nonelauncher.core

/**
 * Kotlin side of the JNI bridge. Implementations live in crates/mc-jni (Rust).
 * Every function returns a JSON string. On failure it contains {"error": "..."}.
 */
object NativeBridge {
    init {
        System.loadLibrary("mc_jni")
    }

    /** GET a URL and return the body as text. */
    @JvmStatic external fun fetchText(url: String): String

    /** Mojang's version_manifest_v2.json. */
    @JvmStatic external fun fetchManifest(): String

    /** Offline-mode UUID for a player name. Returns a plain string, not JSON. */
    @JvmStatic external fun offlineUuid(name: String): String

    /** Returns {"program": "...", "args": [...]} for the given version and config JSON. */
    @JvmStatic external fun buildCommand(versionJson: String, configJson: String): String

    /** Returns a JSON array of {url, dest, sha1, size} for everything the version needs. */
    @JvmStatic external fun downloadTasks(versionJson: String, configJson: String): String
}
