package com.sally.launcher.core.engine

/**
 * Typed representation of one six-field event crossing the JNI queue.
 * This is deliberately independent of Android and GLFW classes so it can be
 * tested on the host JVM and can later feed a real GLFW/LWJGL adapter.
 */
data class NativeInputEvent(
    val type: Int,
    val code: Int,
    val action: Int,
    val x: Float,
    val y: Float,
    val value: Float,
) {
    fun toDebugString(): String =
        "type=$type, code=$code, action=$action, x=$x, y=$y, value=$value"
}

data class NativeInputDecodeResult(
    val events: List<NativeInputEvent>,
    /** Invalid complete records plus one malformed record if the batch has a trailing partial record. */
    val malformedRecordCount: Int,
    /** Number of floats that could not form a complete six-value record. */
    val trailingValueCount: Int,
)

/** Decodes the native bridge's stable [type, code, action, x, y, value] record format. */
object NativeInputEventDecoder {
    const val VALUES_PER_EVENT = 6

    fun decode(batch: FloatArray): NativeInputDecodeResult {
        if (batch.isEmpty()) return NativeInputDecodeResult(emptyList(), 0, 0)

        val completeRecords = batch.size / VALUES_PER_EVENT
        val trailingValues = batch.size % VALUES_PER_EVENT
        val events = ArrayList<NativeInputEvent>(completeRecords)
        var malformedRecords = 0

        for (recordIndex in 0 until completeRecords) {
            val offset = recordIndex * VALUES_PER_EVENT
            val type = exactInt(batch[offset])
            val code = exactInt(batch[offset + 1])
            val action = exactInt(batch[offset + 2])
            val x = batch[offset + 3]
            val y = batch[offset + 4]
            val value = batch[offset + 5]

            if (type == null || code == null || action == null ||
                !x.isFinite() || !y.isFinite() || !value.isFinite()) {
                malformedRecords++
                continue
            }
            events += NativeInputEvent(type, code, action, x, y, value)
        }

        if (trailingValues > 0) malformedRecords++
        return NativeInputDecodeResult(events, malformedRecords, trailingValues)
    }

    private fun exactInt(value: Float): Int? {
        if (!value.isFinite() || value.toDouble() < Int.MIN_VALUE.toDouble() || value.toDouble() > Int.MAX_VALUE.toDouble()) {
            return null
        }
        val integer = value.toInt()
        return integer.takeIf { it.toFloat() == value }
    }
}
