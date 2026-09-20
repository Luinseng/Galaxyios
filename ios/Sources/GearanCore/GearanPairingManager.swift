import Foundation

/// Pairing lifecycle — identical state set to wear/GearanPairingStateMachine.
public enum PairingState: String, Equatable {
    case idle, scanning, watchFound, connecting, handshaking
    case waitingForUserConfirmation, securingConnection, savingTrustedDevice
    case initialSync, paired, reconnecting, connected, disconnected, failed
}

public struct PairingError: Error, Equatable {
    public var code: String; public var message: String
    public init(code: String, message: String) { self.code = code; self.message = message }
}

public enum PairingEvent {
    case startScanning, watchFound, connected, handshakeDone(sas: String)
    case userConfirmed, userCancelled, sessionSecured, trustedSaved, syncDone
    case linkLost, reconnected, failed(PairingError), reset
}

/// Pure state machine (no Bluetooth) — unit-tested.
public final class GearanPairingStateMachine {
    public private(set) var state: PairingState = .idle
    public init(_ initial: PairingState = .idle) { state = initial }
    @discardableResult
    public func send(_ e: PairingEvent) -> PairingState {
        switch state {
        case .idle:
            if case .startScanning = e { state = .scanning }
        case .scanning:
            switch e {
            case .watchFound: state = .watchFound
            case .connected: state = .connecting
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .watchFound:
            switch e {
            case .connected: state = .connecting
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .connecting:
            switch e {
            case .connected: state = .handshaking
            case .handshakeDone: state = .waitingForUserConfirmation
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .handshaking:
            switch e {
            case .handshakeDone: state = .waitingForUserConfirmation
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .waitingForUserConfirmation:
            switch e {
            case .userConfirmed: state = .securingConnection
            case .userCancelled: state = .disconnected
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .securingConnection:
            switch e {
            case .sessionSecured: state = .savingTrustedDevice
            case .failed: state = .failed
            default: break
            }
        case .savingTrustedDevice:
            switch e {
            case .trustedSaved: state = .initialSync
            case .failed: state = .failed
            default: break
            }
        case .initialSync:
            switch e {
            case .syncDone: state = .paired
            case .failed: state = .failed
            default: break
            }
        case .paired:
            switch e {
            case .reconnected: state = .connected
            case .linkLost: state = .reconnecting
            case .reset: state = .idle
            default: break
            }
        case .reconnecting:
            switch e {
            case .reconnected: state = .connected
            case .failed: state = .failed
            case .reset: state = .idle
            default: break
            }
        case .connected:
            switch e {
            case .linkLost: state = .reconnecting
            case .reset: state = .idle
            default: break
            }
        case .disconnected:
            switch e {
            case .startScanning: state = .scanning
            case .reset: state = .idle
            default: break
            }
        case .failed:
            switch e {
            case .reset: state = .idle
            case .startScanning: state = .scanning
            default: break
            }
        }
        return state
    }
}
