import Foundation
import Security

/// Keychain-backed trusted store. Survives reboot/reinstall-window (kSecAttrAccessibleAfterFirstUnlock).
/// A plain disconnect never deletes the record — only Forget Watch does.
public final class TrustedDeviceStore {
    private let service = "com.gearan.ios.trustedWatch"
    private let account = "trustedWatch"
    private let ownAccount = "ownDeviceId"

    public init() {}

    public func save(_ watch: TrustedWatch) throws {
        let data = try JSONEncoder().encode(watch)
        try write(account: account, data: data)
    }

    public func load() -> TrustedWatch? {
        guard let data = read(account: account) else { return nil }
        return try? JSONDecoder().decode(TrustedWatch.self, from: data)
    }

    public func updateLastSeen(_ iso: String) throws {
        guard var w = load() else { return }
        w.lastSeen = iso
        try save(w)
    }

    public func forget() throws {
        delete(account: account)
    }

    public func ownDeviceId() -> String {
        if let d = read(account: ownAccount), let s = String(data: d, encoding: .utf8) { return s }
        let fresh = UUID().uuidString
        try? write(account: ownAccount, data: Data(fresh.utf8))
        return fresh
    }

    // MARK: - Keychain primitives
    private func write(account: String, data: Data) throws {
        delete(account: account)
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        let status = SecItemAdd(q as CFDictionary, nil)
        guard status == errSecSuccess else { throw PairingError(code: "KEYCHAIN", message: "write failed \(status)") }
    }

    private func read(account: String) -> Data? {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess else { return nil }
        return out as? Data
    }

    private func delete(account: String) {
        let q: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(q as CFDictionary)
    }
}
