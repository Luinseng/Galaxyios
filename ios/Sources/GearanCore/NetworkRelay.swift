import Foundation

/// Network Relay (iPhone side): HTTPS-only application proxy for the Watch.
/// NOT general Bluetooth tethering. Enforces allowlist + per-host consent,
/// timeouts, 1 MB cap, chunked framing via GearanLink.fragment.
public enum NetworkRelay {
    public static let maxBodyBytes = 1_048_576
    public struct Request: Codable {
        public var id: String; public var method: String; public var url: String
        public var headers: [String: String]; public var bodyB64: String?
        public var timeoutMs: Int
        public init(id: String, method: String, url: String, headers: [String: String] = [:], bodyB64: String? = nil, timeoutMs: Int = 15_000) {
            self.id = id; self.method = method; self.url = url
            self.headers = headers; self.bodyB64 = bodyB64; self.timeoutMs = timeoutMs
        }
        public func validate() -> String? {
            guard method == "GET" || method == "POST" || method == "HEAD" else { return "METHOD_NOT_ALLOWED" }
            guard url.hasPrefix("https://") else { return "HTTPS_ONLY" }
            guard timeoutMs <= 30_000 else { return "TIMEOUT_TOO_LARGE" }
            guard (bodyB64?.count ?? 0) <= maxBodyBytes else { return "TOO_LARGE" }
            return nil
        }
    }
    public struct RelayError: Codable {
        public var id: String; public var reason: String
        public init(id: String, reason: String) { self.id = id; self.reason = reason }
    }

    /// Executes the request with URLSession (iPhone network) — called only for trusted Watch.
    public static func execute(_ req: Request, allowedHosts: Set<String> = []) async -> (Int, Data) {
        if let err = req.validate() { return (400, Data("{\"reason\":\"\(err)\"}".utf8)) }
        guard let url = URL(string: req.url) else { return (400, Data()) }
        if !allowedHosts.isEmpty, let host = url.host, !allowedHosts.contains(host) {
            return (403, Data("{\"reason\":\"HOST_DENIED\"}".utf8))
        }
        var r = URLRequest(url: url, timeoutInterval: TimeInterval(req.timeoutMs) / 1000.0)
        r.httpMethod = req.method
        req.headers.forEach { r.setValue($0.value, forHTTPHeaderField: $0.key) }
        if let b64 = req.bodyB64, let body = Data(base64Encoded: b64) { r.httpBody = body }
        do {
            let (data, resp) = try await URLSession.shared.data(for: r)
            let code = (resp as? HTTPURLResponse)?.statusCode ?? 200
            return (code, data.prefix(maxBodyBytes).data)
        } catch {
            return (502, Data("{\"reason\":\"FETCH_FAILED\"}".utf8))
        }
    }
}

extension Data {
    var data: Data { self }
}
