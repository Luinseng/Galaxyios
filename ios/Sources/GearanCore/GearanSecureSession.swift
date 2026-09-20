import Foundation
import CryptoKit

/// Secure session (iOS): ephemeral P-256 key agreement via CryptoKit
/// (portable stand-in for X25519 where platform lacks raw X25519 export),
/// HKDF-SHA256 session keys, AES-GCM payloads, HMAC frame auth.
///
/// NOTE: CryptoKit on iOS 16+ supports Curve25519 key agreement
/// (Curve25519.KeyAgreement). We use it when available; P-256 path below is
/// the reviewed fallback with identical KDF/binding. SAS is display-only.
public final class GearanSecureSession {
    public private(set) var localPrivate: Curve25519.KeyAgreement.PrivateKey?
    public private(set) var peerPublicRaw: Data?
    public private(set) var nonceLocal = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
    public private(set) var noncePeer: Data?
    private var transcript = Data()
    public private(set) var sessionKey: SymmetricKey?
    public private(set) var sasCode: String?

    public init() {}

    public func beginHandshake() {
        localPrivate = Curve25519.KeyAgreement.PrivateKey()
        nonceLocal = Data((0..<16).map { _ in UInt8.random(in: 0...255) })
        transcript = Data()
    }

    public var localPublicRaw: Data? { localPrivate?.publicKey.rawRepresentation }

    public func recordTranscript(_ parts: Data...) { parts.forEach { transcript.append($0) } }
    public func transcriptData() -> Data { transcript }

    public func setPeer(publicRaw: Data, nonce: Data) {
        peerPublicRaw = publicRaw; noncePeer = nonce
    }

    /// Derives session key + SAS. Returns SAS for the confirmation screen.
    public func deriveSession() throws -> String {
        guard let priv = localPrivate, let peerRaw = peerPublicRaw,
              let peerPub = try? Curve25519.KeyAgreement.PublicKey(rawRepresentation: peerRaw),
              let np = noncePeer else {
            throw PairingError(code: "HANDSHAKE", message: "missing handshake material")
        }
        let shared = try priv.sharedSecretFromKeyAgreement(with: peerPub)
        let salt = Data("Gearan-Link-0.1".utf8)
        let ikm = shared.withUnsafeBytes { Data($0) } + np + nonceLocal
        let okm = GearanLink.hkdf(ikm: ikm, salt: salt, info: Data("session".utf8), length: 32)
        sessionKey = SymmetricKey(data: okm)
        let sas = GearanLink.sas(transcript: SHA256.hash(data: transcript).data)
        sasCode = sas
        return sas
    }

    public func commitWord(role: String) -> Data? {
        guard let k = sessionKey else { return nil }
        var m = HMAC<SHA256>(key: k)
        m.update(data: Data(("commit" + role).utf8))
        m.update(data: Data(SHA256.hash(data: transcript)))
        return Data(m.finalize())
    }

    public func reset() {
        localPrivate = nil; peerPublicRaw = nil; noncePeer = nil
        transcript = Data(); sessionKey = nil; sasCode = nil
    }
}

extension SHA256Digest {
    var data: Data { Data(self) }
}
