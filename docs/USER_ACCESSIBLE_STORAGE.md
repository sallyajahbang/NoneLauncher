# User-accessible launcher storage

Sally Launcher now stores user/game data under Android's app-specific external files directory:

`/storage/emulated/0/Android/data/com.sally.launcher/files/`

Minecraft versions, libraries, assets, game/options files, engine payloads, Minecraft logs, and diagnostics are rooted there. The app migrates missing legacy files from its former internal `files/minecraft` directory on first access, without overwriting files already present at the new destination.

The installed executable Java runtimes deliberately remain at internal app storage (`/data/user/0/com.sally.launcher/files/minecraft/runtimes`). Android external storage is commonly mounted `noexec`, so moving `bin/java` and the JVM runtime there can break JLI or native library loading. Runtime archives are not treated as user game data.

No broad storage permission is required for the app-specific external directory. Android versions/file managers may still restrict browsing `Android/data`; connecting the phone to a computer over USB is often the most reliable way to copy files out.
