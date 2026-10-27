package com.sally.launcher.core.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeInputPumpTest {
    @Test
    fun drainDeliversEventsInOrderAndReportsQueueDepth() {
        val pump = NativeInputPump(
            pollBatch = { limit ->
                assertEquals(2, limit)
                floatArrayOf(2f, 65f, 1f, 0f, 0f, 0f, 2f, 65f, 0f, 0f, 0f, 0f)
            },
            queueDepth = { 7 },
        )
        val received = mutableListOf<NativeInputEvent>()

        val result = pump.drain(2) { received.add(it) }

        assertEquals(2, received.size)
        assertEquals(1, received[0].action)
        assertEquals(0, received[1].action)
        assertEquals(2, result.recordsRead)
        assertEquals(2, result.deliveredEventCount)
        assertEquals(0, result.malformedRecordCount)
        assertEquals(7, result.queueDepthAfterDrain)
    }

    @Test
    fun boundsBatchSizeAndContinuesAfterSinkFailure() {
        var observedLimit = 0
        val pump = NativeInputPump(
            pollBatch = { limit ->
                observedLimit = limit
                floatArrayOf(2f, 65f, 1f, 0f, 0f, 0f, 2f, 66f, 1f, 0f, 0f, 0f)
            },
            queueDepth = { 0 },
        )
        var calls = 0
        val result = pump.drain(900) {
            calls++
            if (calls == 1) error("simulated consumer failure")
        }

        assertEquals(256, observedLimit)
        assertEquals(2, calls)
        assertEquals(1, result.deliveredEventCount)
        assertEquals(1, result.sinkFailureCount)
    }

    @Test
    fun reportsSourceFailureAndInvalidLimitWithoutDraining() {
        var pollCalls = 0
        val pump = NativeInputPump(
            pollBatch = { pollCalls++; error("native source failed") },
            queueDepth = { 3 },
        )
        val failed = pump.drain(4) {}
        assertEquals(1, failed.sourceFailureCount)
        assertEquals(3, failed.queueDepthAfterDrain)

        val invalid = pump.drain(0) {}
        assertEquals(1, pollCalls) // The invalid limit must not invoke the source.
        assertEquals(0, invalid.recordsRead)
        assertEquals(0, invalid.sourceFailureCount)
    }
}
