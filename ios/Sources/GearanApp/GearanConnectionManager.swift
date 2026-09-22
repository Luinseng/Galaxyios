import Foundation
import Combine
import CoreBluetooth
import GearanCore

/// Owns the BLE connection used during pairing. GATT readiness requires all
/// three characteristics, readable device info, and active TX notifications.
public final class GearanConnectionManager: NSObject, ObservableObject {
    @Published public private(set) var stage = "idle" {
        didSet { onStageChange?(stage) }
    }
    @Published public private(set) var deviceInfo: String?
    @Published public var lastError: GearanStageError?
    @Published public private(set) var isReady = false

    public var onReceive: ((Data) -> Void)?
    public var onDisconnect: ((Error?) -> Void)?
    public var onError: ((GearanStageError) -> Void)?
    public var onStageChange: ((String) -> Void)?

    private var central: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var ctrlChar: CBCharacteristic?
    private var rxChar: CBCharacteristic?
    private var txChar: CBCharacteristic?
    private var pendingIdentifier: UUID?
    private var completion: ((Result<String, GearanStageError>) -> Void)?
    private var ctrlRead = false
    private var txNotificationsEnabled = false
    private var outboundChunks: [Data] = []
    private var writeInFlight = false
    private var intentionalDisconnect = false

    public override init() {
        super.init()
        central = CBCentralManager(
            delegate: self,
            queue: .main,
            options: [CBCentralManagerOptionRestoreIdentifierKey: BLEScanner.restoreID + ".conn"]
        )
    }

    /// Resolves the scanner UUID through this object's central. Requests made
    /// before Bluetooth becomes available are queued.
    public func connect(
        to identifier: UUID,
        completion: @escaping (Result<String, GearanStageError>) -> Void
    ) {
        cancelConnection(resetStage: false)
        pendingIdentifier = identifier
        self.completion = completion
        lastError = nil
        deviceInfo = nil
        isReady = false
        guard central.state == .poweredOn else {
            setStage("waiting for Bluetooth")
            return
        }
        resolveAndConnect()
    }

    public func disconnect() {
        intentionalDisconnect = true
        completion = nil
        cancelConnection(resetStage: true)
    }

    /// Writes transport bytes to RX, split at CoreBluetooth's negotiated size.
    public func send(_ data: Data) {
        guard isReady, let peripheral, let rxChar else {
            fail(stage: "send", code: "NOT_READY", message: "GATT transport is not ready")
            return
        }
        guard !data.isEmpty else { return }
        let type = writeType(for: rxChar)
        let maximum = peripheral.maximumWriteValueLength(for: type)
        guard maximum > 0 else {
            fail(stage: "send", code: "INVALID_MTU", message: "Peripheral reported an invalid write size")
            return
        }
        var offset = 0
        while offset < data.count {
            let end = min(offset + maximum, data.count)
            outboundChunks.append(data.subdata(in: offset..<end))
            offset = end
        }
        pumpWrites()
    }

    private func resolveAndConnect() {
        guard let identifier = pendingIdentifier else { return }
        guard let resolved = central.retrievePeripherals(withIdentifiers: [identifier]).first else {
            fail(
                stage: "resolve peripheral",
                code: "NO_PERIPHERAL",
                message: "Peripheral is no longer available. Scan again and retry."
            )
            return
        }
        pendingIdentifier = nil
        peripheral = resolved
        resolved.delegate = self
        intentionalDisconnect = false
        setStage("connecting")
        GearanLog.ble("connect requested")
        central.connect(resolved, options: nil)
    }

    private func writeType(for characteristic: CBCharacteristic) -> CBCharacteristicWriteType {
        characteristic.properties.contains(.writeWithoutResponse) ? .withoutResponse : .withResponse
    }

    private func pumpWrites() {
        guard !writeInFlight, !outboundChunks.isEmpty,
              let peripheral, let rxChar else { return }
        let type = writeType(for: rxChar)
        if type == .withoutResponse && !peripheral.canSendWriteWithoutResponse { return }
        let chunk = outboundChunks.removeFirst()
        writeInFlight = type == .withResponse
        peripheral.writeValue(chunk, for: rxChar, type: type)
        if type == .withoutResponse { pumpWrites() }
    }

    private func finishIfReady() {
        guard ctrlRead, txNotificationsEnabled, let info = deviceInfo, !isReady else { return }
        isReady = true
        setStage("GATT ready")
        GearanLog.link("GATT ready; device info read (\(info.prefix(24))...)")
        let callback = completion
        completion = nil
        callback?(.success(info))
    }

    private func cancelConnection(resetStage: Bool) {
        if let peripheral { central.cancelPeripheralConnection(peripheral) }
        pendingIdentifier = nil
        peripheral = nil
        ctrlChar = nil
        rxChar = nil
        txChar = nil
        ctrlRead = false
        txNotificationsEnabled = false
        outboundChunks.removeAll()
        writeInFlight = false
        isReady = false
        if resetStage { setStage("idle") }
    }

    private func setStage(_ value: String) { stage = value }

    private func fail(stage: String, code: String, message: String) {
        let error = GearanStageError(stage: stage, code: code, message: message)
        lastError = error
        isReady = false
        setStage("failed")
        GearanLog.ble("FAILED [\(code)] @ \(stage): \(message)")
        onError?(error)
        let callback = completion
        completion = nil
        callback?(.failure(error))
    }
}

extension GearanConnectionManager: CBCentralManagerDelegate {
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            if pendingIdentifier != nil { resolveAndConnect() }
        case .unauthorized:
            if pendingIdentifier != nil {
                fail(stage: "Bluetooth", code: "UNAUTHORIZED", message: "Bluetooth permission is denied")
            }
        case .unsupported:
            if pendingIdentifier != nil {
                fail(stage: "Bluetooth", code: "UNSUPPORTED", message: "Bluetooth LE is not supported")
            }
        default:
            if pendingIdentifier != nil { setStage("waiting for Bluetooth") }
        }
    }

    public func centralManager(_ central: CBCentralManager, willRestoreState dict: [String: Any]) {
        GearanLog.ble("connection central restored")
    }

    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        setStage("discovering services")
        peripheral.discoverServices([CBUUID(string: GearanLink.serviceUUID)])
    }

    public func centralManager(
        _ central: CBCentralManager,
        didFailToConnect peripheral: CBPeripheral,
        error: Error?
    ) {
        fail(stage: "connect", code: "CONNECT_FAILED", message: error?.localizedDescription ?? "Unknown error")
    }

    public func centralManager(
        _ central: CBCentralManager,
        didDisconnectPeripheral peripheral: CBPeripheral,
        error: Error?
    ) {
        let wasIntentional = intentionalDisconnect
        intentionalDisconnect = false
        self.peripheral = nil
        ctrlChar = nil
        rxChar = nil
        txChar = nil
        isReady = false
        outboundChunks.removeAll()
        writeInFlight = false
        setStage(wasIntentional ? "idle" : "disconnected")
        if !wasIntentional { onDisconnect?(error) }
    }
}

extension GearanConnectionManager: CBPeripheralDelegate {
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error {
            fail(stage: "GATT service discovery", code: "SERVICES_FAILED", message: error.localizedDescription)
            return
        }
        let uuid = CBUUID(string: GearanLink.serviceUUID)
        guard let service = peripheral.services?.first(where: { $0.uuid == uuid }) else {
            fail(stage: "GATT service discovery", code: "SERVICE_NOT_FOUND", message: "Gearan service not found")
            return
        }
        setStage("discovering characteristics")
        peripheral.discoverCharacteristics(
            [CBUUID(string: GearanLink.ctrlUUID),
             CBUUID(string: GearanLink.rxUUID),
             CBUUID(string: GearanLink.txUUID)],
            for: service
        )
    }

    public func peripheral(
        _ peripheral: CBPeripheral,
        didDiscoverCharacteristicsFor service: CBService,
        error: Error?
    ) {
        if let error {
            fail(stage: "GATT characteristic discovery", code: "CHARS_FAILED", message: error.localizedDescription)
            return
        }
        let characteristics = service.characteristics ?? []
        ctrlChar = characteristics.first { $0.uuid == CBUUID(string: GearanLink.ctrlUUID) }
        rxChar = characteristics.first { $0.uuid == CBUUID(string: GearanLink.rxUUID) }
        txChar = characteristics.first { $0.uuid == CBUUID(string: GearanLink.txUUID) }
        guard let ctrlChar else {
            fail(stage: "GATT characteristic discovery", code: "CTRL_NOT_FOUND", message: "CTRL characteristic not found")
            return
        }
        guard let rxChar else {
            fail(stage: "GATT characteristic discovery", code: "RX_NOT_FOUND", message: "RX characteristic not found")
            return
        }
        guard let txChar else {
            fail(stage: "GATT characteristic discovery", code: "TX_NOT_FOUND", message: "TX characteristic not found")
            return
        }
        guard ctrlChar.properties.contains(.read) else {
            fail(stage: "GATT validation", code: "CTRL_NOT_READABLE", message: "CTRL characteristic is not readable")
            return
        }
        guard rxChar.properties.contains(.write) || rxChar.properties.contains(.writeWithoutResponse) else {
            fail(stage: "GATT validation", code: "RX_NOT_WRITABLE", message: "RX characteristic is not writable")
            return
        }
        guard txChar.properties.contains(.notify) || txChar.properties.contains(.indicate) else {
            fail(stage: "GATT validation", code: "TX_NOT_NOTIFIABLE", message: "TX characteristic cannot notify")
            return
        }
        setStage("configuring GATT")
        peripheral.setNotifyValue(true, for: txChar)
        peripheral.readValue(for: ctrlChar)
    }

    public func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateNotificationStateFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard characteristic.uuid == CBUUID(string: GearanLink.txUUID) else { return }
        if let error {
            fail(stage: "enable TX notifications", code: "NOTIFY_FAILED", message: error.localizedDescription)
            return
        }
        guard characteristic.isNotifying else {
            fail(stage: "enable TX notifications", code: "NOTIFY_DISABLED", message: "TX notifications were not enabled")
            return
        }
        txNotificationsEnabled = true
        finishIfReady()
    }

    public func peripheral(
        _ peripheral: CBPeripheral,
        didUpdateValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        if characteristic.uuid == CBUUID(string: GearanLink.ctrlUUID) {
            if let error {
                fail(stage: "read device info", code: "READ_FAILED", message: error.localizedDescription)
                return
            }
            guard let data = characteristic.value,
                  let info = String(data: data, encoding: .utf8), !info.isEmpty else {
                fail(stage: "read device info", code: "BAD_PAYLOAD", message: "CTRL value is unreadable")
                return
            }
            deviceInfo = info
            ctrlRead = true
            finishIfReady()
            return
        }
        guard characteristic.uuid == CBUUID(string: GearanLink.txUUID) else { return }
        if let error {
            fail(stage: "receive", code: "TX_NOTIFICATION_FAILED", message: error.localizedDescription)
            return
        }
        if let data = characteristic.value { onReceive?(data) }
    }

    public func peripheral(
        _ peripheral: CBPeripheral,
        didWriteValueFor characteristic: CBCharacteristic,
        error: Error?
    ) {
        guard characteristic.uuid == CBUUID(string: GearanLink.rxUUID) else { return }
        writeInFlight = false
        if let error {
            outboundChunks.removeAll()
            fail(stage: "send", code: "WRITE_FAILED", message: error.localizedDescription)
            return
        }
        pumpWrites()
    }

    public func peripheralIsReady(toSendWriteWithoutResponse peripheral: CBPeripheral) {
        pumpWrites()
    }
}
