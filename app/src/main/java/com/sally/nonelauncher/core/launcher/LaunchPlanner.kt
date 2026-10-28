// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Buntuk

package com.sally.nonelauncher.core.launcher

import java.io.File

data class LaunchSettings(
    val username: String,
    val uuid: String,
    val accessToken: String,
    val userType: String = "msa",
    val maxMemoryMb: Int = 1024,
    val width: Int = 1280,
    val height: Int = 720,
    val gameDir: File,
    val assetsDir: File,
    val launcherName: String = "NoneLauncher",
    val launcherVersion: String = "0.1.0",
)

/**
 * Hasil perencanaan launch. Runner JVM akan menjalankan:
 * java [jvmArgs] [mainClass] [gameArgs]
 * dengan nativeJars sudah diekstrak ke nativesDir.
 */
data class LaunchPlan(
    val mainClass: String,
    val classpath: List<File>,
    val nativeJars: List<File>,
    val jvmArgs: List<String>,
    val gameArgs: List<String>,
)

object LaunchPlanner {

    private val PLACEHOLDER = Regex("""\$\{([A-Za-z_]+)\}""")

    fun plan(
        version: VersionJson,
        settings: LaunchSettings,
        librariesDir: File,
        clientJar: File,
        nativesDir: File,
    ): LaunchPlan {
        val classpath = mutableListOf<File>()
        val nativeJars = mutableListOf<File>()

        for (lib in version.libraries) {
            if (!RuleEvaluator.isAllowed(lib.rules)) continue

            // Format baru: classifier natives-linux di blok natives.
            lib.natives["linux"]?.let { classifier ->
                lib.classifiers[classifier]?.let { nativeJars += File(librariesDir, it.path) }
            }

            // Format lama: library natives berdiri sendiri dengan nama :natives-linux.
            val artifact = lib.artifact ?: continue
            val file = File(librariesDir, artifact.path)
            if (lib.name.endsWith(":natives-linux")) nativeJars += file else classpath += file
        }
        classpath += clientJar

        val classpathString = classpath.joinToString(":") { it.absolutePath }
        val vars = mapOf(
            "natives_directory" to nativesDir.absolutePath,
            "launcher_name" to settings.launcherName,
            "launcher_version" to settings.launcherVersion,
            "classpath" to classpathString,
            "classpath_separator" to ":",
            "library_directory" to librariesDir.absolutePath,
            "version_name" to version.id,
            "version_type" to "release",
            "game_directory" to settings.gameDir.absolutePath,
            "assets_root" to settings.assetsDir.absolutePath,
            "assets_index_name" to version.assetIndex.id,
            "auth_player_name" to settings.username,
            "auth_uuid" to settings.uuid,
            "auth_access_token" to settings.accessToken,
            "user_type" to settings.userType,
            "user_properties" to "{}",
            "resolution_width" to settings.width.toString(),
            "resolution_height" to settings.height.toString(),
        )

        fun expand(s: String): String = PLACEHOLDER.replace(s) { m ->
            vars[m.groupValues[1]] ?: m.value
        }

        val jvm = mutableListOf(
            "-Xmx${settings.maxMemoryMb}M",
            "-Dminecraft.launcher.brand=${settings.launcherName}",
            "-Dminecraft.launcher.version=${settings.launcherVersion}",
        )
        version.jvmArgs
            .filter { RuleEvaluator.isAllowed(it.rules) }
            .flatMapTo(jvm) { entry -> entry.values.map(::expand) }

        val game: List<String> = if (version.legacyGameArgs != null) {
            // Format lama tidak punya -cp di jvmArgs, jadi ditambahkan di sini.
            jvm += listOf("-Djava.library.path=${nativesDir.absolutePath}", "-cp", classpathString)
            version.legacyGameArgs.split(" ").filter { it.isNotEmpty() }.map(::expand)
        } else {
            version.gameArgs
                .filter { RuleEvaluator.isAllowed(it.rules) }
                .flatMap { entry -> entry.values.map(::expand) }
        }

        return LaunchPlan(
            mainClass = version.mainClass,
            classpath = classpath,
            nativeJars = nativeJars,
            jvmArgs = jvm,
            gameArgs = game,
        )
    }
}
