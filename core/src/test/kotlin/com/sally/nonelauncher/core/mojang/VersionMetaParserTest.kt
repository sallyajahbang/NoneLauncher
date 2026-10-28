package com.sally.nonelauncher.core.mojang

import com.sally.nonelauncher.core.model.RuntimeContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionMetaParserTest {

    private val sample = """
    {
      "id": "1.21.1",
      "type": "release",
      "mainClass": "net.minecraft.client.main.Main",
      "assetIndex": { "id": "16", "url": "https://example.invalid/16.json", "sha1": "abc", "size": 10 },
      "javaVersion": { "component": "java-runtime-delta", "majorVersion": 21 },
      "downloads": { "client": { "sha1": "deadbeef", "size": 5, "url": "https://example.invalid/client.jar" } },
      "arguments": {
        "game": ["--username", "${'$'}{auth_player_name}", { "rules": [{"action":"allow","features":{"has_custom_resolution":true}}], "value": ["--width", "${'$'}{resolution_width}"] }],
        "jvm": [{ "rules": [{"action":"allow","os":{"name":"linux"}}], "value": "-Djava.library.path=${'$'}{natives_directory}" }, "-cp", "${'$'}{classpath}"]
      },
      "libraries": [
        { "name": "org.lwjgl:lwjgl:3.3.3", "downloads": { "artifact": { "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar", "sha1": "1", "size": 2, "url": "https://example.invalid/lwjgl.jar" } } },
        { "name": "org.lwjgl:lwjgl:3.3.3:natives-linux",
          "downloads": { "artifact": { "path": "x", "sha1": "2", "size": 3, "url": "https://example.invalid/x.jar" },
                         "classifiers": { "natives-linux": { "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar", "sha1": "3", "size": 4, "url": "https://example.invalid/n.jar" } } },
          "natives": { "linux": "natives-linux" } },
        { "name": "org.example:windows-only:1.0", "rules": [{"action":"allow","os":{"name":"windows"}}],
          "downloads": { "artifact": { "path": "w.jar", "sha1": "4", "size": 5, "url": "https://example.invalid/w.jar" } } }
      ]
    }
    """.trimIndent()

    private val ctx = RuntimeContext(osName = "linux", arch = "arm64")

    @Test
    fun `parses core fields`() {
        val meta = VersionMetaParser.parse(sample)
        assertEquals("1.21.1", meta.id)
        assertEquals("net.minecraft.client.main.Main", meta.mainClass)
        assertEquals("16", meta.assetIndex.id)
        assertEquals(21, meta.javaMajorVersion)
        assertEquals("https://example.invalid/client.jar", meta.clientJar.url)
    }

    @Test
    fun `library list keeps natives and skips platform-disallowed libs after resolve`() {
        val meta = VersionMetaParser.parse(sample)
        val resolved = LibraryResolver.resolve(meta.libraries, ctx)

        // lwjgl main jar + linux natives; windows-only lib is dropped.
        assertEquals(2, resolved.size)
        assertFalse(resolved[0].isNative)
        assertTrue(resolved[1].isNative)
        assertEquals("org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar", resolved[1].relativePath)
    }

    @Test
    fun `argument entries keep their rules`() {
        val meta = VersionMetaParser.parse(sample)
        assertEquals(3, meta.gameArguments.size)
        // The conditional --width/--height pair is only allowed with the custom resolution feature.
        val conditional = meta.gameArguments[2]
        assertEquals(listOf("--width", "\${resolution_width}"), conditional.values)
        assertTrue(conditional.rules != null)
        // jvm: [rule-gated -Djava.library.path, "-cp", "${classpath}"]
        assertEquals(3, meta.jvmArguments.size)
    }
}
