import Foundation

/// Auto-reconnect policy: exponential backoff, state-aware, never drops trust.
/// Handles: out-of-range → nearby, BT toggle, locked iPhone, app reopen,
/// Watch/iPhone reboot, transient loss.
public final class GearanAutoReconnectManager {
    public enum Reason { case outOfRange, bluetoothOff, appBackground, reboot, transient }
    public var enabled = true
    public private(set) var attempts = 0
    public var onAttempt: ((Int) -> Void)?

    public init() {}

    public func backoff(forAttempt n: Int) -> TimeInterval {
        min(1.0 * pow(2.0, Double(min(n, 5))), 30.0)
    }

    public func recordAttempt() -> TimeInterval {
        attempts += 1
        onAttempt?(attempts)
        return backoff(forAttempt: attempts)
    }

    public func recordConnected() { attempts = 0 }
    public func shouldKeepTrust(on reason: Reason) -> Bool { true } // disconnect != unpair, always
}
