import Foundation

/// Trusted Watch record. NEVER keyed by Bluetooth MAC (randomized).
/// Stored in Keychain (see TrustedDeviceStore). Same schema as wear/.
public struct TrustedWatch: Codable, Equatable {
    public var gearDeviceId: String
    public var model: String
    public var displayName: String
    public var protocolVersion: Int
    public var publicIdentity: String
    public var pairingDate: String
    public var lastSeen: String
    public var capabilities: [String]
    public var softwareVersion: String
    public var preferredTransport: String

    public init(gearDeviceId: String, model: String = "Galaxy Watch4 Classic",
                displayName: String = "Galaxy Watch4 Classic",
                protocolVersion: Int = 1, publicIdentity: String = "",
                pairingDate: String = "", lastSeen: String = "",
                capabilities: [String] = [], softwareVersion: String = "",
                preferredTransport: String = "ble-gatt") {
        self.gearDeviceId = gearDeviceId; self.model = model
        self.displayName = displayName
        self.protocolVersion = protocolVersion; self.publicIdentity = publicIdentity
        self.pairingDate = pairingDate; self.lastSeen = lastSeen
        self.capabilities = capabilities; self.softwareVersion = softwareVersion
        self.preferredTransport = preferredTransport
    }
}

public enum CapabilityID: String, CaseIterable {
    case BATTERY, HEART_RATE, HEALTH_SENSORS, BIA, SPO2, NETWORK_RELAY
    case GEARAN_NOTIFICATIONS, ANCS_EXPERIMENTAL, DEVICE_INFO, DIAGNOSTICS
}

public enum CapabilityStatus: String, Codable {
    case AVAILABLE, UNAVAILABLE, PERMISSION_REQUIRED, SDK_RESTRICTION, EXPERIMENTAL
}

public struct CapabilityReport: Codable, Equatable {
    public var id: String; public var status: CapabilityStatus; public var detail: String
    public init(id: String, status: CapabilityStatus, detail: String = "") {
        self.id = id; self.status = status; self.detail = detail
    }
}
