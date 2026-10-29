package com.sally.javoxel.game

import com.sally.javoxel.data.VersionMeta
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class LaunchParams(
    /** Folder berisi versions/, libraries/, dan assets/ */
    val baseDir: File,
    val gameDir: File,
    /** JAVA_HOME hasil JreInstaller.ensureJre() */
    val javaHome: File,
    val nativesDir: File,
    val playerName: String = "Player",
    val memoryMb: Int = 1024,
)

/**
 * Menyusun perintah untuk menjalankan Minecraft: java, argumen JVM, main class, lalu argumen game.
 * Belum dihubungkan ke tombol Launch. Layer GLFW dan natives belum ada.
 */
object LaunchCommand {

    fun build(meta: VersionMeta, p: LaunchParams): List<String> {
        val classpath = (
            meta.libraries.map { File(p.baseDir, "libraries/${it.path}") } +
                File(p.baseDir, "versions/${meta.id}/${meta.id}.jar")
            ).joinToString(":") { it.path }

        val vars = mapOf(
            "auth_player_name" to p.playerName,
            "version_name" to meta.id,
            "game_directory" to p.gameDir.path,
            "assets_root" to File(p.baseDir, "assets").path,
            "assets_index_name" to meta.assetIndex.id,
            "auth_uuid" to "00000000000000000000000000000000",
            "auth_access_token" to "0",
            "user_type" to "legacy",
            "version_type" to "release",
            "natives_directory" to p.nativesDir.path,
            "launcher_name" to "javoxel",
            "launcher_version" to "0.1.0",
            "classpath" to classpath,
        )

        val json = JSONObject(meta.raw)
        val jvm = mutableListOf<String>()
        val game = mutableListOf<String>()

        val args = json.optJSONObject("arguments")
        if (args != null) {
            jvm += expand(args.optJSONArray("jvm"), vars)
            game += expand(args.optJSONArray("game"), vars)
        } else {
            // Format lama (sebelum 1.13): argumen game berupa satu string.
            jvm += listOf("-Djava.library.path=${p.nativesDir.path}", "-cp", classpath)
            game += json.optString("minecraftArguments")
                .split(" ")
                .filter { it.isNotBlank() }
                .map { substitute(it, vars) }
        }

        return buildList {
            add(File(p.javaHome, "bin/java").path)
            add("-Xmx${p.memoryMb}M")
            addAll(jvm)
            add(meta.mainClass)
            addAll(game)
        }
    }

    /** Ganti ${nama} dengan nilai dari [vars]. Yang tidak dikenal dibiarkan. */
    private fun substitute(text: String, vars: Map<String, String>): String =
        Regex("""\$\{(\w+)}""").replace(text) { match ->
            vars[match.groupValues[1]] ?: match.value
        }

    /**
     * Ambil string dari array argumen. Entri berupa String atau objek dengan "rules" dan "value".
     */
    private fun expand(array: JSONArray?, vars: Map<String, String>): List<String> {
        if (array == null) return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            when (val item = array.get(i)) {
                is String -> out += substitute(item, vars)
                is JSONObject -> {
                    if (!isAllowed(item.optJSONArray("rules"))) continue
                    when (val value = item.get("value")) {
                        is String -> out += substitute(value, vars)
                        is JSONArray -> for (j in 0 until value.length()) {
                            out += substitute(value.getString(j), vars)
                        }
                    }
                }
            }
        }
        return out
    }

    /** Sama seperti di VersionRepository: aturan os dan features dianggap tidak cocok. */
    private fun isAllowed(rules: JSONArray?): Boolean {
        if (rules == null) return true
        var allowed = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val matches = rule.optJSONObject("os") == null && !rule.has("features")
            if (matches) allowed = rule.getString("action") == "allow"
        }
        return allowed
    }
}
