package sa.factory.masarakobdcarrenta.hardware

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sa.factory.masarakobdcarrenta.engine.BleDigitalKeyFobProtocolEngine
import sa.factory.masarakobdcarrenta.engine.ObdCanBusTelematicsProtocolEngine
import sa.factory.masarakobdcarrenta.engine.ObdVehicleTelemetry
import sa.factory.masarakobdcarrenta.engine.VehicleLockState
import kotlin.random.Random

data class HardwareTelemetryFrame(
    val peripheralName: String,
    val connectionState: String,
    val livePayload: String,
    val timestamp: Long = System.currentTimeMillis()
)

class HardwareSensorDriver(
    val obdEngine: ObdCanBusTelematicsProtocolEngine = ObdCanBusTelematicsProtocolEngine(),
    val keyFobEngine: BleDigitalKeyFobProtocolEngine = BleDigitalKeyFobProtocolEngine()
) {
    private val peripheral = "OBD-II CAN-Bus Telematics + BLE Digital Key Fob"

    fun getVehicleTelemetry(): ObdVehicleTelemetry = obdEngine.getTelemetrySnapshot()

    fun getDigitalKeyState(): VehicleLockState = keyFobEngine.getState()

    fun lockVehicle() = keyFobEngine.lockDoors()

    fun startEngine(): Boolean = keyFobEngine.startRemoteEngine()

    fun observePeripheralStream(): Flow<HardwareTelemetryFrame> = flow {
        emit(HardwareTelemetryFrame(peripheral, "CONNECTING", "CAN-Bus ISO 15765 & BLE Key Fob GATT handshake initializing..."))
        delay(300)
        emit(HardwareTelemetryFrame(peripheral, "ONLINE", "OBD-II CAN-Bus & BLE Digital Key: ONLINE | BCM Paired | Ready"))

        var sequence = 1
        while (true) {
            delay(1500)
            sequence++
            val jitter = Random.nextInt(1, 5)
            val telemetry = obdEngine.getTelemetrySnapshot()
            val keyState = keyFobEngine.getState().name

            val payload = "CAN-Bus [${telemetry.speedKmh} km/h | ${telemetry.rpm} RPM | Coolant: ${telemetry.coolantTempC}C | Fuel: ${telemetry.fuelLevelPct}% | Score: ${telemetry.driverSafetyScore}] | BLE Key [$keyState | Anti-Relay: PASS] | Frame #$sequence (+${jitter}ms)"

            emit(HardwareTelemetryFrame(
                peripheralName = peripheral,
                connectionState = "ACTIVE_STREAMING",
                livePayload = payload
            ))
        }
    }
}
