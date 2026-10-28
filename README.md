# None Launcher

Launcher Minecraft Java untuk Android, ditulis dari awal.

- Package: `com.sally.nonelauncher`
- Lisensi: GPL-3.0-only (lihat [LICENSE](LICENSE))
- Target awal: Android 8.0+ (minSdk 26), arm64-v8a

## Status

Kerangka proyek saja. Belum ada fungsi launcher. Milestone pertama adalah menjalankan Minecraft 1.16.5 sampai layar title muncul, tanpa autentikasi dan tanpa mod loader.

## Build

Build dijalankan otomatis di GitHub Actions (`.github/workflows/build.yml`) pada setiap push ke `main`, pull request, atau tag `v*`. APK hasil build tersedia di tab Actions sebagai artifact `none-launcher-apk`. Untuk tag `v*`, APK juga dilampirkan ke GitHub Release.

Build lokal:

```bash
gradle :app:assembleDebug
```

Membutuhkan JDK 17, Android SDK (platform 34), dan Gradle 8.10.

## Lisensi

Copyright (C) 2026 Buntuk

Program ini adalah perangkat lunak bebas: Anda dapat mendistribusikan dan/atau memodifikasinya di bawah ketentuan GNU General Public License versi 3, sebagaimana diterbitkan oleh Free Software Foundation. Program ini didistribusikan tanpa jaminan apa pun. Lihat [LICENSE](LICENSE) untuk teks lengkap.

Aset dan JAR Minecraft tidak termasuk dalam repositori ini dan tidak didistribusikan ulang. Launcher hanya mengunduh file dari server Mojang.
