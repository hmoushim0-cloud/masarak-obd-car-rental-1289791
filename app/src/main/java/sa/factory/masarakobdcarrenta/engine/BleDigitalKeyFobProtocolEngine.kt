package sa.factory.masarakobdcarrenta.engine

import java.security.MessageDigest
import kotlin.math.pow

enum class VehicleLockState {
    LOCKED,
    PROXIMITY_DETECTED,
    UNLOCKED,
    IMMOBILIZER_DISARMED,
    REMOTE_ENGINE_STARTED,
    TRUNK_OPEN
}

data class DigitalKeySession(
    val sessionId: String,
    val renterId: String,
    val vehicleVin: String,
    val state: VehicleLockState,
    val estimatedDistanceMeters: Double,
    val isAntiRelayVerified: Boolean,
    val leaseValidUntilMillis: Long
)

class BleDigitalKeyFobProtocolEngine {

    companion object {
        const val TX_POWER_AT_1M = -59 // dBm standard beacon calibration
        const val PATH_LOSS_EXPONENT = 2.0 // Free-space indoor/car environment
        const val MAX_RELAY_LATENCY_MS = 30L
    }

    private var currentState: VehicleLockState = VehicleLockState.LOCKED
    private var activeSession: DigitalKeySession? = null

    fun estimateDistanceMeters(rssiDbm: Int): Double {
        if (rssiDbm == 0) return -1.0
        val ratio = (TX_POWER_AT_1M - rssiDbm) / (10.0 * PATH_LOSS_EXPONENT)
        val distance = 10.0.pow(ratio)
        return Math.round(distance * 100.0) / 100.0
    }

    fun verifyAntiRelayLatency(roundTripLatencyMs: Long): Boolean {
        // Anti-relay protection: reject any transit latency exceeding 30ms to prevent signal amplification
        return roundTripLatencyMs in 1..MAX_RELAY_LATENCY_MS
    }

    fun computeChallengeHash(challenge: String, secretKey: String): String {
        val input = "$challenge:$secretKey"
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun authenticateAndUnlock(
        renterId: String,
        vehicleVin: String,
        challenge: String,
        expectedResponse: String,
        secretKey: String,
        rssiDbm: Int,
        rttLatencyMs: Long
    ): Boolean {
        val computed = computeChallengeHash(challenge, secretKey)
        if (computed != expectedResponse) return false
        if (!verifyAntiRelayLatency(rttLatencyMs)) return false

        val distance = estimateDistanceMeters(rssiDbm)
        // Passive unlock allowed only within 1.5 meters
        if (distance > 1.5) {
            currentState = VehicleLockState.PROXIMITY_DETECTED
            return false
        }

        currentState = VehicleLockState.UNLOCKED
        activeSession = DigitalKeySession(
            sessionId = "KEY-SESS-${System.currentTimeMillis() % 100000}",
            renterId = renterId,
            vehicleVin = vehicleVin,
            state = currentState,
            estimatedDistanceMeters = distance,
            isAntiRelayVerified = true,
            leaseValidUntilMillis = System.currentTimeMillis() + (86400 * 1000L) // 24 hours
        )
        return true
    }

    fun lockDoors() {
        currentState = VehicleLockState.LOCKED
        activeSession = activeSession?.copy(state = VehicleLockState.LOCKED)
    }

    fun startRemoteEngine(): Boolean {
        if (currentState == VehicleLockState.UNLOCKED || currentState == VehicleLockState.IMMOBILIZER_DISARMED) {
            currentState = VehicleLockState.REMOTE_ENGINE_STARTED
            activeSession = activeSession?.copy(state = VehicleLockState.REMOTE_ENGINE_STARTED)
            return true
        }
        return false
    }

    fun getState(): VehicleLockState = currentState

    fun getActiveSession(): DigitalKeySession? = activeSession
}
