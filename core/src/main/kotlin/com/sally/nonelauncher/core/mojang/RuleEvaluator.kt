package com.sally.nonelauncher.core.mojang

import com.sally.nonelauncher.core.model.RuntimeContext
import com.sally.nonelauncher.core.util.obj
import com.sally.nonelauncher.core.util.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Evaluates the `rules` arrays used by Mojang version metadata.
 *
 * Semantics (matching the vanilla launcher):
 *  - no rules at all  -> allowed
 *  - rules present    -> disallowed by default; each matching rule, in order,
 *                        sets the result to its action ("allow" / "disallow")
 */
object RuleEvaluator {

    fun isAllowed(rules: JsonArray?, ctx: RuntimeContext): Boolean {
        if (rules == null || rules.isEmpty()) return true

        var allowed = false
        for (element in rules) {
            val rule = element as? JsonObject ?: continue
            val action = rule.str("action") ?: continue
            if (matches(rule, ctx)) {
                allowed = action == "allow"
            }
        }
        return allowed
    }

    private fun matches(rule: JsonObject, ctx: RuntimeContext): Boolean {
        rule.obj("os")?.let { os ->
            os.str("name")?.let { if (it != ctx.osName) return false }
            os.str("arch")?.let { if (it != ctx.arch) return false }
            // "version" is a regex on the OS version; not applicable on Android.
        }

        rule.obj("features")?.let { features ->
            for ((feature, wanted) in features) {
                val expected = (wanted as? JsonPrimitive)?.booleanOrNull ?: continue
                val present = feature in ctx.features
                if (present != expected) return false
            }
        }
        return true
    }
}
