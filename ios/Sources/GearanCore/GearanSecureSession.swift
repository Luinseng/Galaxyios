import CryptoKit
import Foundation
import Security

/// Ephemeral X25519 session. Wire public keys are RFC 7748 raw 32-byte values.
/// iOS supports only X25519 here; callers must not advertise optional P-256.
public final class GearanSecureSession {
    public enum Role { case initiator, responder }

    public let role: Role
    public private(set) var localPrivate: Curve25519.KeyAgreement.PrivateKey?
    public private(set) var peerPublicRaw: Data?
    public private(set) var nonceLocal: Data
    public private(set) var noncePeer: Data?
    public private(set) var sessionKey: SymmetricKey?
    public private(set) var sasCode: String?
    private var transcript = Data()

    public init(role: Role = .initiator) {
        self.role = role
        nonceLocal = Self.randomNonce()
    }

    public func beginHandshake() {
        localPrivate = Curve25519.KeyAgreement.PrivateKey()
        nonceLocal = Self.randomNonce()
        peerPublicRaw = nil
        noncePeer = nil
        transcript = Data()
        sessionKey = nil
        sasCode = nil
    }

    public var localPublicRaw: Data? { localPrivate?.publicKey.rawRepresentation }

    public func recordTranscript(_ parts: Data...) { parts.forEach { transcript.append($0) } }
    public func transcriptData() -> Data { transcript }

    public func setPeer(publicRaw: Data, nonce: Data) throws {
        guard publicRaw.count == 32 else {
            throw PairingError(code: "HANDSHAKE_KEY", message: "X25519 public key must be exactly 32 raw bytes")
        }
        guard nonce.count == 16 else {
            throw PairingError(code: "HANDSHAKE_NONCE", message: "pairing nonce must be exactly 16 bytes")
        }
        _ = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: publicRaw)
        peerPublicRaw = publicRaw
        noncePeer = nonce
    }

    /// Derives HKDF(shared || nonceI || nonceW, salt, "session") and SAS.
    public func deriveSession() throws -> String {
        guard let privateKey = localPrivate, let peerRaw = peerPublicRaw, let peerNonce = noncePeer else {
            throw PairingError(code: "HANDSHAKE", message: "missing handshake material")
        }
        let peerKey: Curve25519.KeyAgreement.PublicKey
        do {
            peerKey = try Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerRaw)
        } catch {
            throw PairingError(code: "HANDSHAKE_KEY", message: "invalid X25519 public key")
        }
        let shared = try privateKey.sharedSecretFromKeyAgreement(with: peerKey)
        let sharedBytes = shared.withUnsafeBytes { Data($0) }
        let (nonceI, nonceW) = Self.orderedNonces(role: role, local: nonceLocal, peer: peerNonce)
        let derived = GearanLink.hkdf(
            ikm: sharedBytes + nonceI + nonceW,
            salt: Data("Gearan-Link-0.1".utf8),
            info: Data("session".utf8),
            length: 32
        )
        sessionKey = SymmetricKey(data: derived)
        let sas = GearanLink.sas(transcript: Data(SHA256.hash(data: transcript)))
        sasCode = sas
        return sas
    }

    public func commitWord(role: String) throws -> Data {
        guard let key = sessionKey else {
            throw PairingError(code: "HANDSHAKE", message: "session key not derived")
        }
        var authenticator = HMAC<SHA256>(key: key)
        authenticator.update(data: Data(("commit" + role).utf8))
        authenticator.update(data: Data(SHA256.hash(data: transcript)))
        return Data(authenticator.finalize())
    }

    public func reset() {
        localPrivate = nil
        peerPublicRaw = nil
        noncePeer = nil
        transcript = Data()
        sessionKey = nil
        sasCode = nil
    }

    static func orderedNonces(role: Role, local: Data, peer: Data) -> (Data, Data) {
        role == .initiator ? (local, peer) : (peer, local)
    }

    private static func randomNonce() -> Data {
        var bytes = [UInt8](repeating: 0, count: 16)
        precondition(SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes) == errSecSuccess)
        return Data(bytes)
    }
}
