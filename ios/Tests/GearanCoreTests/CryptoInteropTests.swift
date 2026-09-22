import XCTest
@testable import GearanCore

final class CryptoInteropTests: XCTestCase {
    func testX25519WirePublicKeyIsRaw32Bytes() {
        let session = GearanSecureSession()
        session.beginHandshake()
        XCTAssertEqual(session.localPublicRaw?.count, 32)
    }

    func testNonceOrderIsRoleBased() {
        let local = Data(repeating: 0x11, count: 16)
        let peer = Data(repeating: 0x22, count: 16)
        let initiator = GearanSecureSession.orderedNonces(role: .initiator, local: local, peer: peer)
        let responder = GearanSecureSession.orderedNonces(role: .responder, local: local, peer: peer)
        XCTAssertEqual(initiator.0, local); XCTAssertEqual(initiator.1, peer)
        XCTAssertEqual(responder.0, peer); XCTAssertEqual(responder.1, local)
    }

    func testRejectsNonCanonicalX25519WireKey() {
        XCTAssertThrowsError(try GearanSecureSession().setPeer(
            publicRaw: Data(repeating: 0, count: 44),
            nonce: Data(repeating: 0, count: 16)
        ))
    }
}
