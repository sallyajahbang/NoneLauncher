# CI log diagnosis and fixes

The uploaded GitHub Actions logs showed two root causes in the starter source:

1. `src/app.rs` used the pre-0.34 egui/eframe API while Cargo resolved eframe/egui 0.37:
   - `eframe::App::update` was replaced by `logic` + required `ui` methods.
   - `TopBottomPanel` and `SidePanel` were unified into `egui::Panel`.
   - Panels and `CentralPanel` now receive a mutable `egui::Ui` and use `show`.
   - `Context::style` / `set_style` were renamed to `global_style` / `set_global_style`; this app uses `global_style_mut`.
2. Linux stopped at `cargo fmt --all -- --check` because `src/app.rs` was not rustfmt-formatted.

The source in this patch migrates the UI to the eframe 0.37 API, formats the app source, removes an unused local, and updates the build notes. The GitHub Actions YAML itself successfully completed checkout, toolchain setup, dependency resolution, and Android SDK/NDK setup in the provided logs; the observed failures occurred later in source compilation or formatting.

## Validation performed in the patch environment

- `Cargo.toml` parsed successfully with Python `tomllib`.
- Both GitHub Actions workflow files parsed successfully as YAML.
- Regression checks confirmed that the old `App::update`, `SidePanel`, and `TopBottomPanel` calls are absent from `src/app.rs`.
- A basic Rust delimiter smoke check passed.

A full `cargo fmt`, `cargo check`, desktop build, or Android APK build was not run in this environment because the Rust toolchain and Android SDK are not installed here. The next authoritative compile result will be the next GitHub Actions run.
