import XCTest
@testable import GearanCore

final class GearanLinkHardeningTests: XCTestCase {
    private let key = Data(repeating: 0x4B, count: 32)

    private func frame(_ index: UInt16, _ total: UInt16, _ payload: Data,
                       requestId: UInt32 = 7) -> GearanLink.Frame {
        GearanLink.Frame(messageType: UInt8(GearanLink.mtSyncData), flags: 0,
                         requestId: requestId, sequence: UInt32(index), chunkIndex: index,
                         totalChunks: total, payload: payload)
    }

    func testTrailingBytesRejected() throws {
        var raw = try GearanLink.encode(frame(0, 1, Data("ok".utf8)), authKey: key)
        raw.append(1)
        XCTAssertThrowsError(try GearanLink.decode(raw, authKey: key))
    }

    func testDuplicateReplacementAndMaximumSize() throws {
        let r = Reassembler()
        XCTAssertNil(try r.feed(frame(0, 2, Data(repeating: 1, count: GearanLink.maxFramePayload))))
        XCTAssertNil(try r.feed(frame(0, 2, Data([2]))))
        XCTAssertEqual(try r.feed(frame(1, 2, Data([3]))), Data([2, 3]))

        let max = Reassembler()
        var result: Data?
        for index in 0..<GearanLink.maxTotalChunks {
            result = try max.feed(frame(UInt16(index), UInt16(GearanLink.maxTotalChunks),
                                        Data(repeating: 4, count: GearanLink.maxFramePayload),
                                        requestId: 8))
        }
        XCTAssertEqual(result, Data(repeating: 4, count: GearanLink.maxMessageBytes))
    }

    func testMismatchClearsBuffer() throws {
        let r = Reassembler()
        XCTAssertNil(try r.feed(frame(0, 2, Data("old".utf8))))
        XCTAssertThrowsError(try r.feed(frame(1, 3, Data("bad".utf8))))
        XCTAssertNil(try r.feed(frame(1, 2, Data("new-1".utf8))))
        XCTAssertEqual(try r.feed(frame(0, 2, Data("new-0".utf8))), Data("new-0new-1".utf8))
    }
}
