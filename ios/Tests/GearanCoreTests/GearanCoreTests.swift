import XCTest
@testable import GearanCore

final class GearanLinkTests: XCTestCase {
    let key = Data(repeating: 0x42, count: 32)

    func testRoundTrip() throws {
        let f = GearanLink.Frame(messageType: 0x03, flags: 0x0001, requestId: 42, sequence: 7, chunkIndex: 0, totalChunks: 1, payload: Data("{\"seq\":7}".utf8))
        let back = try GearanLink.decode(GearanLink.encode(f, authKey: key), authKey: key)
        XCTAssertEqual(back.payload, f.payload)
        XCTAssertEqual(back.requestId, 42)
    }

    func testBadMagicRejected() throws {
        var raw = try GearanLink.encode(GearanLink.Frame(messageType: 0x03, flags: 0, requestId: 1, sequence: 1, chunkIndex: 0, totalChunks: 1, payload: Data("x".utf8)), authKey: key)
        raw[0] = UInt8(ascii: "X")
        XCTAssertThrowsError(try GearanLink.decode(raw, authKey: key))
    }

    func testAuthFailure() throws {
        let raw = try GearanLink.encode(GearanLink.Frame(messageType: 0x03, flags: 0, requestId: 1, sequence: 1, chunkIndex: 0, totalChunks: 1, payload: Data("hello".utf8)), authKey: key)
        XCTAssertThrowsError(try GearanLink.decode(raw, authKey: Data(repeating: 0x11, count: 32)))
    }

    func testChunkReassembly() throws {
        let payload = Data((0..<1500).map { UInt8($0 & 0xFF) })
        let frames = try GearanLink.fragment(messageType: 0x0E, payload: payload, requestId: 9, sequence: 3)
        XCTAssertEqual(frames.count, 3)
        let r = Reassembler()
        var out: Data?
        for f in [frames[2], frames[0], frames[1]] { if let d = try r.feed(f) { out = d } }
        XCTAssertEqual(out, payload)
    }

    func testReplayWindow() {
        let w = ReplayWindow()
        XCTAssertTrue(w.accept(1)); XCTAssertFalse(w.accept(1)); XCTAssertTrue(w.accept(2))
    }

    func testPairingMachine() {
        let m = GearanPairingStateMachine()
        m.send(.startScanning); XCTAssertEqual(m.state, .scanning)
        m.send(.watchFound); XCTAssertEqual(m.state, .watchFound)
        m.send(.connected); XCTAssertEqual(m.state, .connecting)
        m.send(.connected); XCTAssertEqual(m.state, .handshaking)
        m.send(.handshakeDone(sas: "482731")); XCTAssertEqual(m.state, .waitingForUserConfirmation)
        m.send(.userConfirmed); XCTAssertEqual(m.state, .securingConnection)
        m.send(.sessionSecured); XCTAssertEqual(m.state, .savingTrustedDevice)
        m.send(.trustedSaved); XCTAssertEqual(m.state, .initialSync)
        m.send(.syncDone); XCTAssertEqual(m.state, .paired)
        m.send(.linkLost); XCTAssertEqual(m.state, .reconnecting)
        m.send(.reconnected); XCTAssertEqual(m.state, .connected)
    }

    func testSasFormat() {
        let sas = GearanLink.sas(transcript: Data("gearan-test".utf8))
        XCTAssertEqual(sas.count, 6)
        XCTAssertNotNil(Int(sas))
    }

    func testRelayValidation() {
        let bad = NetworkRelay.Request(id: "1", method: "GET", url: "http://example.com")
        XCTAssertEqual(bad.validate(), "HTTPS_ONLY")
        let ok = NetworkRelay.Request(id: "1", method: "GET", url: "https://example.com")
        XCTAssertNil(ok.validate())
    }

    func testAncsDefaultsUnavailable() {
        XCTAssertEqual(AncsProbe.probe(), .unavailable)
    }

    func testHeartbeatPolicy() {
        XCTAssertEqual(HeartbeatPolicy.interval(active: true, idle: false), 30)
        XCTAssertEqual(HeartbeatPolicy.interval(active: true, idle: true), 120)
    }

    func testSyncEngineIncremental() {
        let e = GearanSyncEngine()
        XCTAssertNil(e.collect(deviceId: "w"))
        e.markDirty(.BATTERY, nowIso: "2026-09-20T00:00:00Z")
        let p = e.collect(deviceId: "w") { $0 == SyncCategory.BATTERY.rawValue ? "{\"percent\":84}" : "{}" }
        XCTAssertNotNil(p)
        XCTAssertEqual(p!.changes.count, 1)
        XCTAssertEqual(p!.changes[0].revision, 1)
        XCTAssertNil(e.collect(deviceId: "w"))
        // stale replay rejected
        let e2 = GearanSyncEngine()
        XCTAssertEqual(e2.apply(p!), [SyncCategory.BATTERY.rawValue])
        XCTAssertTrue(e2.apply(p!).isEmpty)
    }
}
