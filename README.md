# None Launcher

Launcher Minecraft: Java Edition untuk Android, ditulis dari awal dengan Kotlin.

> **Status: tahap awal.** Fondasi logika sudah ada dan teruji (metadata versi,
> download dengan verifikasi SHA-1, aturan platform, ekstraksi native, akun offline,
> pembentukan argumen JVM). Menjalankan game di Android **belum** berfungsi karena
> bridge JVM/renderer native belum di-port. UI masih polos sengaja.

## Yang sudah ada

| Modul | Isi |
|---|---|
| `core/` | Modul Kotlin/JVM murni: manifest & metadata versi Mojang, resolver library + native, downloader paralel dengan retry dan SHA-1, installer (client, library, native, aset), akun offline, pembentuk perintah launch. Ada unit test dan test end-to-end terhadap HTTP server lokal. |
| `app/` | Aplikasi Android minimal: pilih versi, install, launch. `AndroidGameBackend` masih stub. |

## Yang belum ada

- Bridge JVM native Android (JNI + renderer GL/Vulkan). Ini bagian terberat dan akan di-port dari referensi di bawah.
- Login Microsoft (saat ini hanya akun offline).
- Manajemen Java runtime (struktur path sudah disiapkan di `LauncherPaths`).
- Mod loader (Fabric, Forge, NeoForge, Quilt) dan modpack.
- Desain UI.

## Build

Prasyarat: JDK 21, Android SDK (untuk modul `app`).

```bash
./gradlew :core:test          # unit test core
./gradlew :app:assembleDebug  # APK debug
```

CI ada di `.github/workflows/build.yml`: menjalankan test core lalu membangun APK debug dan mengunggahnya sebagai artifact.

## Lisensi

Proyek ini dilisensikan di bawah **GNU General Public License v3.0** (lihat [LICENSE](LICENSE)),
sama dengan proyek referensi yang dijelaskan di bawah.

## Kredit dan referensi

- **[ZalithLauncher2](https://github.com/ZalithLauncher/ZalithLauncher2)** (GPL-3.0), oleh tim ZalithLauncher.
  Dijadikan rujukan untuk struktur launcher Android dan untuk bridge JNI yang
  akan di-port dari `ZalithLauncher/src/main/jni/` dan `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/bridge/`.
  Saat ini kode di repo ini ditulis ulang dari awal; belum ada kode yang disalin dari ZalithLauncher2.
- **[PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)**, sumber asli bridge JVM Android
  yang dipakai ZalithLauncher2.
- **Mojang / Minecraft**: metadata versi dan aset diambil dari layanan publik Mojang
  (`piston-meta.mojang.com`, `resources.download.minecraft.net`). Minecraft adalah merek dagang Mojang Studios;
  proyek ini tidak berafiliasi dengan Mojang atau Microsoft.

Jika nanti kode dari ZalithLauncher2 atau PojavLauncher disalin, berkas itu harus tetap
menyertakan header lisensi dan atribusi aslinya.
