import Foundation
import os

/// Redacted diagnostic logging. Never logs keys, nonces, payloads, health or
/// notification content. SAS appears only as MATCHED/MISMATCH.
public enum GearanLog {
    private static let ble = OSLog(subsystem: "com.gearan.ios", category: "GEARAN_BLE")
    private static let pair = OSLog(subsystem: "com.gearan.ios", category: "GEARAN_PAIR")
    private static let crypto = OSLog(subsystem: "com.gearan.ios", category: "GEARAN_CRYPTO")
    private static let link = OSLog(subsystem: "com.gearan.ios", category: "GEARAN_LINK")

    public static func ble(_ msg: String) { os_log("%{public}@", log: ble, type: .info, msg) }
    public static func pair(_ msg: String) { os_log("%{public}@", log: pair, type: .info, msg) }
    public static func crypto(_ msg: String) { os_log("%{public}@", log: crypto, type: .info, msg) }
    public static func link(_ msg: String) { os_log("%{public}@", log: link, type: .info, msg) }
}
