package com.sally.nonelauncher.core.launch

/**
 * Something that can actually run a [LaunchCommand].
 *
 * - [ProcessGameBackend] starts the command as a normal OS process. It works
 *   on desktop JVMs and is what the tests and CLI use.
 * - On Android the backend must go through the PojavLauncher-style native
 *   bridge (JNI + GL/Vulkan renderer). See `app/.../AndroidGameBackend.kt`.
 */
interface GameBackend {
    /** Starts the command and returns the process-like handle used to wait on it. */
    fun start(command: LaunchCommand): RunningGame
}

interface RunningGame {
    /** Blocks until the game exits and returns its exit code. */
    fun waitFor(): Int
    fun destroy()
}

class ProcessGameBackend : GameBackend {
    override fun start(command: LaunchCommand): RunningGame {
        val process = ProcessBuilder(listOf(command.executable) + command.args)
            .directory(command.workingDir)
            .redirectErrorStream(true)
            .inheritIO()
            .start()
        return object : RunningGame {
            override fun waitFor(): Int = process.waitFor()
            override fun destroy() = process.destroy()
        }
    }
}
