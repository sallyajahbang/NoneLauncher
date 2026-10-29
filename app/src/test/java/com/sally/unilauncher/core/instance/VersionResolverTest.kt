package com.sally.unilauncher.core.instance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun write(game: File, id: String, json: String) {
        val dir = File(game, "versions/$id").apply { mkdirs() }
        File(dir, "$id.json").writeText(json)
    }

    @Test
    fun `reads mainClass, assetIndex and libraries from a single version`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "1.21.1", """
            {
              "mainClass": "net.minecraft.client.main.Main",
              "assetIndex": {"id": "16", "url": "https://x/16.json", "sha1": "abc", "size": 10},
              "libraries": [
                {
                  "name": "org.lwjgl:lwjgl:3.3.3",
                  "downloads": {"artifact": {
                    "path": "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3.jar",
                    "url": "https://repo/lwjgl.jar", "sha1": "s1", "size": 99
                  }}
                }
              ]
            }
        """.trimIndent())

        val r = VersionResolver(game).resolve("1.21.1")

        assertEquals("net.minecraft.client.main.Main", r.mainClass)
        assertEquals("16", r.assetIndex?.id)
        assertEquals(1, r.libraries.size)
        assertEquals("org.lwjgl:lwjgl", r.libraries[0].key)
        assertEquals("https://repo/lwjgl.jar", r.libraries[0].url)
        assertEquals(99L, r.libraries[0].size)
        assertEquals(listOf("1.21.1"), r.chain)
    }

    @Test
    fun `child inherits mainClass and assetIndex and overrides duplicate libraries`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "1.21.1", """
            {
              "mainClass": "net.minecraft.client.main.Main",
              "assetIndex": {"id": "16"},
              "libraries": [
                {"name": "a:lib:1.0"},
                {"name": "b:lib:1.0"}
              ]
            }
        """.trimIndent())
        write(game, "fabric-1", """
            {
              "inheritsFrom": "1.21.1",
              "libraries": [
                {"name": "a:lib:2.0"},
                {"name": "c:lib:1.0"}
              ]
            }
        """.trimIndent())

        val r = VersionResolver(game).resolve("fabric-1")

        assertEquals("net.minecraft.client.main.Main", r.mainClass)
        assertEquals("16", r.assetIndex?.id)
        assertEquals(listOf("fabric-1", "1.21.1"), r.chain)
        // Urutan: library anak dulu, lalu sisa dari induk. "a:lib" versi 2.0 menang atas 1.0.
        assertEquals(listOf("a:lib", "c:lib", "b:lib"), r.libraries.map { it.key })
        assertEquals("a/lib/2.0/lib-2.0.jar", r.libraries[0].path)
    }

    @Test
    fun `rules remove libraries that do not match linux`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "v", """
            {
              "libraries": [
                {"name": "x:mac:1", "rules": [{"action": "allow", "os": {"name": "osx"}}]},
                {"name": "x:linux:1", "rules": [{"action": "allow", "os": {"name": "linux"}}]},
                {"name": "x:all:1", "rules": [{"action": "allow"}, {"action": "disallow", "os": {"name": "osx"}}]},
                {"name": "x:feature:1", "rules": [{"action": "allow", "features": {"is_demo_user": true}}]}
              ]
            }
        """.trimIndent())

        val keys = VersionResolver(game, osName = "linux").resolve("v").libraries.map { it.key }

        assertEquals(listOf("x:linux", "x:all"), keys)
    }

    @Test
    fun `library without downloads uses maven path and default base url`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "v", """{"libraries": [{"name": "net.fabricmc:fabric-loader:0.16.0"}]}""")

        val lib = VersionResolver(game).resolve("v").libraries.single()

        assertEquals("net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar", lib.path)
        assertEquals(
            "https://libraries.minecraft.net/net/fabricmc/fabric-loader/0.16.0/fabric-loader-0.16.0.jar",
            lib.url
        )
        assertNull(lib.sha1)
    }

    @Test
    fun `library with custom base url uses it`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "v", """{"libraries": [{"name": "a.b:c:1.0", "url": "https://maven.example.com/"}]}""")

        val lib = VersionResolver(game).resolve("v").libraries.single()

        assertEquals("https://maven.example.com/a/b/c/1.0/c-1.0.jar", lib.url)
    }

    @Test
    fun `missing version throws VersionNotFoundException`() {
        val game = tmp.newFolder(".minecraft")
        assertThrows(VersionNotFoundException::class.java) {
            VersionResolver(game).resolve("tidak-ada")
        }
    }

    @Test
    fun `inheritance cycle throws instead of looping forever`() {
        val game = tmp.newFolder(".minecraft")
        write(game, "a", """{"inheritsFrom": "b"}""")
        write(game, "b", """{"inheritsFrom": "a"}""")

        assertThrows(IllegalStateException::class.java) {
            VersionResolver(game).resolve("a")
        }
    }
}
