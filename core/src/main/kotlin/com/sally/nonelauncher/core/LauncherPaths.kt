package com.sally.nonelauncher.core

import java.io.File

/**
 * On-disk layout used by the launcher. Everything lives under [root], so
 * the app can move or wipe its data by changing a single directory.
 *
 * root/
 *   versions/<id>/<id>.json, <id>.jar, natives/
 *   libraries/
 *   assets/indexes/<id>.json, assets/objects/<h2>/<hash>
 *   runtime/java-<major>/bin/java
 *   instances/<name>/          (game directory: saves, mods, options)
 */
class LauncherPaths(val root: File) {
    val versions = File(root, "versions")
    val libraries = File(root, "libraries")
    val assets = File(root, "assets")
    val assetIndexes = File(assets, "indexes")
    val assetObjects = File(assets, "objects")
    val runtimes = File(root, "runtime")
    val instances = File(root, "instances")

    init {
        listOf(versions, libraries, assetIndexes, assetObjects, runtimes, instances)
            .forEach { it.mkdirs() }
    }

    fun versionDir(id: String): File = File(versions, id).apply { mkdirs() }

    fun versionJson(id: String): File = File(versionDir(id), "$id.json")

    fun clientJar(id: String): File = File(versionDir(id), "$id.jar")

    fun nativesDir(id: String): File = File(versionDir(id), "natives").apply { mkdirs() }

    fun instanceDir(name: String): File = File(instances, name).apply { mkdirs() }

    fun assetIndexFile(id: String): File = File(assetIndexes, "$id.json")

    /** Objects are stored by the first two hex chars of their SHA-1, like the vanilla layout. */
    fun assetObjectFile(hash: String): File =
        File(File(assetObjects, hash.substring(0, 2)), hash)

    fun javaExecutable(major: Int): File = File(runtimes, "java-$major/bin/java")
}
