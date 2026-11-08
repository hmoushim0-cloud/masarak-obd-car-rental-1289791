package sa.factory.masarakobdcarrenta

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sa.factory.masarakobdcarrenta.data.repository.MasarakObdRepositoryImpl
import sa.factory.masarakobdcarrenta.engine.*
import sa.factory.masarakobdcarrenta.hardware.HardwareSensorDriver
import sa.factory.masarakobdcarrenta.model.MasarakObdItem

class MasarakObdRegressionTest {

    private lateinit var obdEngine: ObdCanBusTelematicsProtocolEngine
    private lateinit var keyFobEngine: BleDigitalKeyFobProtocolEngine
    private lateinit var sensorDriver: HardwareSensorDriver
    private lateinit var repository: MasarakObdRepositoryImpl

    @Before
    fun setUp() {
        obdEngine = ObdCanBusTelematicsProtocolEngine()
        keyFobEngine = BleDigitalKeyFobProtocolEngine()
        sensorDriver = HardwareSensorDriver(obdEngine, keyFobEngine)
        repository = MasarakObdRepositoryImpl(sensorDriver)
    }

    @Test
    fun testCheckoutCalculationAndTax() = runBlocking {
        val item1 = MasarakObdItem("t1", "تأجير سيدان فارهة يومي", "الوصف", 450.0, "معتمد", "عام")
        val item2 = MasarakObdItem("t2", "تأمين شامل بدون تحمّل", "الوصف", 150.0, "معتمد", "عام")

        repository.addToCart(item1)
        repository.addToCart(item2)

        val order = repository.checkout("Mada Contactless EMV")

        assertEquals(600.0, order.subtotalSar, 0.01)
        assertEquals(90.0, order.vatSar, 0.01)
        assertEquals(690.0, order.totalSar, 0.01)
        assertEquals(0, repository.activeCart.value.size)
    }

    @Test
    fun testObdCoolantTemperatureDecoding() {
        // Formula: A - 40
        assertEquals(90, obdEngine.decodeCoolantTemp(130)) // 130 - 40 = 90 C
        assertEquals(0, obdEngine.decodeCoolantTemp(40))   // 40 - 40 = 0 C
        assertEquals(-40, obdEngine.decodeCoolantTemp(0))  // 0 - 40 = -40 C
    }

    @Test
    fun testObdEngineRpmDecoding() {
        // Formula: (256 * A + B) / 4
        // For A = 0x1F (31), B = 0x40 (64) => (31 * 256 + 64) / 4 = (7936 + 64) / 4 = 8000 / 4 = 2000 RPM
        val rpm = obdEngine.decodeEngineRpm(0x1F, 0x40)
        assertEquals(2000, rpm)

        // For idle: A = 0x0C (12), B = 0x80 (128) => (3072 + 128) / 4 = 3200 / 4 = 800 RPM
        val idleRpm = obdEngine.decodeEngineRpm(0x0C, 0x80)
        assertEquals(800, idleRpm)
    }

    @Test
    fun testObdVehicleSpeedDecoding() {
        // Formula: A
        assertEquals(0, obdEngine.decodeVehicleSpeed(0))
        assertEquals(120, obdEngine.decodeVehicleSpeed(120))
        assertEquals(140, obdEngine.decodeVehicleSpeed(140))
    }

    @Test
    fun testObdFuelLevelPercentage() {
        // Formula: 100 * A / 255
        assertEquals(100.0, obdEngine.decodeFuelLevel(255), 0.1)
        assertEquals(0.0, obdEngine.decodeFuelLevel(0), 0.1)
        assertEquals(50.2, obdEngine.decodeFuelLevel(128), 0.5)
    }

    @Test
    fun testObdDiagnosticTroubleCodeParsing() {
        // Powertrain: P0100 (Mass Air Flow Sensor Circuit)
        // System 0 => 'P', Digit1 = 0, Digit2 = 1, Digit3 = 0, Digit4 = 0
        // ByteA = (0 shl 6) | (0 shl 4) | 1 = 0x01, ByteB = 0x00
        val dtcP = obdEngine.decodeDtcCode(0x01, 0x00)
        assertEquals("P0100", dtcP)

        // Chassis: C0035 (Left Front Wheel Speed Sensor)
        // System 1 => 'C' (1 shl 6 = 0x40), Digit1 = 0, Digit2 = 0, Digit3 = 3, Digit4 = 5
        // ByteA = 0x40, ByteB = 0x35
        val dtcC = obdEngine.decodeDtcCode(0x40, 0x35)
        assertEquals("C0035", dtcC)

        obdEngine.addDiagnosticTroubleCode(dtcP)
        obdEngine.addDiagnosticTroubleCode(dtcC)
        assertEquals(2, obdEngine.getActiveDtcs().size)

        obdEngine.clearDiagnosticTroubleCodes()
        assertEquals(0, obdEngine.getActiveDtcs().size)
    }

    @Test
    fun testDriverSafetyScoreAndHarshBraking() {
        assertEquals(100, obdEngine.calculateSafetyScore())

        obdEngine.recordDrivingEvent("HARSH_BRAKE") // -5
        obdEngine.recordDrivingEvent("HARSH_BRAKE") // -5
        obdEngine.recordDrivingEvent("HARSH_ACCEL") // -3

        assertEquals(87, obdEngine.calculateSafetyScore())
    }

    @Test
    fun testBleRssiDistanceEstimation() {
        // At -59 dBm => ratio = 0 => 10^0 = 1.0 meter
        val d1m = keyFobEngine.estimateDistanceMeters(-59)
        assertEquals(1.0, d1m, 0.05)

        // At -65 dBm => ratio = 6 / 20 = 0.3 => 10^0.3 ~= 1.995 meters
        val d2m = keyFobEngine.estimateDistanceMeters(-65)
        assertEquals(2.0, d2m, 0.1)

        // Invalid RSSI
        assertEquals(-1.0, keyFobEngine.estimateDistanceMeters(0), 0.01)
    }

    @Test
    fun testBleAntiRelayLatencyAndPassiveUnlock() {
        // Valid latency (< 30ms)
        assertTrue(keyFobEngine.verifyAntiRelayLatency(15))
        // Relay attack detected (> 30ms)
        assertFalse(keyFobEngine.verifyAntiRelayLatency(45))

        val challenge = "CHALLENGE_2026"
        val secret = "SECRET_RENTAL_KEY_99"
        val validResponse = keyFobEngine.computeChallengeHash(challenge, secret)

        // Authenticate with valid secret and close distance (-55 dBm -> ~0.6m) and low latency (12ms)
        val unlocked = keyFobEngine.authenticateAndUnlock(
            renterId = "RENTER-123",
            vehicleVin = "1HGCR2F83HA000000",
            challenge = challenge,
            expectedResponse = validResponse,
            secretKey = secret,
            rssiDbm = -55,
            rttLatencyMs = 12
        )
        assertTrue(unlocked)
        assertEquals(VehicleLockState.UNLOCKED, keyFobEngine.getState())
        assertNotNull(keyFobEngine.getActiveSession())

        // Remote engine start
        val started = keyFobEngine.startRemoteEngine()
        assertTrue(started)
        assertEquals(VehicleLockState.REMOTE_ENGINE_STARTED, keyFobEngine.getState())
    }

    @Test
    fun testHardwareSensorDriverIntegration() = runBlocking {
        val frame = sensorDriver.observePeripheralStream().first()
        assertNotNull(frame)
        assertEquals("OBD-II CAN-Bus Telematics + BLE Digital Key Fob", frame.peripheralName)
        assertEquals("CONNECTING", frame.connectionState)

        val telemetry = sensorDriver.getVehicleTelemetry()
        assertTrue(telemetry.speedKmh >= 0)
        assertTrue(telemetry.fuelLevelPct > 0.0)

        assertEquals(VehicleLockState.LOCKED, sensorDriver.getDigitalKeyState())
    }
}
