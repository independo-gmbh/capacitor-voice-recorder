import XCTest
@testable import VoiceRecorder

final class RecordDataMapperTests: XCTestCase {
    func testLegacyDictionaryUsesEmptyStrings() {
        let recordData = RecordData(recordDataBase64: nil, mimeType: "audio/aac", fileExtension: "aac", msDuration: 500, uri: nil)

        let payload = RecordDataMapper.toLegacyDictionary(recordData)

        XCTAssertEqual(payload["recordDataBase64"] as? String, "")
        XCTAssertEqual(payload["mimeType"] as? String, "audio/aac")
        XCTAssertEqual(payload["fileExtension"] as? String, "aac")
        XCTAssertEqual(payload["msDuration"] as? Int, 500)
        XCTAssertEqual(payload["uri"] as? String, "")
    }

    func testNormalizedPrefersUriAndNormalizesFilePath() {
        let recordData = RecordData(recordDataBase64: "BASE64", mimeType: "audio/aac", fileExtension: "aac", msDuration: 500, uri: "/tmp/recording.aac")

        let payload = RecordDataMapper.toNormalizedDictionary(recordData)

        XCTAssertEqual(payload["uri"] as? String, "file:///tmp/recording.aac")
        XCTAssertEqual(payload["fileExtension"] as? String, "aac")
        XCTAssertNil(payload["recordDataBase64"])
    }

    func testNormalizedKeepsFileScheme() {
        let recordData = RecordData(recordDataBase64: "BASE64", mimeType: "audio/aac", fileExtension: "aac", msDuration: 500, uri: "file:///tmp/recording.aac")

        let payload = RecordDataMapper.toNormalizedDictionary(recordData)

        XCTAssertEqual(payload["uri"] as? String, "file:///tmp/recording.aac")
        XCTAssertEqual(payload["fileExtension"] as? String, "aac")
        XCTAssertNil(payload["recordDataBase64"])
    }

    func testNormalizedUsesBase64WhenUriMissing() {
        let recordData = RecordData(recordDataBase64: "BASE64", mimeType: "audio/aac", fileExtension: "aac", msDuration: 500, uri: nil)

        let payload = RecordDataMapper.toNormalizedDictionary(recordData)

        XCTAssertEqual(payload["recordDataBase64"] as? String, "BASE64")
        XCTAssertEqual(payload["fileExtension"] as? String, "aac")
        XCTAssertNil(payload["uri"])
    }

    func testNormalizedOmitsEmptyBase64() {
        let recordData = RecordData(recordDataBase64: "", mimeType: "audio/aac", fileExtension: "aac", msDuration: 500, uri: nil)

        let payload = RecordDataMapper.toNormalizedDictionary(recordData)

        XCTAssertNil(payload["recordDataBase64"])
        XCTAssertEqual(payload["fileExtension"] as? String, "aac")
        XCTAssertNil(payload["uri"])
    }
}
