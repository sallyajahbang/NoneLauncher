# Catatan belajar: cara kerja Zalith Launcher 2

Dokumen ini memetakan komponen Zalith Launcher 2 untuk dipelajari.
Path mengacu ke `ZalithLauncher/src/main/` di repo Zalith (versi yang kamu upload).

Aturan main: **baca untuk memahami, tulis ulang sendiri.** Jangan menyalin kode Zalith.
Kalau kode disalin, proyek wajib GPL-3.0. Pola dan ide (misalnya urutan tahap peluncuran)
boleh dipelajari dan dipakai.

## Peta komponen

| Area | Lokasi (java/com/sally/unilauncher/...) | Fungsi |
|---|---|---|
| Akun | `game/account` | Login, penyimpanan akun, refresh token |
| Download engine | `game/download/engine` | Downloader batch, retry, pelacakan progres |
| Instalasi game | `game/download/game` | Mengunduh versi, library, asset; menggabungkan JSON (`GameJsonMerger`) |
| Loader mod | `game/download/game/fabric`, `forge`, `optifine` | Instalasi Fabric, Forge, OptiFine |
| Versi terpasang | `game/version/installed` | Model `Version`, parser JSON, `VersionsManager` (daftar sebagai StateFlow) |
| Peluncuran | `game/launch/GameLauncher.kt` | Pipeline utama peluncuran (`launchGame`) |
| Argumen JVM | `game/launch/JvmLauncher.kt`, `LaunchArgs.kt` | Menyusun argumen dan menjalankan JVM |
| Alur tugas | `game/launch/GameLaunchFlow.kt` | Membungkus peluncuran sebagai daftar tugas bertahap dengan error handling |
| Service | `game/launch/GameService.kt` | Foreground service agar proses game tetap hidup |
| Runtime Java | `game/multirt` | Mengelola banyak JRE (`RuntimesManager`) |
| Renderer | `game/renderer` | Interface `RendererInterface` dan daftar renderer (GL4ES, Vulkan, dll.) |
| Input | `game/input`, `game/sdl`, `game/control` | Keyboard, mouse, gamepad, overlay kontrol |
| UI | `ui/activities`, `ui/screens`, `ui/theme` | Activity, layar Compose, tema |
| Native (JNI) | `src/main/jni` | Bridge C: JRE launcher, EGL, input, AWT, logger |

## Urutan belajar yang disarankan

1. **Model data dulu.** Baca `game/version/installed/Version.kt` dan `VersionInfo.kt`.
   Pahami bentuk JSON versi Minecraft, terutama `inheritsFrom`. Ini inti dari semua proses lain.
2. **Pencarian versi.** Baca `VersionsManager.kt`. Lihat bagaimana daftar versi diekspos
   sebagai `StateFlow` dan dibaca UI. Ini mirip dengan `InstanceScanner` di proyek baru.
3. **Instalasi.** Baca `game/download/game/GameInstaller.kt` dan `GameJsonMerger.kt`.
   Perhatikan cara library dan asset diturunkan dari JSON, lalu cara loader mod menimpa JSON dasar.
4. **Download engine.** Baca `game/download/engine`. Perhatikan pemisahan antara
   "daftar file yang dibutuhkan" dan "cara mengunduhnya".
5. **Pipeline peluncuran.** Baca `GameLauncher.kt` (fungsi `launchGame`), lalu `GameLaunchFlow.kt`.
   Ini tempat semua tahap bertemu: runtime, library, argumen, proses.
6. **Runtime Java.** Baca `game/multirt`. Lihat bagaimana versi JRE dipilih sesuai versi Minecraft.
7. **Renderer dan native.** Baca `RendererInterface.kt`, lalu `src/main/jni/jre_launcher.c`
   dan `ctxbridges/`. Ini bagian tersulit: jembatan OpenGL/EGL dari Android ke JVM.
8. **Input.** Baca `game/sdl` dan `input_bridge_v3.c`. Ini sumber banyak bug di perangkat.
9. **UI terakhir.** Baru baca `MainActivity.kt` dan `VMActivity.kt`. Keduanya besar
   (808 dan 1154 baris) dan sangat bergantung pada DI (Hilt) dan ViewModel.

## Catatan teknis yang perlu diperhatikan

- Zalith memakai Hilt untuk DI. Proyek baru ini sengaja belum memakainya agar sederhana.
  Tambahkan nanti jika jumlah dependensi sudah banyak.
- `minSdk` Zalith 26, `targetSdk` 34, `compileSdk` 37. Proyek baru memakai `compileSdk` 35
  yang lebih mudah dipasang di SDK yang umum.
- Zalith butuh JDK 11 untuk build. Proyek baru memakai target Java 17.
- Penyimpanan game sekarang di folder aplikasi (`getExternalFilesDir`). Akses folder
  publik perlu SAF atau izin storage, dan itu perlu didesain tersendiri.
