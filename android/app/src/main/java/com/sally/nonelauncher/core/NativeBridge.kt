package com.sally.nonelauncher.core

/**
 * Kotlin side of the JNI bridge. Implementations live in crates/mc-jni (Rust).
 * Most functions return JSON. On failure they contain {"error": "..."}.
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

    /** Returns a JSON array of {url, dest, sha1, size} for the version's libraries and client. */
    @JvmStatic external fun downloadTasks(versionJson: String, configJson: String): String

    /** Returns a JSON array of download tasks for the asset objects in an asset index. */
    @JvmStatic external fun assetTasks(indexJson: String, assetsDir: String): String

    /** Downloads one task (from taskJson) to its dest. Returns {"ok":true} on success. */
    @JvmStatic external fun downloadFile(taskJson: String): String

    /**
     * Starts the JVM from libjvmPath and runs mainClass.main(args).
     * argsJson is a JSON array of the arguments from buildCommand.
     * Blocks until the game exits. Call it from a background thread.
     */
    @JvmStatic external fun launchJvm(libjvmPath: String, mainClass: String, argsJson: String): String
}
