# RustRowser

Browser ringan yang ditulis dari nol dengan Rust. Fokus v0.1: **fungsi dulu**, dengan
manajemen RAM yang hemat untuk Android.

## Fitur (v0.1)

- Bilah alamat; input yang bukan URL otomatis dicari (DuckDuckGo)
- Tab banyak, tombol tutup, tab baru
- Riwayat maju/mundur per tab
- Muat halaman di thread latar belakang (UI tidak macet)
- Tautan halaman (panel "Tautan", bisa dilipat)
- Render teks dengan virtualisasi

## Strategi memori (untuk Android)

| Teknik | Lokasi | Dampak |
|---|---|---|
| Maksimal 3 tab memuat isi sekaligus, sisanya di-unload (LRU) | `tabs.rs` `enforce_budget` | RAM per tab tetap terbatas |
| Tab yang di-unload hanya menyimpan URL, dimuat ulang saat dibuka | `tabs.rs` `Content::Unloaded` | Hampir nol RAM untuk tab lama |
| Batas unduhan 4 MB, sisanya dipotong | `fetch.rs` `MAX_BODY_BYTES` | Halaman raksasa tidak meledakkan memori |
| DOM dibuang setelah diekstrak; yang disimpan hanya teks & tautan | `page.rs` | Puncak RAM jauh lebih rendah |
| Teks maks 300 KB, tautan maks 300 | `page.rs` | Batas atas yang pasti |
| Render virtual (`show_rows`) | `app.rs` | Hanya baris yang terlihat yang digambar |
| Riwayat 30 URL per tab, tanpa isi | `tabs.rs` `MAX_HISTORY` | Riwayat tidak menumpuk konten |
| Tanpa gambar/CSS/JS | seluruh desain | Konsumsi RAM & CPU rendah |
| Profil release `opt-level="s"`, LTO, strip | `Cargo.toml` | APK kecil |

Angka-angka di atas bisa diubah di konstanta masing-masing file.

## Build

### Desktop (untuk uji cepat)

```bash
cargo test --lib
cargo run --release
```

### Android (APK)

Build otomatis di GitHub Actions lewat `.github/workflows/build.yml`:
1. Push ke `main`, atau jalankan manual dari tab **Actions** → **Run workflow**.
2. Unduh artifact `RustRowser-apk` dari halaman run tersebut.

Build lokal (perlu Android SDK + NDK 26):

```bash
rustup target add aarch64-linux-android
cargo install cargo-apk --locked
cargo apk build --release --lib
# hasil: target/release/apk/rustrowser.apk
```

## Catatan & batasan jujur

- Ini **browser berbasis teks** (HTML → teks + tautan). Belum ada mesin CSS/JavaScript/layout
  penuh. Situs yang sangat bergantung JavaScript tidak akan tampil benar.
- HTTPS memakai `rustls` (tanpa OpenSSL), jadi cross-compile ke Android lebih mudah.
- Build Android belum diuji di lingkungan pengembangan ini; jika workflow gagal, kirimkan log-nya
  untuk diperbaiki.
- Untuk versi berikutnya (v0.2+): bookmark, cache halaman di disk, mesin layout (taffy),
  dukungan gambar lazy, dan `onTrimMemory` Android untuk melepas isi tab saat RAM kritis.

## Struktur

```
src/
  lib.rs     titik masuk (desktop & android_main)
  main.rs    binary desktop
  app.rs     UI egui, aksi, render virtual
  tabs.rs    tab, riwayat, anggaran memori (LRU)
  fetch.rs   unduh halaman, batas ukuran, normalisasi alamat
  page.rs    parser HTML -> teks/tautan
.github/workflows/build.yml   uji + build APK
```

## Lisensi

MIT
