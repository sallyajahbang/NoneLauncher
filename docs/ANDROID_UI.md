# Android UI layout

RustCraft is an Android-only application. The UI is rendered by egui through eframe/wgpu.

## Layout rules

- The app root explicitly allocates the complete available viewport with a top-left, top-down layout. This is important because eframe's root UI can inherit a centered layout; without overriding it, the content may collapse to a single row centered vertically.
- The main content is vertically scrollable and reserves space for persistent bottom navigation.
- Primary navigation has five large touch targets: Home, Worlds, Settings, Dev, and About.
- Developer diagnostics are a normal destination, not a floating overlay.
- The app does not request immersive fullscreen. Android system bars remain visible.
- Controls use at least 48 logical points for their interaction height where practical.

## Current screens

- **Home:** project status, voxel preview placeholder, and quick links.
- **Worlds:** singleplayer world list placeholder.
- **Settings:** render-distance control and runtime information.
- **Dev:** FPS, frame time, uptime, render distance, graphics backend, world status, and platform.
- **About:** build and project information.

## Known limitations

The voxel preview is currently a UI-painted placeholder, not the dedicated 3D chunk renderer. World creation and persistence are not implemented yet. Validate touch behavior and logical sizing on a real Android device after each UI change.
