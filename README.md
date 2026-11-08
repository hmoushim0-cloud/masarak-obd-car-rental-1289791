# تطبيق مسارك لتأجير السيارات الذكية (masarak-obd-car-rental-1289791)
> منظومة car_rental المتكاملة مع ربط OBD-II CAN-Bus Telematics + BLE Digital Key Fob

- **Project ID**: #1289791
- **Commercial Archetype**: car_rental (Fleet Management, Vehicle Telematics, Keyless Digital Car Access)
- **Bound Hardware Peripheral**: OBD-II CAN-Bus Telematics + BLE Digital Key Fob
- **Package Name**: sa.factory.masarakobdcarrenta
- **Zero-Emoji Invariant**: Strictly Enforced (100% Vector Icons)

---

## [Sidekick Autonomous Update] Production Protocol Engines & Verification

### 1. ObdCanBusTelematicsProtocolEngine
- **Standard OBD-II CAN-Bus Telematics**: Full ISO 15765-4 CAN frame decoder (functional request 0x7DF, ECU response 0x7E8) for Mode 01 diagnostic parameters including Engine Coolant Temperature (PID 0x05), Engine RPM (PID 0x0C), Vehicle Speed (PID 0x0D), Fuel Level Percentage (PID 0x2F), and Odometer Distance (PID 0x31).
- **Diagnostic Trouble Code (DTC) Processing**: Algorithmic 2-byte hexadecimal fault code parser converting raw CAN words into standardized SAE DTCs (Powertrain P, Chassis C, Body B, Network U).
- **Driver Behavior Telemetry & Scoring**: Real-time event detection monitoring harsh deceleration/braking events, excessive engine revving, and dynamic safety score calculations (0-100 index).

### 2. BleDigitalKeyFobProtocolEngine
- **Car Connectivity Consortium (CCC) Digital Key Architecture**: Bluetooth Low Energy (BLE) proximity detection with log-distance path loss RSSI distance estimation (-59 dBm calibrated reference at 1.0 meter).
- **Cryptographic Challenge-Response & Anti-Relay Protection**: SHA-256 HMAC token authentication with strict round-trip transit latency verification (< 30ms threshold) to prevent relay and signal-amplification attacks.
- **Keyless Vehicle Access State Machine**: Multi-state vehicle security manager supporting LOCKED, PROXIMITY_DETECTED, UNLOCKED, IMMOBILIZER_DISARMED, and REMOTE_ENGINE_STARTED.

### 3. HardwareSensorDriver Integration
- High-frequency dual-engine telemetry loop streaming live CAN-Bus parameters, vehicle safety score, BLE digital key connection status, and sequence counters into ViewModel StateFlow.

### 4. Unit Regression Suite
- 10 comprehensive unit regression tests in MasarakObdRegressionTest.kt covering checkout calculations, 15% VAT accuracy, OBD coolant/RPM/speed/fuel math, DTC code parsing, driver safety scoring, BLE RSSI distance formulas, anti-relay latency thresholds, and hardware driver flows.
