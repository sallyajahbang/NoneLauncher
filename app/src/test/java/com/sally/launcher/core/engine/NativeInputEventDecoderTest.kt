package com.sally.launcher.core.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInputEventDecoderTest {
    @Test
    fun decodesSixValueRecordsInOrder() {
        val result = NativeInputEventDecoder.decode(floatArrayOf(
            2f, 65f, 1f, 0f, 0f, 0f,
            3f, 0f, 3f, 1.25f, -2.5f, 0f,
        ))

        assertEquals(2, result.events.size)
        assertEquals(NativeInputEvent(2, 65, 1, 0f, 0f, 0f), result.events[0])
        assertEquals(NativeInputEvent(3, 0, 3, 1.25f, -2.5f, 0f), result.events[1])
        assertEquals(0, result.malformedRecordCount)
        assertEquals(0, result.trailingValueCount)
    }

    @Test
    fun skipsMalformedRecordsWithoutLosingFollowingRecords() {
        val result = NativeInputEventDecoder.decode(floatArrayOf(
            2f, 65.5f, 1f, 0f, 0f, 0f, // non-integer code
            2f, 66f, 1f, 0f, Float.NaN, 0f, // non-finite coordinate
            2f, 67f, 0f, 0f, 0f, 0f,
        ))

        assertEquals(1, result.events.size)
        assertEquals(67, result.events.single().code)
        assertEquals(2, result.malformedRecordCount)
    }

    @Test
    fun reportsIncompleteTailAsMalformed() {
        val result = NativeInputEventDecoder.decode(floatArrayOf(2f, 65f, 1f, 0f, 0f, 0f, 99f))
        assertEquals(1, result.events.size)
        assertEquals(1, result.malformedRecordCount)
        assertEquals(1, result.trailingValueCount)
    }

    @Test
    fun emptyBatchIsSafe() {
        val result = NativeInputEventDecoder.decode(FloatArray(0))
        assertTrue(result.events.isEmpty())
        assertEquals(0, result.malformedRecordCount)
        assertEquals(0, result.trailingValueCount)
    }
}
