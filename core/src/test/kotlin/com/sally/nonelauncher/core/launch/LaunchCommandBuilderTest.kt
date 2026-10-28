package com.sally.nonelauncher.core.launch

import com.sally.nonelauncher.core.LauncherPaths
import com.sally.nonelauncher.core.auth.OfflineAccount
import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.mojang.VersionMetaParser
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class LaunchCommandBuilderTest {

    private val tmp: File = createTempDir("mcd-test")
    private val paths = LauncherPaths(tmp)
    private val ctx = RuntimeContext(osName = "linux", arch = "arm64")

    private val json = """
    {
      "id": "test-1",
      "type": "release",
      "mainClass": "net.minecraft.client.main.Main",
      "assetIndex": { "id": "idx", "url": "https://example.invalid/i.json" },
      "downloads": { "client": { "url": "https://example.invalid/c.jar" } },
      "arguments": {
        "game": ["--username", "${'$'}{auth_player_name}", "--version", "${'$'}{version_name}", "--gameDir", "${'$'}{game_directory}"],
        "jvm": ["-Djava.library.path=${'$'}{natives_directory}", "-cp", "${'$'}{classpath}"]
      },
      "libraries": []
    }
    """.trimIndent()

    private fun installed(): InstalledVersion {
        val meta = VersionMetaParser.parse(json)
        return InstalledVersion(
            meta = meta,
            clientJar = File(tmp, "client.jar"),
            libraryClasspath = listOf("/libs/a.jar", "/libs/b.jar"),
            nativesDir = File(tmp, "natives"),
        )
    }

    @Test
    fun `substitutes account and version variables`() {
        val builder = LaunchCommandBuilder(paths, ctx)
        val cmd = builder.build(
            installed(),
            LaunchOptions(
                account = OfflineAccount("Steve"),
                gameDir = File(tmp, "inst"),
                javaExecutable = "/java/bin/java",
            ),
        )

        assertEquals("/java/bin/java", cmd.executable)
        assertContains(cmd.args, "Steve")
        assertContains(cmd.args, "test-1")
        assertTrue(cmd.args.contains("-Xmx2048M"))
        assertTrue(cmd.args.contains("net.minecraft.client.main.Main"))
    }

    @Test
    fun `classpath ends with the client jar and is joined with the platform separator`() {
        val cmd = LaunchCommandBuilder(paths, ctx).build(
            installed(),
            LaunchOptions(OfflineAccount("Steve"), File(tmp, "inst"), "java"),
        )
        val cpIndex = cmd.args.indexOf("-cp")
        val classpath = cmd.args[cpIndex + 1]
        val parts = classpath.split(File.pathSeparator)
        assertEquals(listOf("/libs/a.jar", "/libs/b.jar", File(tmp, "client.jar").absolutePath), parts)
    }

    @Test
    fun `unknown placeholders are left untouched`() {
        val out = LaunchCommandBuilder.substitute("x-\${nope}-y", mapOf("yes" to "1"))
        assertEquals("x-\${nope}-y", out)
    }

    @Test
    fun `game directory is created`() {
        val dir = File(tmp, "fresh-instance")
        assertFalse(dir.exists())
        LaunchCommandBuilder(paths, ctx).build(
            installed(),
            LaunchOptions(OfflineAccount("Alex"), dir, "java"),
        )
        assertTrue(dir.isDirectory)
    }

    @Test
    fun `offline account has legacy user type`() {
        val acc = OfflineAccount("Alex")
        assertIs<OfflineAccount>(acc)
        assertEquals("legacy", acc.userType)
        assertEquals("0", acc.accessToken)
    }
}
