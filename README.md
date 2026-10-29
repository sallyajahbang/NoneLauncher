# Javoxel

Launcher Minecraft Java Edition untuk Android.
Package: `com.sally.javoxel`

## Status

Sudah ada:

- Project Gradle (Kotlin, Jetpack Compose Material3, minSdk 26, targetSdk 28, arm64 saja).
- `MainActivity` dengan satu layar: input versi Minecraft, tombol Unduh, Siapkan Java, dan Launch.
- `requiredJavaMajor()` sebagai tebakan Java dari nomor versi. Saat aksi dijalankan, nilai dari `javaVersion` di JSON Mojang lebih dipakai.
- `data/`: unduh manifest Mojang, JSON versi, client.jar, libraries (artifact utama), dan aset, dengan verifikasi SHA-1. Unduhan ke `filesDir/minecraft/`. `VersionRepository.readLocal()` membaca versi yang sudah diunduh tanpa jaringan.
- `runtime/JreInstaller`: ekstrak JRE dari assets (`assets/jre/jre-<major>/`: `universal.tar.xz` opsional dan `bin-arm64.tar.xz`) ke `filesDir/runtime/java-<major>/`, lalu set bit eksekusi.
- `runtime/JvmLauncher` dan `app/src/main/cpp/javoxel_jvm.c`: membuka `libjli.so` dari JRE dengan `dlopen`, lalu memanggil `JLI_Launch` di dalam proses app. Pendekatan ini sama dengan launcher lain (lihat catatan lisensi).
- `game/GameLauncher`: membaca versi lokal, menyusun argumen lewat `LaunchCommand`, lalu menjalankan JVM. Tombol Launch di layar memakai ini.

Belum ada, dan bagian ini belum diuji di perangkat:

- Library class JRE. `bin-arm64` saja belum cukup, perlu `lib/modules` (Java 17 dan 21) atau `lib/rt.jar` (Java 8). Mungkin ada di `universal.tar.xz`.
- Natives per-classifier untuk LWJGL. Untuk 1.16.5 formatnya masih `natives`.
- Layer GLFW (LWJGL 3) dan surface Android.
- Renderer: gl4es, lalu Zink sebagai opsi kedua.
- Audio (OpenAL).
- Input sentuh.
- Akun. Sekarang offline dengan UUID nol.

Catatan kondisi saat ini: tombol Launch akan mencoba menjalankan JVM. Minecraft kemungkinan besar gagal saat memuat LWJGL (belum ada natives dan GLFW). Itu wajar untuk tahap ini. Yang dibuktikan adalah JVM bisa start dari dalam app.

## Build

Gradle wrapper belum disertakan. Generate sekali dengan Gradle yang terpasang:

```
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

APK ada di `app/build/outputs/apk/debug/`.

Build native membutuhkan Android NDK dan CMake 3.22.1 (lewat Android Studio SDK Manager, atau `sdkmanager`). `javoxel_jvm.c` sudah lolos kompilasi `gcc -std=c11 -Wall -Wextra` terhadap `jni.h` JDK 21. Build Android belum dicoba.

## Rencana berikutnya

1. Pastikan JRE arm64 lengkap (library class) dan uji Launch sampai JVM mulai.
2. Natives per-classifier untuk LWJGL.
3. Layer GLFW (LWJGL 3) dan EGL bridge.
4. Renderer gl4es, lalu Zink.
5. Audio (OpenAL).
6. Input sentuh.
7. Isi URL JRE di `runtime/JavaSources.kt` setelah diverifikasi, supaya JRE bisa diunduh, bukan dibundle.

## Catatan

- Lisensi belum ditentukan. Referensi arsitektur: ZalithLauncher2 (GPL-3.0). Kode di sini ditulis sendiri berdasarkan API `JLI_Launch`, bukan disalin dari ZalithLauncher2. Kalau nanti ada kode yang disalin langsung, lisensi project harus GPL-3.0.
- `targetSdk = 28` sengaja dipilih supaya binary JRE bisa dieksekusi dari `filesDir`. Ini cocok untuk instalasi APK langsung. Untuk Google Play, target SDK harus dinaikkan dan perlu strategi lain untuk eksekusi JRE.
