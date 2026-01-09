import XCTest
@testable import VoiceRecorder

final class ResponseGeneratorTests: XCTestCase {
    func testFromBoolean() {
        XCTAssertEqual(ResponseGenerator.fromBoolean(true)["value"], true)
        XCTAssertEqual(ResponseGenerator.fromBoolean(false)["value"], false)
    }

    func testSuccessAndFailureResponses() {
        XCTAssertEqual(ResponseGenerator.successResponse()["value"], true)
        XCTAssertEqual(ResponseGenerator.failResponse()["value"], false)
    }

    func testDataResponse() {
        XCTAssertEqual(ResponseGenerator.dataResponse("payload")["value"] as? String, "payload")
    }

    func testStatusResponse() {
        XCTAssertEqual(ResponseGenerator.statusResponse(.RECORDING)["status"], "RECORDING")
    }
}
