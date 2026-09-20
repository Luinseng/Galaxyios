import Foundation

/// Incremental sync engine (iOS side). Same categories and rules as
/// wear/GearanSyncEngine: per-category revision + timestamp, only dirty
/// categories travel, stale revisions are rejected. Wire-compatible JSON.
public enum SyncCategory: String, CaseIterable, Codable {
    case DEVICE_STATE, BATTERY, HEALTH, NOTIFICATIONS, NETWORK, SETTINGS, CAPABILITIES
}

public struct CategoryChange: Codable, Equatable {
    public var category: String
    public var revision: Int64
    public var timestamp: String
    public var dataJson: String
    public init(category: String, revision: Int64, timestamp: String, dataJson: String = "{}") {
        self.category = category; self.revision = revision
        self.timestamp = timestamp; self.dataJson = dataJson
    }
}

public struct SyncPayload: Codable, Equatable {
    public var deviceId: String
    public var changes: [CategoryChange]
    public init(deviceId: String, changes: [CategoryChange]) {
        self.deviceId = deviceId; self.changes = changes
    }
}

public final class GearanSyncEngine {
    private var revisions: [String: Int64] = [:]
    private var dirty = Set<String>()
    private var pendingTs: [String: String] = [:]
    public private(set) var lastSync: String?

    public init() {
        SyncCategory.allCases.forEach { revisions[$0.rawValue] = 0 }
    }

    @discardableResult
    public func markDirty(_ category: SyncCategory, nowIso: String) -> Bool {
        dirty.insert(category.rawValue)
        pendingTs[category.rawValue] = nowIso
        return true
    }

    public func pendingCount() -> Int { dirty.count }

    /// Collects dirty categories (each revision = prev + 1). Nil when clean —
    /// no empty syncs on the radio.
    public func collect(deviceId: String, dataFor: (String) -> String = { _ in "{}" }) -> SyncPayload? {
        guard !dirty.isEmpty else { return nil }
        let changes = dirty.sorted().map { cat -> CategoryChange in
            let rev = (revisions[cat] ?? 0) + 1
            revisions[cat] = rev
            return CategoryChange(category: cat, revision: rev,
                                  timestamp: pendingTs[cat] ?? "", dataJson: dataFor(cat))
        }
        dirty.removeAll(); pendingTs.removeAll()
        GearanLog.link("incremental sync: \(changes.count) categories")
        return SyncPayload(deviceId: deviceId, changes: changes)
    }

    /// Applies a received payload; returns categories actually newer than local.
    @discardableResult
    public func apply(_ payload: SyncPayload) -> [String] {
        var applied: [String] = []
        for c in payload.changes {
            if let local = revisions[c.category], c.revision > local {
                revisions[c.category] = c.revision
                applied.append(c.category)
            }
        }
        if !applied.isEmpty {
            lastSync = Date().ISO8601Format()
            GearanLog.link("applied \(applied.count) categories")
        }
        return applied
    }
}
