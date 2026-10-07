package com.example.dronepilottracking2026

import com.example.dronepilottracking2026.core.bluetooth.BleHeartRateParser
import com.example.dronepilottracking2026.data.model.DMR_CYCLE_MS
import com.example.dronepilottracking2026.data.model.DMR_SLOT_SPACING_MS
import com.example.dronepilottracking2026.data.model.MqttConfig
import com.example.dronepilottracking2026.data.model.isDmrSlotWindow
import com.example.dronepilottracking2026.data.model.nextDmrSequenceValue
import com.example.dronepilottracking2026.data.model.validateProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreBehaviorTest {
    @Test
    fun heartRateParserReadsEightBitAndContactFlags() {
        val parsed = BleHeartRateParser.parse(byteArrayOf(0x06, 72))
        assertNotNull(parsed)
        assertEquals(72, parsed?.bpm)
        assertTrue(parsed?.sensorContactSupported == true)
        assertTrue(parsed?.sensorContactDetected == true)
    }

    @Test
    fun heartRateParserReadsUnsignedSixteenBitValueAndRejectsTruncatedData() {
        assertEquals(300, BleHeartRateParser.parse(byteArrayOf(0x01, 0x2C, 0x01))?.bpm)
        assertEquals(null, BleHeartRateParser.parse(byteArrayOf(0x01, 0x2C)))
    }

    @Test
    fun mqttRequiresCompleteHostCredentialsPortAndTopicsAndDefaultsToTls() {
        assertTrue(MqttConfig().useTls)
        val config = MqttConfig(
            host = "broker.example.com", tcpPort = 8883, username = "pilot", password = "secret",
            serialNumber = "unit-01", personelDataTopic = "personel/data", personelSosTopic = "personel/sos"
        )
        assertTrue(config.isComplete)
        assertFalse(config.copy(tcpPort = 0).isComplete)
        assertFalse(config.copy(personelSosTopic = " ").isComplete)
    }

    @Test
    fun profileValidationRejectsIdSpacesButAllowsThemInNameAndNrp() {
        assertTrue(validateProfile("001", "Pilot", "12345").isValid)
        assertTrue(validateProfile("A01", "Pilot 2", "12A 45").isValid)
        assertFalse(validateProfile("A 01", "Pilot", "12345").isValid)
        assertFalse(validateProfile("A01 ", "Pilot", "12345").isValid)
        assertFalse(validateProfile("ID!", "Pilot", "12A45").isValid)
        assertFalse(validateProfile("001", "Pilot".repeat(30), "12A45").isValid)
    }

    @Test
    fun dmrCycleUsesNineSecondsAndOnePointFiveSecondSlots() {
        assertEquals(9_000L, DMR_CYCLE_MS)
        assertEquals(1_500L, DMR_SLOT_SPACING_MS)
        assertTrue(isDmrSlotWindow(now = 0L, slot = 1))
        assertFalse(isDmrSlotWindow(now = 1_500L, slot = 1))
        assertTrue(isDmrSlotWindow(now = 1_500L, slot = 2))
    }

    @Test
    fun dmrSequenceWrapsToZeroAfter299() {
        assertEquals(1L, nextDmrSequenceValue(0L))
        assertEquals(299L, nextDmrSequenceValue(298L))
        assertEquals(0L, nextDmrSequenceValue(299L))
    }
}
