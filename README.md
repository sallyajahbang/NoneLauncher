# None Launcher (Rust core + Kotlin UI)

Launcher Minecraft: Java Edition untuk Android. Arsitekturnya dibuat seperti Amethyst,
dengan core di Rust dan UI di Kotlin.

## Struktur

```
none-launcher/
├── Cargo.toml                 workspace Rust
├── crates/
│   ├── mc-core/               logika platform-independen (dapat diuji di host)
│   │   └── src/
│   │       ├── manifest.rs    version_manifest_v2 Mojang
│   │       ├── version.rs     parsing JSON versi, library, argumen
│   │       ├── rules.rs       evaluasi rules (os, arch, features)
│   │       ├── launch.rs      membangun perintah JVM + game
│   │       ├── download.rs    rencana unduhan, unduh + verifikasi SHA-1
│   │       └── auth.rs        akun offline (UUID vanilla)
│   └── mc-jni/                JNI bridge -> com.sally.nonelauncher.core.NativeBridge
└── android/                   app Gradle (Kotlin)
    └── app/src/main/java/com/sally/nonelauncher/
        ├── MainActivity.kt
        └── core/NativeBridge.kt
```

## Status

| Bagian | Status |
|---|---|
| Parsing manifest & JSON versi | Ditulis, belum dikompilasi/diuji (lihat catatan) |
| Evaluasi rules | Ditulis + unit test |
| Build perintah JVM (modern & legacy) | Ditulis + unit test |
| Rencana unduhan & verifikasi SHA-1 | Ditulis |
| Akun offline | Ditulis + unit test (UUID "Notch" vanilla) |
| JNI bridge | Ditulis, belum dibuild |
| App Android | Skeleton saja, belum dibuild |
| Login Microsoft | Belum (TODO di `auth.rs`) |
| Download aset (asset index) | Belum |
| Natives legacy (<1.19, ekstraksi jar) | Belum |
| Runtime JVM, GLFW, LWJGL, renderer | Belum diintegrasikan (lihat bagian Amethyst) |

## Build

### Core (host)

```bash
cargo test -p mc-core
```

### Library native untuk Android

Butuh Android NDK dan `cargo-ndk`:

```bash
rustup target add aarch64-linux-android
cargo install cargo-ndk
cargo ndk -t arm64-v8a -o android/app/src/main/jniLibs build -p mc-jni --release
```

Hasilnya `libmc_jni.so` di `android/app/src/main/jniLibs/arm64-v8a/`.

### APK

Buka folder `android/` di Android Studio, atau jalankan `gradle wrapper` lalu
`./gradlew :app:assembleDebug`.

## Integrasi runtime Amethyst (langkah berikutnya)

Yang belum ada di sini adalah bagian yang membuat Minecraft benar-benar berjalan:

1. **JRE**: pakai JRE ARM64 dari CI Amethyst (artifact `jre8-pojav` / JRE 17/21).
   Taruh di `assets/` atau modul runtime terpisah. Jalankan lewat JNI Invocation API
   (`libjvm.so`) dari native code.
2. **GLFW/LWJGL**: pakai `jre_lwjgl3glfw` dan LWJGL3 dari Amethyst. Stub GLFW
   terhubung ke EGL/surface Android.
3. **Renderer**: GL4ES, MobileGlues, Mesa/Zink, atau ANGLE, sesuai pilihan Amethyst.
4. **Audio/input**: OpenAL-Soft + oboe, dan pemetaan input touch/gamepad.

Repo rujukan: https://github.com/AngelAuraMC/Amethyst-Android (LGPL-3.0).
Untuk membangun JRE dan LWJGL sendiri, lihat repo `openjdk-build-multiarch` dan `lwjgl3`
milik AngelAuraMC.

## Lisensi

Core ini berlisensi LGPL-3.0-or-later, sama dengan Amethyst. Kode ini tidak menyalin
kode Amethyst. Jika nanti kode atau file dari Amethyst disalin, lisensi LGPL-3.0 dan
notice-nya wajib dipertahankan. Aset Minecraft tidak boleh didistribusikan. Launcher
harus mengunduhnya dari server resmi Mojang.
