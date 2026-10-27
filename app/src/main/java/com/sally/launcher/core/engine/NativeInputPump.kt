package com.sally.launcher.core.engine

/** Results from one bounded input-queue drain. */
data class NativeInputPumpResult(
    val recordsRead: Int,
    val deliveredEventCount: Int,
    val malformedRecordCount: Int,
    val trailingValueCount: Int,
    val sinkFailureCount: Int,
    val sourceFailureCount: Int,
    val queueDepthAfterDrain: Int,
)

/**
 * Consumes JNI queue batches and delivers typed events to an injected sink, in queue order.
 * Call this from the future engine/render thread; the diagnostics screen currently supplies a
 * monitor sink only. This class intentionally does not pretend to call GLFW by itself.
 *
 * Injectable providers make the queue/pump contract testable on a host JVM without loading JNI.
 */
class NativeInputPump(
    private val pollBatch: (Int) -> FloatArray = NativeEngineBridge::pollEvents,
    private val queueDepth: () -> Int = NativeEngineBridge::queuedEventCount,
) {
    fun drain(
        maxEvents: Int = 64,
        sink: (NativeInputEvent) -> Unit,
    ): NativeInputPumpResult {
        if (maxEvents <= 0) {
            return NativeInputPumpResult(
                recordsRead = 0,
                deliveredEventCount = 0,
                malformedRecordCount = 0,
                trailingValueCount = 0,
                sinkFailureCount = 0,
                sourceFailureCount = 0,
                queueDepthAfterDrain = safeQueueDepth(),
            )
        }

        val boundedMax = maxEvents.coerceAtMost(256)
        var sourceFailureCount = 0
        val batch = try {
            pollBatch(boundedMax)
        } catch (_: Exception) {
            sourceFailureCount++
            FloatArray(0)
        }
        val decoded = NativeInputEventDecoder.decode(batch)
        var delivered = 0
        var sinkFailures = 0
        decoded.events.forEach { event ->
            try {
                sink(event)
                delivered++
            } catch (_: Exception) {
                // Keep the input/render loop alive if an optional consumer fails on one event.
                sinkFailures++
            }
        }

        return NativeInputPumpResult(
            recordsRead = batch.size / NativeInputEventDecoder.VALUES_PER_EVENT,
            deliveredEventCount = delivered,
            malformedRecordCount = decoded.malformedRecordCount,
            trailingValueCount = decoded.trailingValueCount,
            sinkFailureCount = sinkFailures,
            sourceFailureCount = sourceFailureCount,
            queueDepthAfterDrain = safeQueueDepth(),
        )
    }

    private fun safeQueueDepth(): Int = try {
        queueDepth().coerceAtLeast(0)
    } catch (_: Exception) {
        0
    }
}
