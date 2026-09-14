package net.n2yti.midgley.auto.data.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Obd2PidDecoderTest {

    @Test
    fun testDecodeStandardHexResponse() {
        // 0x50 = 80 in decimal -> 80 / 255.0 * 100.0 = 31.3725%
        val raw = "41 2F 50\r"
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 15.0)

        assertNotNull(telemetry)
        assertEquals(31.37, telemetry!!.fuelLevelPercent, 0.1)
        assertEquals(4.70, telemetry.fuelLevelGallons, 0.1)
        assertEquals(10.30, telemetry.gallonsNeeded, 0.1)
        assertFalse(telemetry.isLowFuel)
    }

    @Test
    fun testDecodeCompactResponseWithoutSpaces() {
        val raw = "412F80"
        // 0x80 = 128 -> 128 / 255.0 * 100.0 = 50.196%
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 20.0)

        assertNotNull(telemetry)
        assertEquals(50.20, telemetry!!.fuelLevelPercent, 0.1)
        assertEquals(10.04, telemetry.fuelLevelGallons, 0.1)
        assertEquals(9.96, telemetry.gallonsNeeded, 0.1)
        assertFalse(telemetry.isLowFuel)
    }

    @Test
    fun testDecodeFullTankBoundary() {
        val raw = "41 2F FF\r\n>"
        // 0xFF = 255 -> 100.0%
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 15.0)

        assertNotNull(telemetry)
        assertEquals(100.0, telemetry!!.fuelLevelPercent, 0.01)
        assertEquals(15.0, telemetry.fuelLevelGallons, 0.01)
        assertEquals(0.0, telemetry.gallonsNeeded, 0.01)
        assertFalse(telemetry.isLowFuel)
    }

    @Test
    fun testDecodeEmptyTankBoundary() {
        val raw = "41 2F 00"
        // 0x00 = 0 -> 0.0%
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 15.0)

        assertNotNull(telemetry)
        assertEquals(0.0, telemetry!!.fuelLevelPercent, 0.01)
        assertEquals(0.0, telemetry.fuelLevelGallons, 0.01)
        assertEquals(15.0, telemetry.gallonsNeeded, 0.01)
        assertTrue(telemetry.isLowFuel)
    }

    @Test
    fun testDecodeLowFuelThresholdTrigger() {
        // 0x1E = 30 -> 30 / 255.0 * 100.0 = 11.76% (< 15.0%)
        val raw = "41 2F 1E"
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 15.0)

        assertNotNull(telemetry)
        assertEquals(11.76, telemetry!!.fuelLevelPercent, 0.1)
        assertTrue(telemetry.isLowFuel)
    }

    @Test
    fun testDecodeNoisyElmResponseWithPrefixesAndPrompts() {
        val raw = "SEARCHING...\r\n41 2F 64\r\n>"
        // 0x64 = 100 -> 100 / 255.0 * 100.0 = 39.21%
        val telemetry = Obd2PidDecoder.decodeFuelLevelResponse(raw, tankCapacityGallons = 18.5)

        assertNotNull(telemetry)
        assertEquals(39.21, telemetry!!.fuelLevelPercent, 0.1)
        assertEquals(7.25, telemetry.fuelLevelGallons, 0.1)
        assertEquals(11.25, telemetry.gallonsNeeded, 0.1)
    }

    @Test
    fun testDecodeInvalidOrNonMatchingResponses() {
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse(null))
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse(""))
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse("NODATA"))
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse("ERROR"))
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse("41 00 11 22 33"))
        assertNull(Obd2PidDecoder.decodeFuelLevelResponse("41 2F ZZ"))
    }

    @Test
    fun testSimulatedTelemetryGeneration() {
        val sim = Obd2PidDecoder.createSimulatedTelemetry(fuelPercent = 25.0, tankCapacityGallons = 16.0)
        assertEquals(25.0, sim.fuelLevelPercent, 0.01)
        assertEquals(4.0, sim.fuelLevelGallons, 0.01)
        assertEquals(12.0, sim.gallonsNeeded, 0.01)
        assertFalse(sim.isLowFuel)
    }
}