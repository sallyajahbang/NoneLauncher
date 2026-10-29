//! RustRowser - browser ringan untuk desktop dan Android.
//!
//! - Desktop : `cargo run --release`
//! - Android : `cargo apk build --release --lib` (lihat .github/workflows/android.yml)

pub mod app;
pub mod fetch;
pub mod page;
pub mod tabs;

use app::BrowserApp;

/// Menjalankan browser di desktop (Linux/Windows/macOS).
pub fn run_desktop() -> eframe::Result {
    let options = eframe::NativeOptions::default();
    eframe::run_native(
        "RustRowser",
        options,
        Box::new(|_cc| Ok(Box::new(BrowserApp::new()))),
    )
}

/// Titik masuk Android (NativeActivity). Dipanggil otomatis oleh runtime Android.
#[cfg(target_os = "android")]
#[no_mangle]
fn android_main(app: android_activity::AndroidApp) {
    let options = eframe::NativeOptions {
        android_app: Some(app),
        ..Default::default()
    };
    let _ = eframe::run_native(
        "RustRowser",
        options,
        Box::new(|_cc| Ok(Box::new(BrowserApp::new()))),
    );
}
