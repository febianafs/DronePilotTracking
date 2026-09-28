package com.example.dronepilottracking2026.core.bluetooth

object BleHeartRateParser {
    data class Result(
        val bpm: Int,
        val sensorContactSupported: Boolean,
        val sensorContactDetected: Boolean
    )

    fun parse(payload: ByteArray): Result? {
        if (payload.isEmpty()) return null

        val flags = payload[0].toInt() and 0xFF
        val isUint16 = flags and 0x01 != 0
        val contactSupported = flags and 0x04 != 0
        val contactDetected = flags and 0x02 != 0
        val bpm = if (isUint16) {
            if (payload.size < 3) return null
            (payload[1].toInt() and 0xFF) or ((payload[2].toInt() and 0xFF) shl 8)
        } else {
            if (payload.size < 2) return null
            payload[1].toInt() and 0xFF
        }

        return Result(
            bpm = bpm.coerceAtLeast(0),
            sensorContactSupported = contactSupported,
            sensorContactDetected = contactDetected
        )
    }
}
