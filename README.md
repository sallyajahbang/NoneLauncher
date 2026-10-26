# NoneLauncher

NoneLauncher is a new Android Java-game launcher project. It is NOT a renamed or rebundled PojavLauncher.

The reference project was used only to understand the requirements of Android-to-LWJGL input, pointer grabbing, touch deltas, and the runtime boundary. The launcher/application architecture here is new and intentionally small.

## Current stage
- Own launcher UI
- Own GameActivity/GameView
- Own InputRouter
- Own JNI NativeInput boundary
- Multi-touch pointer down/up
- Relative pointer/camera deltas
- Keyboard key/scan/modifier forwarding
- Mouse button/scroll API
- Grab state API

## Next stages
1. Replace GameView with an actual EGL/OpenGL surface.
2. Add an isolated Java runtime manager.
3. Add LWJGL 3 native/runtime loading.
4. Implement a small GLFW compatibility bridge that consumes NativeInput events.
5. Add Minecraft version/profile installation.
6. Add virtual controls on top of the game surface.

Do not copy PojavLauncher source into this project. Implement each layer independently.
