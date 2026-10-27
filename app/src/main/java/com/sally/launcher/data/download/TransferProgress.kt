package com.sally.launcher.data.download

/**
 * Byte-based progress for one install run.
 *
 * Every file reports how many bytes it currently holds on disk, and the overall
 * done/total pair is the sum over all files. The bar therefore moves with data,
 * never with the number of files finished.
 *
 * Partial files that are resumed or restarted are handled by signed deltas: a
 * restart can move the done count backwards, which keeps the bar honest.
 */
internal class TransferProgress(
    expectedBytes: Long,
    private val onUpdate: (done: Long, total: Long) -> Unit,
) {
    private val lock = Any()
    private val bytesPerFile = HashMap<String, Long>()
    private val total = expectedBytes.coerceAtLeast(0L)
    private var done = 0L
    private var lastEmittedDone = -1L
    private var lastEmittedAt = 0L

    /** Records how many bytes [key] currently holds on disk. */
    fun update(key: String, bytesOnDisk: Long) {
        synchronized(lock) {
            val current = bytesOnDisk.coerceAtLeast(0L)
            val previous = bytesPerFile[key] ?: 0L
            bytesPerFile[key] = current
            done += current - previous
            emit(force = false)
        }
    }

    /** Marks [key] as complete with its exact final size, so the total lines up at the end. */
    fun finish(key: String, finalBytes: Long) {
        synchronized(lock) {
            val current = finalBytes.coerceAtLeast(0L)
            val previous = bytesPerFile[key] ?: 0L
            bytesPerFile[key] = current
            done += current - previous
            emit(force = true)
        }
    }

    // Throttled so 128 KB network reads do not flood the UI thread.
    private fun emit(force: Boolean) {
        val now = System.currentTimeMillis()
        val bigEnoughStep = done - lastEmittedDone >= MIN_STEP_BYTES
        val oldEnough = now - lastEmittedAt >= MIN_INTERVAL_MS
        if (!force && !(bigEnoughStep || oldEnough)) return
        lastEmittedDone = done
        lastEmittedAt = now
        onUpdate(done.coerceAtLeast(0L), total)
    }

    private companion object {
        const val MIN_STEP_BYTES = 256L * 1024L
        const val MIN_INTERVAL_MS = 120L
    }
}
