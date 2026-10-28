package com.sally.launcher

/** One developer diagnostic. [id] selects the callback in the UI; [busyKey] marks it as running. */
internal data class DeveloperAction(
    val id: String,
    val title: String,
    val description: String,
    val busyKey: String?,
)

/** Text shown in the developer options section. Kept separate from the UI so it can be unit tested. */
internal object DeveloperOptionsText {
    val actions: List<DeveloperAction> = listOf(
        DeveloperAction(
            id = "prepare",
            title = "Siapkan profil launch",
            description = "Cek metadata, classpath, runtime, akun, dan penghalang.",
            busyKey = "prepare",
        ),
        DeveloperAction(
            id = "probe",
            title = "Probe loader JVM",
            description = "Cek libjli dan libjvm. Tidak menjalankan Minecraft.",
            busyKey = "probe",
        ),
        DeveloperAction(
            id = "smoke",
            title = "Tes runtime Java",
            description = "Jalankan proses JLI sekali pakai dengan -version.",
            busyKey = "smoke",
        ),
        DeveloperAction(
            id = "graphics",
            title = "Tes grafis & input",
            description = "Buka permukaan diagnostik GLES dan input.",
            busyKey = null,
        ),
        DeveloperAction(
            id = "argv",
            title = "Lihat argumen launch terakhir",
            description = "Tampilkan argumen JVM yang terakhir dikirim, token disamarkan, dan versi build.",
            busyKey = null,
        ),
        DeveloperAction(
            id = "pojav",
            title = "Tes jembatan Pojav",
            description = "Buka diagnostik jembatan surface / JNI.",
            busyKey = null,
        ),
    )

    const val DISABLE_LABEL = "Matikan mode developer"
    const val ENABLE_HINT = "Ketuk 7 kali untuk mode developer."
    const val GROUP_TITLE = "Developer"
}
