import Foundation

/// Diagnostics snapshot (no secrets). Mirrors wear/DiagnosticsSnapshot.
public struct DiagnosticsSnapshot: Codable, Equatable {
    public var watchModel = "Galaxy Watch4 Classic"
    public var connectionStatus = "unknown"
    public var pairingState = "idle"
    public var gearDeviceId = ""
    public var protocolVersion = 1
    public var bluetoothState = "unknown"
    public var rssi: Int?
    public var mtu = 23
    public var txBytes: Int64 = 0
    public var rxBytes: Int64 = 0
    public var packetRetries: Int64 = 0
    public var lastHeartbeat: String?
    public var lastSync: String?
    public var capabilities: [String] = []
    public init() {}
}

extension DiagnosticsSnapshot: CustomDebugStringConvertible {
    /// Redacted one-line summary for Export Diagnostics (no secrets by construction).
    public var debugDescription: String {
        "Gearan diag model=\(watchModel) conn=\(connectionStatus) pair=\(pairingState) " +
        "proto=\(protocolVersion) bt=\(bluetoothState) mtu=\(mtu) " +
        "tx=\(txBytes) rx=\(rxBytes) retries=\(packetRetries) caps=\(capabilities.joined(separator: ","))"
    }
}

/// ANCS probe: experimental. Real availability decided on hardware via service
/// discovery; default UNAVAILABLE until a field test confirms otherwise.
public enum AncsProbe {
    public enum Result: String { case available, unavailable, unknown }
    public static func probe() -> Result { .unavailable }
}

/// Adaptive heartbeat intervals (battery-aware, Watch4 Classic optimized).
public enum HeartbeatPolicy {
    public static func interval(active: Bool, idle: Bool) -> TimeInterval {
        guard active else { return -1 } // paused
        return idle ? 120 : 30
    }
}
