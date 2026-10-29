# Javoxel

Launcher Minecraft Java Edition untuk Android.
Package: `com.sally.javoxel`

## Status

Scaffold awal. Yang sudah ada:

- Project Gradle (Kotlin, Jetpack Compose Material3, minSdk 26, targetSdk 35)
- `MainActivity` dengan satu layar: input versi Minecraft dan info Java yang dibutuhkan
- `requiredJavaMajor()` untuk memetakan versi Minecraft ke versi Java (8 / 17 / 21)
- `data/`: unduh manifest Mojang, JSON versi, client.jar, libraries (artifact utama), dan aset, dengan verifikasi SHA-1. Tombol "Unduh versi" di layar sudah memakainya. Unduhan ke `filesDir/minecraft/`.

- `runtime/`: ekstrak JRE dari assets (`assets/jre/jre-<major>/`: `universal.tar.xz` opsional dan `bin-arm64.tar.xz`) ke `getExternalFilesDir()/runtime/java-<major>/`, lalu set bit eksekusi. Tombol "Siapkan Java" di layar memakainya.

**Catatan JRE:** yang di-bundle saat ini hanya `bin-arm64.tar.xz` untuk JRE 8, 17, dan 21, tanpa `universal.tar.xz`. Bagian class library (mis. `lib/rt.jar` untuk JRE 8, `lib/modules` untuk 17 dan 21) belum ada, jadi JRE belum bisa dijalankan.

Belum ada: natives per-classifier, renderer, layer GLFW, input sentuh, dan auth.

## Build

Gradle wrapper belum disertakan. Generate sekali dengan Gradle yang terpasang:

```
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

APK ada di `app/build/outputs/apk/debug/`.

## Rencana berikutnya

1. Natives per-classifier untuk LWJGL (1.16.5 masih pakai format `natives`)
2. Isi URL JRE 8, 17, dan 21 di `runtime/JavaSources.kt` setelah diverifikasi
3. Layer GLFW (LWJGL 3) dan EGL bridge via NDK / CMake
4. Renderer gl4es, lalu Zink sebagai opsi kedua
5. Input sentuh

## Catatan

- Lisensi belum ditentukan. Kalau ada kode dari Zalith Launcher 2 atau PojavLauncher yang dipakai, lisensinya harus GPL-3.0.
