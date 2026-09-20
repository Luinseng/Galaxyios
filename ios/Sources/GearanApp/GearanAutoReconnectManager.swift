import Foundation
import CoreBluetooth
import GearanCore

/// Priority feature: PAIR ONCE → TRUST → AUTO RECONNECT.
///
/// Owns a dedicated CBCentralManager (restore ID `com.gearan.ios.reconnect`)
/// so reconnection survives app kill, locked phone and reboots via
/// `bluetooth-central` background mode. Never calls Forget internally:
/// a plain disconnect only moves to `.reconnecting` with progressive backoff
/// (1 s → 30 s max). Trust is keyed by GearanDeviceID, never by MAC.
public final class GearanAutoReconnectManager: NSObject, ObservableObject {
    public enum State: String {
        case idle
        case connected
        case reconnecting
        case watchUnavailable
        case bluetoothDisabled
        case serviceUnavailable
        case authRequired
    }

    @Published public var state: State = .idle
    @Published public var attempts = 0
    @Published public var lastError: String?

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var backoffTask: Task<Void, Never>?
    private var trustedID: String?
    private var peripheralUUID: UUID?
    private let store = TrustedDeviceStore()

    public override init() {
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [CBCentralManagerOptionRestoreIdentifierKey: "com.gearan.ios.reconnect"]
        )
    }

    /// Call on app start and after pairing: restores trust, then reconnects.
    public func restoreAndReconnect() {
        guard let watch = store.load() else {
            state = .idle
            return
        }
        trustedID = watch.gearDeviceId
        // CBPeripheral identifier (UUID, not MAC) persists across launches on
        // the same iPhone; cached in UserDefaults keyed by GearanDeviceID.
        if let s = UserDefaults.standard.string(forKey: keyFor(watch.gearDeviceId)),
           let uuid = UUID(uuidString: s) {
            peripheralUUID = uuid
        }
        attemptReconnect()
    }

    private func keyFor(_ gearDeviceId: String) -> String {
        "gearan.peripheralUUID." + gearDeviceId
    }

    /// Remember the live peripheral UUID right after pairing (identifier is a
    /// UUID, not a MAC). Persisted inside the trusted record slot.
    public func setPairedPeripheral(_ id: UUID, gearDeviceId: String) {
        peripheralUUID = id
        trustedID = gearDeviceId
        UserDefaults.standard.set(id.uuidString, forKey: keyFor(gearDeviceId))
    }

    public func forceReconnect() {
        backoffTask?.cancel()
        attempts = 0
        attemptReconnect()
    }

    public func handlePairingFailedAuth() {
        state = .authRequired
        GearanLog.pair("reconnect: authentication required (re-pair needed)")
    }

    private func backoff(forAttempt n: Int) -> TimeInterval {
        min(1.0 * pow(2.0, Double(min(n, 5))), 30.0)
    }

    private func attemptReconnect() {
        guard central.state == .poweredOn else {
            state = .bluetoothDisabled
            GearanLog.ble("reconnect deferred: bluetooth off")
            return
        }
        guard trustedID != nil else {
            state = .idle
            return
        }
        // Fast path: system-cached peripheral for a known identifier.
        if let uuid = peripheralUUID,
           let known = central.retrievePeripherals(withIdentifiers: [uuid]).first {
            connect(to: known)
            return
        }
        // Slow path: filtered scan for the Gearan service; connect on match.
        state = .reconnecting
        central.scanForPeripherals(
            withServices: [CBUUID(string: GearanLink.serviceUUID)],
            options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        GearanLog.ble("reconnect scan started (attempt \(attempts + 1))")
    }

    private func connect(to p: CBPeripheral) {
        central.stopScan()
        peripheral = p
        state = .reconnecting
        GearanLog.ble("reconnect connect")
        central.connect(p, options: nil)
    }

    private func scheduleRetry(reason: String) {
        attempts += 1
        lastError = reason
        state = .reconnecting
        backoffTask?.cancel()
        let delay = backoff(forAttempt: attempts)
        GearanLog.ble("reconnect retry in \(Int(delay))s (\(reason))")
        backoffTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: UInt64(delay * 1_000_000_000))
            guard let self, !Task.isCancelled else { return }
            self.attemptReconnect()
        }
    }

    public func cancel() {
        backoffTask?.cancel()
        if let p = peripheral { central.cancelPeripheralConnection(p) }
        peripheral = nil
    }
}

extension GearanAutoReconnectManager: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state == .poweredOn {
            if trustedID != nil && state != .connected { attemptReconnect() }
        } else {
            state = .bluetoothDisabled
            GearanLog.ble("bluetooth disabled")
        }
    }

    public func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        GearanLog.ble("reconnect central willRestoreState")
        if trustedID != nil { attemptReconnect() }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                               advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let adv = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []).map { $0.uuidString }
        guard adv.contains(GearanLink.serviceUUID) else { return }
        peripheralUUID = peripheral.identifier
        connect(to: peripheral)
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        attempts = 0
        lastError = nil
        state = .connected
        GearanLog.ble("reconnected")
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        scheduleRetry(reason: error?.localizedDescription ?? "connect failed")
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        if state == .connected || state == .reconnecting {
            scheduleRetry(reason: "link lost")
        } else {
            state = .watchUnavailable
        }
    }
}
