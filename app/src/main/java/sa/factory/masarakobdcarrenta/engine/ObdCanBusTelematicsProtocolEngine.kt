package sa.factory.masarakobdcarrenta.engine

enum class CanBusFrameType {
    STANDARD_11BIT,
    EXTENDED_29BIT
}

data class ObdVehicleTelemetry(
    val speedKmh: Int,
    val rpm: Int,
    val coolantTempC: Int,
    val fuelLevelPct: Double,
    val runTimeSeconds: Int,
    val odometerKm: Double,
    val activeDtcs: List<String>,
    val driverSafetyScore: Int,
    val harshBrakingEvents: Int,
    val timestamp: Long = System.currentTimeMillis()
)

class ObdCanBusTelematicsProtocolEngine {

    companion object {
        const val CAN_ID_FUNCTIONAL_REQUEST = 0x7DF
        const val CAN_ID_ECU_RESPONSE = 0x7E8

        const val PID_COOLANT_TEMP = 0x05
        const val PID_ENGINE_RPM = 0x0C
        const val PID_VEHICLE_SPEED = 0x0D
        const val PID_RUN_TIME = 0x1F
        const val PID_FUEL_LEVEL = 0x2F
        const val PID_ODOMETER = 0x31
    }

    private var currentSpeedKmh: Int = 85
    private var currentRpm: Int = 2200
    private var currentCoolantC: Int = 90
    private var currentFuelPct: Double = 78.5
    private var currentOdometerKm: Double = 42150.0
    private var harshBrakingCount: Int = 0
    private var harshAccelCount: Int = 0
    private val activeDtcs = mutableListOf<String>()

    fun decodeCoolantTemp(byteA: Int): Int {
        return (byteA and 0xFF) - 40
    }

    fun decodeEngineRpm(byteA: Int, byteB: Int): Int {
        val raw = ((byteA and 0xFF) shl 8) or (byteB and 0xFF)
        return raw / 4
    }

    fun decodeVehicleSpeed(byteA: Int): Int {
        return byteA and 0xFF
    }

    fun decodeFuelLevel(byteA: Int): Double {
        val pct = ((byteA and 0xFF) * 100.0) / 255.0
        return Math.round(pct * 10.0) / 10.0
    }

    fun decodeRunTimeSeconds(byteA: Int, byteB: Int): Int {
        return ((byteA and 0xFF) shl 8) or (byteB and 0xFF)
    }

    fun decodeDtcCode(byteA: Int, byteB: Int): String {
        val systemPrefix = when ((byteA shr 6) and 0x03) {
            0 -> "P" // Powertrain
            1 -> "C" // Chassis
            2 -> "B" // Body
            3 -> "U" // Network
            else -> "P"
        }
        val digit1 = (byteA shr 4) and 0x03
        val digit2 = byteA and 0x0F
        val digit3 = (byteB shr 4) and 0x0F
        val digit4 = byteB and 0x0F
        return "%s%d%X%X%X".format(systemPrefix, digit1, digit2, digit3, digit4)
    }

    fun parseCanPayload(mode: Int, pid: Int, dataBytes: ByteArray): Boolean {
        if (mode != 0x01 && mode != 0x41) return false
        if (dataBytes.isEmpty()) return false

        when (pid) {
            PID_COOLANT_TEMP -> {
                currentCoolantC = decodeCoolantTemp(dataBytes[0].toInt())
            }
            PID_ENGINE_RPM -> {
                if (dataBytes.size >= 2) {
                    currentRpm = decodeEngineRpm(dataBytes[0].toInt(), dataBytes[1].toInt())
                }
            }
            PID_VEHICLE_SPEED -> {
                currentSpeedKmh = decodeVehicleSpeed(dataBytes[0].toInt())
            }
            PID_FUEL_LEVEL -> {
                currentFuelPct = decodeFuelLevel(dataBytes[0].toInt())
            }
            PID_RUN_TIME -> {
                if (dataBytes.size >= 2) {
                    // Update runtime
                }
            }
        }
        return true
    }

    fun recordDrivingEvent(event: String) {
        when (event) {
            "HARSH_BRAKE" -> harshBrakingCount++
            "HARSH_ACCEL" -> harshAccelCount++
        }
    }

    fun calculateSafetyScore(): Int {
        val deductions = (harshBrakingCount * 5) + (harshAccelCount * 3)
        return (100 - deductions).coerceIn(40, 100)
    }

    fun addDiagnosticTroubleCode(dtc: String) {
        if (!activeDtcs.contains(dtc)) {
            activeDtcs.add(dtc)
        }
    }

    fun clearDiagnosticTroubleCodes() {
        activeDtcs.clear()
    }

    fun getActiveDtcs(): List<String> = activeDtcs.toList()

    fun getTelemetrySnapshot(): ObdVehicleTelemetry {
        return ObdVehicleTelemetry(
            speedKmh = currentSpeedKmh,
            rpm = currentRpm,
            coolantTempC = currentCoolantC,
            fuelLevelPct = currentFuelPct,
            runTimeSeconds = 3600,
            odometerKm = currentOdometerKm,
            activeDtcs = activeDtcs.toList(),
            driverSafetyScore = calculateSafetyScore(),
            harshBrakingEvents = harshBrakingCount
        )
    }
}
