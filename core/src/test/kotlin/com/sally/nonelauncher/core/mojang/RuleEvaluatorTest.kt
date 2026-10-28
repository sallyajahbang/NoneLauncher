package com.sally.nonelauncher.core.mojang

import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.util.LauncherJson
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleEvaluatorTest {

    private val linux = RuntimeContext(osName = "linux", arch = "arm64")

    private fun rules(json: String): JsonArray = LauncherJson.parseToJsonElement(json).jsonArray

    @Test
    fun `no rules means allowed`() {
        assertTrue(RuleEvaluator.isAllowed(null, linux))
        assertTrue(RuleEvaluator.isAllowed(rules("[]"), linux))
    }

    @Test
    fun `os rule allows only the matching os`() {
        val r = rules("""[{"action":"allow","os":{"name":"linux"}}]""")
        assertTrue(RuleEvaluator.isAllowed(r, linux))
        assertFalse(RuleEvaluator.isAllowed(r, linux.copy(osName = "osx")))
    }

    @Test
    fun `disallow for os excludes that os but allows others when preceded by allow`() {
        val r = rules("""[{"action":"allow"},{"action":"disallow","os":{"name":"osx"}}]""")
        assertTrue(RuleEvaluator.isAllowed(r, linux))
        assertFalse(RuleEvaluator.isAllowed(r, linux.copy(osName = "osx")))
    }

    @Test
    fun `arch mismatch rejects the rule`() {
        val r = rules("""[{"action":"allow","os":{"name":"linux","arch":"x86"}}]""")
        assertFalse(RuleEvaluator.isAllowed(r, linux))
    }

    @Test
    fun `feature rules depend on enabled features`() {
        val demo = rules("""[{"action":"allow","features":{"is_demo_user":true}}]""")
        assertFalse(RuleEvaluator.isAllowed(demo, linux))
        assertTrue(RuleEvaluator.isAllowed(demo, linux.copy(features = setOf("is_demo_user"))))
    }

    @Test
    fun `rules without a matching allow are disallowed`() {
        val r = rules("""[{"action":"allow","os":{"name":"windows"}}]""")
        assertFalse(RuleEvaluator.isAllowed(r, linux))
    }

    @Test
    fun `mavenPath handles classifiers`() {
        assertEquals(
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar",
            LibraryResolver.mavenPath("org.lwjgl:lwjgl:3.3.3:natives-linux"),
        )
        assertEquals(
            "com/mojang/brigadier/1.0.18/brigadier-1.0.18.jar",
            LibraryResolver.mavenPath("com.mojang:brigadier:1.0.18"),
        )
    }
}
