import Foundation

/// Staged connection/pairing error (mirrors wear GearanStageError).
/// Shown in ConnectionErrorView with stage + code + Retry.
public struct GearanStageError: Error, Equatable {
    public var stage: String
    public var code: String
    public var message: String
    public init(stage: String, code: String, message: String) {
        self.stage = stage; self.code = code; self.message = message
    }
}
