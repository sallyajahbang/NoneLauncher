# Android UI blank-screen fix

The previous build showed only the brand/version row centered vertically. The app root inherited a centered egui layout, and the nested vertical layout did not claim the viewport as a full-screen top-left layout.

The fix in `src/app.rs` explicitly allocates `ui.available_size()` using `Layout::top_down(Align::Min)` before drawing the app frame. The content area is then split into a scrollable page and persistent bottom navigation.

The DEV destination is now part of the primary bottom navigation, so it is a full-size touch target rather than a small header action. The same navigation remains available on portrait and landscape layouts and can be horizontally constrained by the device's logical width.

After installing the new APK, force-stop the old process and launch again. If the old screen persists, uninstall the previous debug APK before installing the new artifact.
