import XCTest
@testable import GearanCore

final class NetworkRelayTests: XCTestCase {
    func testRequestValidationAcceptsBoundaries() {
        let request = NetworkRelay.Request(
            id: "request",
            method: "POST",
            url: "https://api.example.com/v1",
            bodyB64: Data(repeating: 0x41, count: NetworkRelay.maxBodyBytes).base64EncodedString(),
            timeoutMs: 30_000
        )

        XCTAssertNil(request.validate())
    }

    func testRequestValidationRejectsInvalidTimeouts() {
        XCTAssertEqual(makeRequest(timeoutMs: 0).validate(), "TIMEOUT_INVALID")
        XCTAssertEqual(makeRequest(timeoutMs: -1).validate(), "TIMEOUT_INVALID")
        XCTAssertEqual(makeRequest(timeoutMs: 30_001).validate(), "TIMEOUT_TOO_LARGE")
    }

    func testRequestValidationUsesDecodedBodySize() {
        let invalid = NetworkRelay.Request(
            id: "request",
            method: "POST",
            url: "https://example.com",
            bodyB64: "not base64"
        )
        let oversized = NetworkRelay.Request(
            id: "request",
            method: "POST",
            url: "https://example.com",
            bodyB64: Data(repeating: 0x41, count: NetworkRelay.maxBodyBytes + 1).base64EncodedString()
        )

        XCTAssertEqual(invalid.validate(), "BODY_INVALID_BASE64")
        XCTAssertEqual(oversized.validate(), "TOO_LARGE")
    }

    func testURLValidationRejectsUnsafeDestinations() {
        let unsafeURLs = [
            "http://example.com",
            "https://localhost/path",
            "https://service.local/path",
            "https://127.0.0.1/path",
            "https://127.1/path",
            "https://2130706433/path",
            "https://0x7f000001/path",
            "https://10.0.0.1/path",
            "https://[::1]/path",
            "https://user:password@example.com/path",
            "https://example.com/path#fragment"
        ]

        for url in unsafeURLs {
            XCTAssertNotNil(makeRequest(url: url).validate(), url)
        }
    }

    func testAllowlistHostCanonicalizationIsExact() {
        XCTAssertEqual(NetworkRelay.canonicalAllowlistHost("API.Example.COM."), "api.example.com")
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("*.example.com"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("https://example.com"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("example.com:443"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost(" example.com"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("localhost"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("printer.local"))
        XCTAssertNil(NetworkRelay.canonicalAllowlistHost("192.168.1.10"))
    }

    func testEmptyAllowlistDeniesWithoutNetworkRequest() async {
        let (status, data) = await NetworkRelay.execute(makeRequest(), allowedHosts: [])

        XCTAssertEqual(status, 403)
        XCTAssertEqual(String(decoding: data, as: UTF8.self), "{\"reason\":\"HOST_DENIED\"}")
    }

    func testAllowlistDoesNotMatchSubdomains() async {
        let request = makeRequest(url: "https://sub.example.com")
        let (status, _) = await NetworkRelay.execute(request, allowedHosts: ["example.com"])

        XCTAssertEqual(status, 403)
    }

    private func makeRequest(
        url: String = "https://example.com",
        timeoutMs: Int = 15_000
    ) -> NetworkRelay.Request {
        NetworkRelay.Request(id: "request", method: "GET", url: url, timeoutMs: timeoutMs)
    }
}
