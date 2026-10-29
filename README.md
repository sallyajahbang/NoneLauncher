# Javoxel

Launcher Minecraft Java Edition untuk Android.
Package: `com.sally.javoxel`

## Status

Scaffold awal. Yang sudah ada:

- Project Gradle (Kotlin, Jetpack Compose Material3, minSdk 26, targetSdk 35)
- `MainActivity` dengan satu layar: input versi Minecraft dan info Java yang dibutuhkan
- `requiredJavaMajor()` untuk memetakan versi Minecraft ke versi Java (8 / 17 / 21)
- `data/`: unduh manifest Mojang, JSON versi, client.jar, libraries (artifact utama), dan aset, dengan verifikasi SHA-1. Tombol "Unduh versi" di layar sudah memakainya. Unduhan ke `filesDir/minecraft/`.

Belum ada: natives per-classifier, unduh JRE, renderer, layer GLFW, input sentuh, dan auth.

## Build

Gradle wrapper belum disertakan. Generate sekali dengan Gradle yang terpasang:

```
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

APK ada di `app/build/outputs/apk/debug/`.

## Rencana berikutnya

1. Natives per-classifier untuk LWJGL (1.16.5 masih pakai format `natives`)
2. Package `runtime/` untuk unduh dan ekstrak JRE (sumber: build OpenJDK Android arm64)
3. Layer GLFW (LWJGL 3) dan EGL bridge via NDK / CMake
4. Renderer gl4es, lalu Zink sebagai opsi kedua
5. Input sentuh

## Catatan

- Lisensi belum ditentukan. Kalau ada kode dari Zalith Launcher 2 atau PojavLauncher yang dipakai, lisensinya harus GPL-3.0.
