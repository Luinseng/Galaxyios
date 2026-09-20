import Foundation
import Combine
import CoreBluetooth
import GearanCore

/// Basic GATT connection (MILESTONE 1): connect → discoverServices →
/// discoverCharacteristics → read CTRL/DEVICE_INFO ("Gearan/…;Galaxy Watch4
/// Classic;proto=0.1") — all BEFORE any crypto. Staged errors carry the exact
/// failing stage for ConnectionErrorView.
public final class GearanConnectionManager: NSObject, ObservableObject {
    @Published public var stage: String = "idle"
    @Published public var deviceInfo: String?
    @Published public var lastError: GearanStageError?

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var ctrlChar: CBCharacteristic?
    private var completion: ((Result<String, GearanStageError>) -> Void)?

    public override init() {
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [CBCentralManagerOptionRestoreIdentifierKey: BLEScanner.restoreID + ".conn"]
        )
    }

    /// Connect to an already-discovered peripheral. `resolve` maps the scanned
    /// UUID to the live CBPeripheral (the app retains it via retrievePeripherals).
    public func connect(to identifier: UUID, resolve: (UUID) -> CBPeripheral?,
                        completion: @escaping (Result<String, GearanStageError>) -> Void) {
        guard let p = resolve(identifier) else {
            fail(stage: "resolve peripheral", code: "NO_PERIPHERAL",
                 message: "Peripheral not in central cache", completion: completion)
            return
        }
        self.completion = completion
        peripheral = p
        p.delegate = self
        stage = "connect"
        GearanLog.ble("connect requested")
        central.connect(p, options: nil)
    }

    public func cancel() {
        if let p = peripheral { central.cancelPeripheralConnection(p) }
        peripheral = nil; ctrlChar = nil; stage = "idle"
    }

    private func fail(stage: String, code: String, message: String,
                      completion: ((Result<String, GearanStageError>) -> Void)? = nil) {
        let e = GearanStageError(stage: stage, code: code, message: message)
        lastError = e
        self.stage = "failed"
        GearanLog.ble("FAILED [\(code)] @ \(stage): \(message)")
        (completion ?? self.completion)?(.failure(e))
    }
}

extension GearanConnectionManager: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        if central.state != .poweredOn {
            GearanLog.ble("central not powered on (\(central.state.rawValue))")
        }
    }

    public func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        GearanLog.ble("conn central willRestoreState")
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        stage = "discover services"
        GearanLog.ble("connected, discovering services")
        peripheral.discoverServices([CBUUID(string: GearanLink.serviceUUID)])
    }

    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        fail(stage: "connect", code: "CONNECT_FAILED", message: error?.localizedDescription ?? "unknown")
    }

    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        GearanLog.ble("disconnected")
        stage = "idle"
    }
}

extension GearanConnectionManager: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error = error {
            fail(stage: "GATT service discovery", code: "SERVICES_FAILED", message: error.localizedDescription)
            return
        }
        guard let svc = peripheral.services?.first(where: { $0.uuid.uuidString == GearanLink.serviceUUID }) else {
            fail(stage: "GATT service discovery", code: "SERVICE_NOT_FOUND",
                 message: "Service UUID not found")
            return
        }
        stage = "discover characteristics"
        peripheral.discoverCharacteristics(
            [CBUUID(string: GearanLink.ctrlUUID), CBUUID(string: GearanLink.rxUUID), CBUUID(string: GearanLink.txUUID)],
            for: svc)
    }

    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error = error {
            fail(stage: "GATT characteristic discovery", code: "CHARS_FAILED", message: error.localizedDescription)
            return
        }
        guard let ctrl = service.characteristics?.first(where: { $0.uuid.uuidString == GearanLink.ctrlUUID }) else {
            fail(stage: "GATT characteristic discovery", code: "CTRL_NOT_FOUND",
                 message: "CTRL characteristic not found")
            return
        }
        ctrlChar = ctrl
        stage = "read device info"
        peripheral.readValue(for: ctrl)
    }

    public func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error = error {
            fail(stage: "read device info", code: "READ_FAILED", message: error.localizedDescription)
            return
        }
        guard characteristic.uuid.uuidString == GearanLink.ctrlUUID,
              let data = characteristic.value,
              let info = String(data: data, encoding: .utf8) else {
            fail(stage: "read device info", code: "BAD_PAYLOAD", message: "CTRL value unreadable")
            return
        }
        deviceInfo = info
        stage = "device info OK"
        GearanLog.link("device info read (\(info.prefix(24))…)")
        completion?(.success(info))
    }
}
