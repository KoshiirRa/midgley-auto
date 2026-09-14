package net.n2yti.midgley.auto.data.obd

/**
 * Telemetry snapshot parsed from vehicle OBD2 Mode 01 PID 0x2F (Fuel Tank Input Level).
 */
data class Obd2Telemetry(
    val fuelLevelPercent: Double,
    val fuelLevelGallons: Double,
    val gallonsNeeded: Double,
    val isLowFuel: Boolean,
    val rawResponse: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

/**
 * Deterministic SAE J1979 Mode 01 PID 0x2F (Fuel Tank Input Level) ASCII parser.
 *
 * Formula:
 * Fuel Level % = (HexByte / 2.55) = (HexByte / 255.0) * 100.0
 *
 * Example:
 * Request: 01 2F\r
 * Response: 41 2F 50\r -> 0x50 = 80 -> 80 / 2.55 = 31.37%
 */
object Obd2PidDecoder {

    const val PID_FUEL_LEVEL_REQUEST = "012F\r"
    const val LOW_FUEL_THRESHOLD_PERCENT = 15.0

    /**
     * Decodes an ELM327 / OBD2 ASCII response containing PID 012F fuel tank level.
     *
     * @param rawResponse Raw ASCII text from the BLE characteristic
     * @param tankCapacityGallons Total fuel tank capacity of vehicle in gallons
     * @return [Obd2Telemetry] instance, or null if response does not contain valid 412F data
     */
    fun decodeFuelLevelResponse(
        rawResponse: String?,
        tankCapacityGallons: Double = 15.0
    ): Obd2Telemetry? {
        if (rawResponse.isNullOrBlank()) return null

        // Clean and normalize ASCII output (strip whitespace, prompts, ELM headers)
        val cleaned = rawResponse
            .replace(" ", "")
            .replace("\r", "")
            .replace("\n", "")
            .replace(">", "")
            .replace("SEARCHING...", "")
            .replace("STOPPED", "")
            .replace("OK", "")
            .uppercase()

        // Locate Mode 41 PID 2F response tag
        val tag = "412F"
        val tagIndex = cleaned.indexOf(tag)
        if (tagIndex == -1 || cleaned.length < tagIndex + 6) {
            return null
        }

        val hexByteStr = cleaned.substring(tagIndex + 4, tagIndex + 6)
        val rawByteVal = try {
            hexByteStr.toInt(16)
        } catch (_: NumberFormatException) {
            return null
        }

        val fuelPercent = ((rawByteVal.toDouble() / 255.0) * 100.0).coerceIn(0.0, 100.0)
        val fuelGallons = (fuelPercent / 100.0) * tankCapacityGallons
        val gallonsNeeded = (tankCapacityGallons - fuelGallons).coerceAtLeast(0.0)
        val isLowFuel = fuelPercent < LOW_FUEL_THRESHOLD_PERCENT

        return Obd2Telemetry(
            fuelLevelPercent = fuelPercent,
            fuelLevelGallons = fuelGallons,
            gallonsNeeded = gallonsNeeded,
            isLowFuel = isLowFuel,
            rawResponse = rawResponse.trim()
        )
    }

    /**
     * Synthesizes mock telemetry for testing, demos, or DHU desktop emulator runs.
     */
    fun createSimulatedTelemetry(
        fuelPercent: Double = 35.0,
        tankCapacityGallons: Double = 15.0
    ): Obd2Telemetry {
        val clampedPct = fuelPercent.coerceIn(0.0, 100.0)
        val hexVal = (clampedPct * 2.55).toInt().coerceIn(0, 255)
        val hexStr = "%02X".format(hexVal)
        val fuelGallons = (clampedPct / 100.0) * tankCapacityGallons
        val gallonsNeeded = (tankCapacityGallons - fuelGallons).coerceAtLeast(0.0)
        val isLowFuel = clampedPct < LOW_FUEL_THRESHOLD_PERCENT

        return Obd2Telemetry(
            fuelLevelPercent = clampedPct,
            fuelLevelGallons = fuelGallons,
            gallonsNeeded = gallonsNeeded,
            isLowFuel = isLowFuel,
            rawResponse = "41 2F $hexStr"
        )
    }
}