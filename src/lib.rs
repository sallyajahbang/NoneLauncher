mod app;

pub use app::RustCraftApp;

pub fn run_desktop() -> eframe::Result {
    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Wgpu,
        viewport: eframe::egui::ViewportBuilder::default()
            .with_title("RustCraft — Native Voxel Runtime")
            .with_inner_size([1280.0, 720.0])
            .with_min_inner_size([800.0, 520.0]),
        ..Default::default()
    };

    eframe::run_native(
        "RustCraft",
        options,
        Box::new(|_cc| Ok(Box::new(RustCraftApp::default()))),
    )
}

#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub fn android_main(app: winit::platform::android::activity::AndroidApp) {
    use tracing_subscriber::EnvFilter;

    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );
    let _ = tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env())
        .try_init();

    let options = eframe::NativeOptions {
        renderer: eframe::Renderer::Wgpu,
        android_app: Some(app),
        viewport: eframe::egui::ViewportBuilder::default()
            .with_title("RustCraft")
            .with_fullscreen(true),
        ..Default::default()
    };

    if let Err(error) = eframe::run_native(
        "RustCraft",
        options,
        Box::new(|_cc| Ok(Box::new(RustCraftApp::default()))),
    ) {
        log::error!("RustCraft Android runtime failed: {error}");
    }
}
