# Android UI layout

RustCraft is designed for Android phones first. The UI uses egui/eframe and adapts to the current logical viewport size.

## Portrait

- A compact app bar with the RustCraft name, Java compatibility target, and Developer shortcut.
- One-column scrollable page content. Cards use the available width instead of fixed desktop columns.
- Four large bottom navigation targets: Home, Worlds, Settings, and About.
- Developer diagnostics are a normal screen, never a floating window that covers gameplay or navigation.

## Landscape

- The bottom navigation is replaced by a compact top navigation row to preserve vertical room.
- The content remains scrollable and the voxel preview height is reduced.
- Long content can scroll instead of being clipped by a fixed desktop panel.

## Android system UI

- The app does not request immersive fullscreen.
- Status and navigation bars remain visible with a dark matching theme.
- For target SDK 35, the API 35 resource opts out of enforced edge-to-edge layout so the app surface is not drawn behind system bars on Android 15.
- The activity supports portrait and landscape rotation.

## Interaction

- Buttons and navigation use touch-sized hit areas (approximately 44–48 logical points).
- Diagnostics live under Developer and can also be opened from Settings.
- No hover-only action is required.

The Android screen should still be checked on physical devices at both portrait and landscape orientations. egui points are logical UI units; final sizing depends on device density and the Android window viewport.
