# Uni Launcher

Launcher Minecraft Java Edition untuk Android, ditulis dari nol.
Zalith Launcher 2 dipakai sebagai bahan belajar (lihat `docs/ZALITH_STUDY.md`), bukan sebagai basis kode.

## Status: tahap 2 (fondasi + library)
- [x] Proyek Gradle + Compose
- [x] Pembacaan versi terpasang dari `versions/<id>/<id>.json` (`InstanceScanner`)
- [x] Daftar versi dan pemilihan di layar utama
- [x] Test unit untuk scanner
- [x] Pembacaan library dan asset (`VersionResolver`: inheritsFrom, rules OS, asset index)
- [ ] Unduhan file yang belum ada
- [ ] Penyusunan argumen JVM
- [ ] Runtime Java
- [ ] Renderer dan bridge native
- [ ] Peluncuran game

## Build
1. Buka folder ini di Android Studio (Android Studio akan membuat Gradle wrapper otomatis).
2. Build dan jalankan modul `app`.
3. Test unit: `./gradlew :app:testDebugUnitTest`

Letakkan folder game di `Android/data/com.sally.unilauncher/files/.minecraft/versions/`.

## CI (GitHub Actions)
Workflow `.github/workflows/android.yml` menjalankan test unit lalu membangun APK debug.
APK bisa diunduh dari tab Actions (artifact `uni-launcher-debug-apk`).
Workflow memakai Gradle 8.10.2 yang dipasang langsung, jadi tidak perlu Gradle wrapper di repo.
Saat sudah stabil, jalankan `gradle wrapper --gradle-version 8.10.2` sekali lalu commit `gradlew` dan `gradle/wrapper/`.

## Lisensi
GPL-3.0 (lihat `LICENSE`). Teks lisensi diambil dari repo Zalith Launcher 2 yang juga GPL-3.0.
