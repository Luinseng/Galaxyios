import Foundation
import CryptoKit

/// Gearan Link Protocol 0.1 framing. Byte-identical to wear/LinkFrameCodec.kt
/// and shared/gearan_link.py. Header 20 B big-endian + HMAC-SHA256 32 B.
public enum GearanLink {
    public static let magic0: UInt8 = 0x47 // "G"
    public static let magic1: UInt8 = 0x52 // "R"
    public static let version: UInt8 = 0x01
    public static let headerLen = 20
    public static let authLen = 32
    public static let maxFramePayload = 512
    public static let maxMessageBytes = 64 * 1024

    public static let serviceUUID = "6E400001-8A21-4A11-9B5C-F3F2A1E4B001"
    public static let rxUUID = "6E400002-8A21-4A11-9B5C-F3F2A1E4B001"
    public static let txUUID = "6E400003-8A21-4A11-9B5C-F3F2A1E4B001"
    public static let ctrlUUID = "6E400004-8A21-4A11-9B5C-F3F2A1E4B001"

    // flags
    public static let flagAckReq: UInt16 = 0x0001
    public static let flagAck: UInt16 = 0x0002
    public static let flagEnc: UInt16 = 0x0004
    public static let flagFrag: UInt16 = 0x0008
    public static let flagRetry: UInt16 = 0x0010
    public static let flagErr: UInt16 = 0x8000

    // message types
    public static let mtAck = 0x01
    public static let mtError = 0x02
    public static let mtPing = 0x03
    public static let mtPong = 0x04
    public static let mtPairHello = 0x05
    public static let mtPairEphemeral = 0x06
    public static let mtPairAuth = 0x07
    public static let mtPairCommit = 0x08
    public static let mtPairConfirm = 0x09
    public static let mtPairAbort = 0x0A
    public static let mtCapsRequest = 0x0B
    public static let mtCapsResponse = 0x0C
    public static let mtSyncStart = 0x0D
    public static let mtSyncData = 0x0E
    public static let mtSyncDone = 0x0F
    public static let mtBattery = 0x10
    public static let mtDeviceInfo = 0x11
    public static let mtNetRequest = 0x12
    public static let mtNetResponse = 0x13
    public static let mtNetError = 0x14
    public static let mtNetCancel = 0x15
    public static let mtNotifPost = 0x16
    public static let mtHealthSample = 0x18
    public static let mtDiagReq = 0x19
    public static let mtDiagResp = 0x1A
    public static let mtDisconnect = 0x1E

    public enum CodecError: Error {
        case tooShort, badMagic, badVersion, badChunks, tooLarge, truncated, authFailure, tooManyConcurrent, chunkMismatch, messageTooLarge
    }

    public struct Frame: Equatable {
        public var messageType: UInt8
        public var flags: UInt16
        public var requestId: UInt32
        public var sequence: UInt32
        public var chunkIndex: UInt16
        public var totalChunks: UInt16
        public var payload: Data
        public init(messageType: UInt8, flags: UInt16, requestId: UInt32, sequence: UInt32, chunkIndex: UInt16, totalChunks: UInt16, payload: Data) {
            self.messageType = messageType; self.flags = flags
            self.requestId = requestId; self.sequence = sequence
            self.chunkIndex = chunkIndex; self.totalChunks = totalChunks
            self.payload = payload
        }
    }

    static func hmac(key: Data, header: Data, payload: Data) -> Data {
        let k = SymmetricKey(data: key)
        var m = HMAC<SHA256>(key: k)
        m.update(data: header); m.update(data: payload)
        return Data(m.finalize())
    }

    public static func encode(_ f: Frame, authKey: Data) throws -> Data {
        guard f.payload.count <= maxFramePayload else { throw CodecError.tooLarge }
        guard f.totalChunks >= 1 else { throw CodecError.badChunks }
        var h = Data(capacity: headerLen)
        h.append(contentsOf: [magic0, magic1, version, f.messageType])
        h.appendUInt16BE(f.flags)
        h.appendUInt32BE(f.requestId)
        h.appendUInt32BE(f.sequence)
        h.appendUInt16BE(f.chunkIndex)
        h.appendUInt16BE(f.totalChunks)
        h.appendUInt16BE(UInt16(f.payload.count))
        let auth = hmac(key: authKey, header: h, payload: f.payload)
        return h + f.payload + auth
    }

    public static func decode(_ data: Data, authKey: Data) throws -> Frame {
        guard data.count >= headerLen + authLen else { throw CodecError.tooShort }
        let h = data.prefix(headerLen)
        let b = [UInt8](h)
        guard b[0] == magic0 && b[1] == magic1 else { throw CodecError.badMagic }
        guard b[2] == version else { throw CodecError.badVersion }
        let type = b[3]
        let flags = (UInt16(b[4]) << 8) | UInt16(b[5])
        let req = (UInt32(b[6]) << 24) | (UInt32(b[7]) << 16) | (UInt32(b[8]) << 8) | UInt32(b[9])
        let seq = (UInt32(b[10]) << 24) | (UInt32(b[11]) << 16) | (UInt32(b[12]) << 8) | UInt32(b[13])
        let cidx = (UInt16(b[14]) << 8) | UInt16(b[15])
        let total = (UInt16(b[16]) << 8) | UInt16(b[17])
        let plen = Int((UInt16(b[18]) << 8) | UInt16(b[19]))
        guard total >= 1 && cidx < total else { throw CodecError.badChunks }
        guard plen <= maxFramePayload else { throw CodecError.tooLarge }
        guard data.count >= headerLen + plen + authLen else { throw CodecError.truncated }
        let payload = data.subdata(in: headerLen..<(headerLen + plen))
        let auth = data.subdata(in: (headerLen + plen)..<(headerLen + plen + authLen))
        let expected = hmac(key: authKey, header: Data(h), payload: payload)
        guard auth == expected else { throw CodecError.authFailure }
        return Frame(messageType: type, flags: flags, requestId: req, sequence: seq, chunkIndex: cidx, totalChunks: total, payload: payload)
    }

    public static func fragment(messageType: UInt8, payload: Data, requestId: UInt32, sequence: UInt32, flags: UInt16 = 0) throws -> [Frame] {
        guard payload.count <= maxMessageBytes else { throw CodecError.messageTooLarge }
        if (payload.isEmpty) { return [Frame(messageType: messageType, flags: flags, requestId: requestId, sequence: sequence, chunkIndex: 0, totalChunks: 1, payload: Data())] }
        var out: [Frame] = []
        let total = (payload.count + maxFramePayload - 1) / maxFramePayload
        for i in 0..<total {
            let s = payload.subdata(in: (i * maxFramePayload)..<min((i + 1) * maxFramePayload, payload.count))
            let f = total > 1 ? (flags | flagFrag) : flags
            out.append(Frame(messageType: messageType, flags: f, requestId: requestId, sequence: sequence, chunkIndex: UInt16(i), totalChunks: UInt16(total), payload: s))
        }
        return out
    }

    /// SAS: 6 digits from SHA256(transcript). Human anti-MITM only, never a key.
    public static func sas(transcript: Data) -> String {
        let d = SHA256.hash(data: transcript)
        let v = (UInt32(d[d.startIndex]) << 24) | (UInt32(d[d.startIndex + 1]) << 16) | (UInt32(d[d.startIndex + 2]) << 8) | UInt32(d[d.startIndex + 3])
        return String(format: "%06d", v % 1_000_000)
    }

    public static func hkdf(ikm: Data, salt: Data, info: Data, length: Int = 32) -> Data {
        let prk = hmac(key: salt, header: ikm, payload: Data())
        var okm = Data(); var t = Data(); var c: UInt8 = 1
        while (okm.count < length) {
            var m = HMAC<SHA256>(key: SymmetricKey(data: prk))
            m.update(data: t); m.update(data: info); m.update(data: Data([c]))
            t = Data(m.finalize()); okm.append(t); c += 1
        }
        return okm.prefix(length)
    }
}

extension Data {
    mutating func appendUInt16BE(_ v: UInt16) { append(UInt8(v >> 8)); append(UInt8(v & 0xFF)) }
    mutating func appendUInt32BE(_ v: UInt32) {
        append(UInt8((v >> 24) & 0xFF)); append(UInt8((v >> 16) & 0xFF))
        append(UInt8((v >> 8) & 0xFF)); append(UInt8(v & 0xFF))
    }
}

/// Sliding replay window (128 entries).
public final class ReplayWindow {
    private let size: UInt32 = 128
    private var maxSeq: Int64 = -1
    private var seen = Set<UInt32>()
    public init() {}
    public func accept(_ seq: UInt32) -> Bool {
        if (seen.contains(seq)) { return false }
        let s = Int64(seq)
        if (s > maxSeq) {
            maxSeq = s; seen.insert(seq)
            let cutoff = maxSeq - Int64(size)
            seen = seen.filter { Int64($0) > cutoff }
            return true
        }
        if (s <= maxSeq - Int64(size)) { return false }
        seen.insert(seq); return true
    }
}

/// Fragment reassembly with timeout + concurrency cap.
public final class Reassembler {
    struct Key: Hashable { var req: UInt32; var type: UInt8 }
    struct Entry { var total: UInt16; var chunks: [UInt16: Data]; var ts: Date }
    private var buffers: [Key: Entry] = [:]
    public var timeout: TimeInterval = 10
    public var maxConcurrent = 8
    public init() {}
    public func feed(_ f: GearanLink.Frame) throws -> Data? {
        buffers = buffers.filter { Date().timeIntervalSince($0.value.ts) <= timeout }
        if (f.totalChunks == 1) { return f.payload }
        let k = Key(req: f.requestId, type: f.messageType)
        var e = buffers[k]
        if (e == nil) {
            guard buffers.count < maxConcurrent else { throw GearanLink.CodecError.tooManyConcurrent }
            e = Entry(total: f.totalChunks, chunks: [:], ts: Date())
            buffers[k] = e
        }
        guard e!.total == f.totalChunks else { throw GearanLink.CodecError.chunkMismatch }
        e!.chunks[f.chunkIndex] = f.payload; e!.ts = Date()
        buffers[k] = e
        if (e!.chunks.count == Int(e!.total)) {
            var out = Data()
            for i in 0..<e!.total { out.append(e!.chunks[i]!) }
            buffers.removeValue(forKey: k)
            return out
        }
        return nil
    }
}
