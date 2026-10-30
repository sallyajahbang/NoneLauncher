//! Android-native application entry point for RustCraft.

#[cfg(target_os = "android")]
mod app;

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub fn android_main(app: winit::platform::android::activity::AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );

    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Wgpu,
        android_app: Some(app),
        // Do not force fullscreen here. Let Android keep the status and
        // navigation bars outside the app content so UI controls are not
        // drawn underneath system icons/gestures.
        viewport: eframe::egui::ViewportBuilder::default().with_title("RustCraft"),
        ..Default::default()
    };

    if let Err(error) = eframe::run_native(
        "RustCraft",
        options,
        Box::new(|_cc| Ok(Box::new(app::RustCraftApp::default()))),
    ) {
        log::error!("RustCraft Android runtime failed: {error}");
    }
}
