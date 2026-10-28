package com.sally.nonelauncher.core

import com.sally.nonelauncher.core.auth.Account
import com.sally.nonelauncher.core.download.Downloader
import com.sally.nonelauncher.core.install.GameInstaller
import com.sally.nonelauncher.core.launch.InstalledVersion
import com.sally.nonelauncher.core.launch.LaunchCommand
import com.sally.nonelauncher.core.launch.LaunchCommandBuilder
import com.sally.nonelauncher.core.launch.LaunchOptions
import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.mojang.LibraryResolver
import com.sally.nonelauncher.core.mojang.ManifestVersion
import com.sally.nonelauncher.core.mojang.VersionManifest
import com.sally.nonelauncher.core.mojang.VersionMetaParser
import java.io.File

/**
 * Entry point the UI talks to. Ties together manifest lookup, installation,
 * and command construction. Running the command is left to a [com.sally.nonelauncher.core.launch.GameBackend].
 */
class LauncherCore(
    val paths: LauncherPaths,
    val platform: RuntimeContext,
    private val downloader: Downloader,
    private val log: (String) -> Unit,
) {
    companion object {
        const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    }

    private val installer = GameInstaller(paths, downloader, platform, log, GameInstaller.ASSET_BASE_URL)
    private val commands = LaunchCommandBuilder(paths, platform)

    suspend fun fetchManifest(): VersionManifest =
        VersionManifest.parse(downloader.fetchText(MANIFEST_URL))

    suspend fun install(entry: ManifestVersion): InstalledVersion = installer.install(entry)

    /**
     * Rebuilds [InstalledVersion] for an already-installed version from its
     * saved metadata and the files on disk (no network access).
     */
    fun loadInstalled(id: String): InstalledVersion {
        val json = paths.versionJson(id)
        require(json.exists()) { "version $id is not installed" }
        val meta = VersionMetaParser.parse(json.readText())
        val classpath = LibraryResolver.resolve(meta.libraries, platform)
            .filter { !it.isNative }
            .map { File(paths.libraries, it.relativePath).absolutePath }
        return InstalledVersion(
            meta = meta,
            clientJar = paths.clientJar(id),
            libraryClasspath = classpath,
            nativesDir = paths.nativesDir(id),
        )
    }

    fun buildCommand(installed: InstalledVersion, account: Account, instance: String, memoryMb: Int = 2048,
                     javaExecutable: File = paths.javaExecutable(installed.meta.javaMajorVersion)): LaunchCommand {
        val options = LaunchOptions(
            account = account,
            gameDir = paths.instanceDir(instance),
            javaExecutable = javaExecutable.absolutePath,
            maxMemoryMb = memoryMb,
        )
        return commands.build(installed, options)
    }
}
