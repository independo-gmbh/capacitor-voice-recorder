import XCTest
@testable import VoiceRecorder

final class ResponseFormatTests: XCTestCase {
    func testDefaultsToLegacyForNil() {
        XCTAssertEqual(ResponseFormat.from(value: nil), .legacy)
    }

    func testNormalizesNormalizedCaseInsensitively() {
        XCTAssertEqual(ResponseFormat.from(value: "normalized"), .normalized)
        XCTAssertEqual(ResponseFormat.from(value: "NORMALIZED"), .normalized)
        XCTAssertEqual(ResponseFormat.from(value: "Normalized"), .normalized)
    }

    func testDefaultsToLegacyForUnknownValues() {
        XCTAssertEqual(ResponseFormat.from(value: "legacy"), .legacy)
        XCTAssertEqual(ResponseFormat.from(value: "other"), .legacy)
    }
}
