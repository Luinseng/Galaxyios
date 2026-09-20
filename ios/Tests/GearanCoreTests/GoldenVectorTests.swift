import XCTest
import CryptoKit
@testable import GearanCore

/// Cross-language golden vectors (tests/vectors/golden_vectors.json).
/// Same file asserted by Python (test_vectors.py) and Kotlin (GoldenVectorTest).
final class GoldenVectorTests: XCTestCase {
    func vectors() throws -> [String: Any] {
        // Deterministic: vectors ride with the test bundle (see vectors/ copy,
        // synced from tests/vectors by tests/vectors/make_vectors.py).
        guard let url = Bundle.module.url(forResource: "golden_vectors", withExtension: "json", subdirectory: "vectors"),
              let d = try? Data(contentsOf: url),
              let j = try? JSONSerialization.jsonObject(with: d) as? [String: Any] else {
            throw NSError(domain: "vectors", code: 1, userInfo: [NSLocalizedDescriptionKey: "golden_vectors.json not found in test bundle"])
        }
        return j
    }

    func hex(_ s: String) -> Data {
        var d = Data()
        var i = s.startIndex
        while i < s.endIndex {
            let j = s.index(i, offsetBy: 2)
            d.append(UInt8(s[i..<j], radix: 16)!)
            i = j
        }
        return d
    }

    func testFramePingVector() throws {
        let v = try vectors()["frame_ping"] as! [String: Any]
        let key = hex(v["authKeyHex"] as! String)
        let payload = Data(base64Encoded: v["payloadB64"] as! String)!
        let f = GearanLink.Frame(messageType: UInt8(v["messageType"] as! Int),
                                 flags: UInt16(v["flags"] as! Int),
                                 requestId: UInt32(v["requestId"] as! Int),
                                 sequence: UInt32(v["sequence"] as! Int),
                                 chunkIndex: 0, totalChunks: 1, payload: payload)
        let raw = try GearanLink.encode(f, authKey: key)
        XCTAssertEqual(raw.map { String(format: "%02x", Int($0)) }.joined(), v["encodedHex"] as! String)
    }

    func testHkdfSessionVector() throws {
        let v = try vectors()["hkdf_session"] as! [String: Any]
        let okm = GearanLink.hkdf(ikm: hex(v["ikmHex"] as! String),
                                  salt: Data((v["saltAscii"] as! String).utf8),
                                  info: Data((v["infoAscii"] as! String).utf8),
                                  length: v["length"] as! Int)
        XCTAssertEqual(okm.map { String(format: "%02x", Int($0)) }.joined(), v["okmHex"] as! String)
    }

    func testSasVector() throws {
        let v = try vectors()["sas"] as! [String: Any]
        XCTAssertEqual(GearanLink.sas(transcript: hex(v["transcriptHex"] as! String)), v["sas"] as! String)
    }

    func testAesGcmNistCase2() throws {
        // NIST SP 800-38D D.2 case 2 decrypt: K=0^128, nonce=0^96.
        let key = SymmetricKey(data: Data(repeating: 0, count: 16))
        let nonce = try AES.GCM.Nonce(data: Data(repeating: 0, count: 12))
        let ct = hex("0388dace60b6a392f328c2b971b2fe78")
        let tag = hex("ab6e47d42cec13bdf53a67b21257bddf")
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: ct, tag: tag)
        let pt = try AES.GCM.open(box, using: key)
        XCTAssertEqual(pt, Data(repeating: 0, count: 16))
    }
}
