import Foundation

/// HTTPS-only application proxy used by a trusted Watch.
public enum NetworkRelay {
    public static let maxBodyBytes = 1_048_576

    public struct Request: Codable {
        public var id: String
        public var method: String
        public var url: String
        public var headers: [String: String]
        public var bodyB64: String?
        public var timeoutMs: Int

        public init(
            id: String,
            method: String,
            url: String,
            headers: [String: String] = [:],
            bodyB64: String? = nil,
            timeoutMs: Int = 15_000
        ) {
            self.id = id
            self.method = method
            self.url = url
            self.headers = headers
            self.bodyB64 = bodyB64
            self.timeoutMs = timeoutMs
        }

        public func validate() -> String? {
            guard ["GET", "POST", "HEAD"].contains(method) else {
                return "METHOD_NOT_ALLOWED"
            }
            guard NetworkRelay.validatedURL(url) != nil else {
                return "HTTPS_ONLY"
            }
            guard timeoutMs > 0 else { return "TIMEOUT_INVALID" }
            guard timeoutMs <= 30_000 else { return "TIMEOUT_TOO_LARGE" }

            if let bodyB64 {
                // Avoid allocating an arbitrarily large decoded buffer first.
                let largestEncodedBody = ((NetworkRelay.maxBodyBytes + 2) / 3) * 4
                guard bodyB64.utf8.count <= largestEncodedBody else { return "TOO_LARGE" }
                guard let body = Data(base64Encoded: bodyB64) else {
                    return "BODY_INVALID_BASE64"
                }
                guard body.count <= NetworkRelay.maxBodyBytes else { return "TOO_LARGE" }
            }
            return nil
        }
    }

    public struct RelayError: Codable {
        public var id: String
        public var reason: String

        public init(id: String, reason: String) {
            self.id = id
            self.reason = reason
        }
    }

    /// Executes a bounded request. An empty or invalid allowlist denies every host.
    public static func execute(
        _ req: Request,
        allowedHosts: Set<String> = []
    ) async -> (Int, Data) {
        if let error = req.validate() {
            return (400, errorBody(error))
        }
        guard let url = validatedURL(req.url), let host = url.host else {
            return (400, errorBody("HTTPS_ONLY"))
        }

        let normalizedAllowedHosts = Set(allowedHosts.compactMap(canonicalAllowlistHost))
        guard !normalizedAllowedHosts.isEmpty,
              let normalizedHost = canonicalHost(host),
              normalizedAllowedHosts.contains(normalizedHost) else {
            return (403, errorBody("HOST_DENIED"))
        }

        var request = URLRequest(
            url: url,
            timeoutInterval: TimeInterval(req.timeoutMs) / 1_000.0
        )
        request.httpMethod = req.method
        req.headers.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        if let bodyB64 = req.bodyB64 {
            request.httpBody = Data(base64Encoded: bodyB64)
        }

        let loader = BoundedLoader(
            allowedHosts: normalizedAllowedHosts,
            timeout: TimeInterval(req.timeoutMs) / 1_000.0
        )
        return await loader.load(request)
    }

    static func canonicalAllowlistHost(_ value: String) -> String? {
        // Allowlist entries are hostnames, never URLs, host:port pairs, or wildcards.
        guard value == value.trimmingCharacters(in: .whitespacesAndNewlines),
              !value.contains("/"), !value.contains(":"), !value.contains("*") else {
            return nil
        }
        return canonicalHost(value)
    }

    static func validatedURL(_ value: String) -> URL? {
        guard let components = URLComponents(string: value),
              components.scheme?.lowercased() == "https",
              components.user == nil,
              components.password == nil,
              components.fragment == nil,
              let host = components.host,
              canonicalHost(host) != nil,
              let url = components.url else {
            return nil
        }
        return url
    }

    static func canonicalHost(_ value: String) -> String? {
        var host = value.lowercased()
        if host.hasSuffix(".") { host.removeLast() }
        guard !host.isEmpty, host.utf8.count <= 253,
              host != "localhost", !host.hasSuffix(".localhost"),
              host != "local", !host.hasSuffix(".local"),
              !isIPLiteral(host) else {
            return nil
        }

        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        guard labels.allSatisfy({ label in
            guard !label.isEmpty, label.utf8.count <= 63,
                  label.first != "-", label.last != "-" else { return false }
            return label.utf8.allSatisfy {
                ($0 >= 97 && $0 <= 122) || ($0 >= 48 && $0 <= 57) || $0 == 45
            }
        }) else {
            return nil
        }
        return host
    }

    private static func isIPLiteral(_ host: String) -> Bool {
        // Colons cover IPv6. Also reject legacy shortened, decimal, and hex IPv4 forms.
        if host.contains(":") { return true }
        let labels = host.split(separator: ".", omittingEmptySubsequences: false)
        let numericAddress = labels.count <= 4
            && labels.allSatisfy { label in
                !label.isEmpty && label.allSatisfy { $0.isNumber }
            }
        let hexadecimalAddress = labels.count == 1
            && host.hasPrefix("0x")
            && host.dropFirst(2).allSatisfy { $0.isHexDigit }
        return numericAddress || hexadecimalAddress
    }

    private static func errorBody(_ reason: String) -> Data {
        Data("{\"reason\":\"\(reason)\"}".utf8)
    }

    private final class BoundedLoader: NSObject, URLSessionDataDelegate, URLSessionTaskDelegate {
        private let allowedHosts: Set<String>
        private let timeout: TimeInterval
        private var responseCode = 200
        private var received = Data()
        private var terminalReason: String?
        private var continuation: CheckedContinuation<(Int, Data), Never>?
        private var session: URLSession?

        init(allowedHosts: Set<String>, timeout: TimeInterval) {
            self.allowedHosts = allowedHosts
            self.timeout = timeout
        }

        func load(_ request: URLRequest) async -> (Int, Data) {
            await withCheckedContinuation { continuation in
                self.continuation = continuation
                let configuration = URLSessionConfiguration.ephemeral
                configuration.timeoutIntervalForRequest = timeout
                configuration.timeoutIntervalForResource = timeout
                configuration.httpShouldSetCookies = false
                configuration.urlCache = nil

                let queue = OperationQueue()
                queue.maxConcurrentOperationCount = 1
                let session = URLSession(
                    configuration: configuration,
                    delegate: self,
                    delegateQueue: queue
                )
                self.session = session
                session.dataTask(with: request).resume()
            }
        }

        func urlSession(
            _ session: URLSession,
            dataTask: URLSessionDataTask,
            didReceive response: URLResponse,
            completionHandler: @escaping (URLSession.ResponseDisposition) -> Void
        ) {
            responseCode = (response as? HTTPURLResponse)?.statusCode ?? 200
            if response.expectedContentLength > Int64(NetworkRelay.maxBodyBytes) {
                terminalReason = "TOO_LARGE"
                completionHandler(.cancel)
            } else {
                completionHandler(.allow)
            }
        }

        func urlSession(
            _ session: URLSession,
            dataTask: URLSessionDataTask,
            didReceive data: Data
        ) {
            guard terminalReason == nil else { return }
            guard data.count <= NetworkRelay.maxBodyBytes - received.count else {
                terminalReason = "TOO_LARGE"
                dataTask.cancel()
                return
            }
            received.append(data)
        }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            willPerformHTTPRedirection response: HTTPURLResponse,
            newRequest request: URLRequest,
            completionHandler: @escaping (URLRequest?) -> Void
        ) {
            guard let redirectedURL = request.url,
                  NetworkRelay.validatedURL(redirectedURL.absoluteString) != nil,
                  let host = redirectedURL.host,
                  let normalizedHost = NetworkRelay.canonicalHost(host),
                  allowedHosts.contains(normalizedHost) else {
                terminalReason = "HOST_DENIED"
                completionHandler(nil)
                return
            }
            completionHandler(request)
        }

        func urlSession(
            _ session: URLSession,
            task: URLSessionTask,
            didCompleteWithError error: Error?
        ) {
            let result: (Int, Data)
            if let reason = terminalReason {
                result = (reason == "TOO_LARGE" ? 413 : 403, NetworkRelay.errorBody(reason))
            } else if error != nil {
                result = (502, NetworkRelay.errorBody("FETCH_FAILED"))
            } else {
                result = (responseCode, received)
            }
            continuation?.resume(returning: result)
            continuation = nil
            session.finishTasksAndInvalidate()
            self.session = nil
        }
    }
}
