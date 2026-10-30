//! Log sederhana yang ditulis ke file, supaya bisa dibaca lewat File Manager
//! tanpa PC dan tanpa logcat.
//!
//! Lokasi file (Android 10 mengizinkan File Manager membuka folder ini):
//!   Android/data/dev.rustcube/files/rustcube_log.txt
//!
//! File dikosongkan setiap app dibuka. Jika file tidak ada sama sekali,
//! berarti app crash sebelum kode Rust sempat berjalan.

use std::fs::{self, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::sync::OnceLock;

static LOG_PATH: OnceLock<PathBuf> = OnceLock::new();

pub fn init(external_dir: Option<PathBuf>) {
    let dir = external_dir.unwrap_or_else(|| {
        PathBuf::from("/storage/emulated/0/Android/data/dev.rustcube/files")
    });
    let _ = fs::create_dir_all(&dir);
    let path = dir.join("rustcube_log.txt");
    let _ = fs::write(&path, "");
    let _ = LOG_PATH.set(path);

    // Panic apa pun (termasuk di thread lain) ikut tercatat beserta backtrace.
    std::panic::set_hook(Box::new(|info| {
        let bt = std::backtrace::Backtrace::force_capture();
        dlog(format!("PANIC: {info}\n{bt}"));
    }));
}

/// Tulis satu baris ke file log. Dibuka dan ditutup setiap kali agar
/// isi tetap tersimpan walaupun app crash mendadak.
pub fn dlog(msg: impl AsRef<str>) {
    if let Some(path) = LOG_PATH.get() {
        if let Ok(mut f) = OpenOptions::new().create(true).append(true).open(path) {
            let _ = writeln!(f, "{}", msg.as_ref());
        }
    }
}
