import Foundation
import Combine
import CoreBluetooth
import GearanCore

/// Scans ONLY for the Gearan Service UUID. Never shows headsets/speakers.
/// Watch advertises as "Gearan Watch4C" + manufacturer blob GR|ver|id|caps.
///
/// Lifetime: the CBCentralManager is retained by this object and created with
/// a persistent restore identifier, so the system can restore scanning after
/// the app was killed / phone locked (background `bluetooth-central` mode).
/// `startScan()` before `.poweredOn` is queued, not lost.
public final class BLEScanner: NSObject, ObservableObject {
    public static let restoreID = "com.gearan.ios.central"

    @Published public var discoveries: [ScannedWatch] = []
    @Published public var centralState: String = "unknown"
    @Published public var isScanning = false

    private var central: CBCentralManager!
    private var seen: [UUID: ScannedWatch] = [:]
    private var pendingScan = false
    public var onFound: ((ScannedWatch) -> Void)?

    /// Debug record for BLE Scan Debug screen. No MAC address (iOS hides it;
    /// peripheral.identifier is a UUID, not a MAC).
    public struct ScannedWatch: Identifiable, Equatable {
        public var id: UUID               // CBPeripheral identifier (UUID, not MAC)
        public var name: String
        public var rssi: Int
        public var serviceUUIDs: [String] // advertised service UUIDs
        public var isGearan: Bool
        public init(id: UUID, name: String, rssi: Int, serviceUUIDs: [String], isGearan: Bool) {
            self.id = id; self.name = name; self.rssi = rssi
            self.serviceUUIDs = serviceUUIDs; self.isGearan = isGearan
        }
    }

    public override init() {
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [CBCentralManagerOptionRestoreIdentifierKey: BLEScanner.restoreID]
        )
    }

    /// NOTE: no createBond() exists on iOS. Bonding is triggered by the OS
    /// when accessing encrypted characteristics; Gearan trust is independent.
    public func startScan() {
        guard central.state == .poweredOn else {
            pendingScan = true
            GearanLog.ble("scan queued (state=\(label(central.state)))")
            return
        }
        startScanNow()
    }

    private func startScanNow() {
        seen.removeAll(); discoveries.removeAll()
        let svc = CBUUID(string: GearanLink.serviceUUID)
        central.scanForPeripherals(withServices: [svc], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
        isScanning = true
        GearanLog.ble("scan started (Gearan UUID filter)")
    }

    public func stopScan() {
        pendingScan = false
        central.stopScan(); isScanning = false
        GearanLog.ble("scan stopped")
    }

    func label(_ s: CBManagerState) -> String {
        switch s {
        case .poweredOn: return "poweredOn"
        case .poweredOff: return "poweredOff"
        case .unauthorized: return "unauthorized"
        case .unsupported: return "unsupported"
        case .resetting: return "resetting"
        case .unknown: return "unknown"
        @unknown default: return "unknown"
        }
    }
}

extension BLEScanner: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        centralState = label(central.state)
        GearanLog.ble("central state=\(centralState)")
        if central.state == .poweredOn && pendingScan {
            pendingScan = false
            startScanNow()
        }
        if central.state != .poweredOn {
            isScanning = false
        }
    }

    public func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        // System-restored centrals after relaunch (locked phone / reboot).
        GearanLog.ble("central willRestoreState (\(dict.keys.count) keys)")
        if pendingScan || isScanning { startScanNow() }
    }

    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                               advertisementData: [String: Any], rssi RSSI: NSNumber) {
        let advUUIDs = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? [CBUUID] ?? []).map { $0.uuidString }
        let name = peripheral.name ?? (advertisementData[CBAdvertisementDataLocalNameKey] as? String) ?? "?"
        // Double-check: service-UUID filter already applied; name is informational.
        let looksGearan = advUUIDs.contains(GearanLink.serviceUUID)
            || name.hasPrefix("Gearan") || name.contains("Watch4") || name.contains("Galaxy Watch")
        guard looksGearan else { return }
        let w = ScannedWatch(id: peripheral.identifier,
                             name: "Galaxy Watch4 Classic",
                             rssi: RSSI.intValue,
                             serviceUUIDs: advUUIDs,
                             isGearan: advUUIDs.contains(GearanLink.serviceUUID))
        if seen[w.id] == nil {
            seen[w.id] = w; discoveries.append(w)
            GearanLog.ble("found Watch4C rssi=\(w.rssi)")
            onFound?(w)
        }
    }
}
