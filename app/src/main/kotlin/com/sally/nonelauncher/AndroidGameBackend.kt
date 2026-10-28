package com.sally.nonelauncher

import com.sally.nonelauncher.core.launch.GameBackend
import com.sally.nonelauncher.core.launch.LaunchCommand
import com.sally.nonelauncher.core.launch.RunningGame

/**
 * Android backend. Not implemented yet.
 *
 * Running Minecraft on Android needs a JVM plus a native renderer
 * (GL4ES / Zink / Vulkan) and a JNI entry point that starts the JVM inside the
 * app process. The reference implementation is PojavLauncher's bridge, which
 * ZalithLauncher2 ships (GPL-3.0). Port that bridge here, keeping its license
 * and attribution:
 *
 *   ZalithLauncher/src/main/jni/jre_launcher.c
 *   ZalithLauncher/src/main/jni/egl_bridge.c
 *   ZalithLauncher/src/main/java/com/movtery/zalithlauncher/bridge/
 *
 * Until then, starting a game throws a clear error instead of silently failing.
 */
class AndroidGameBackend : GameBackend {
    override fun start(command: LaunchCommand): RunningGame {
        throw UnsupportedOperationException(
            "Native JVM bridge is not implemented yet. " +
                "Command prepared for ${command.executable} with ${command.args.size} args.",
        )
    }
}
