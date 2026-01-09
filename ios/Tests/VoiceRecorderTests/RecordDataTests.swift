import XCTest
@testable import VoiceRecorder

final class RecordDataTests: XCTestCase {
    func testToDictionaryIncludesEmptyStringsWhenNil() {
        let recordData = RecordData(recordDataBase64: nil, mimeType: "audio/aac", msDuration: 100, uri: nil)

        let payload = recordData.toDictionary()

        XCTAssertEqual(payload["recordDataBase64"] as? String, "")
        XCTAssertEqual(payload["mimeType"] as? String, "audio/aac")
        XCTAssertEqual(payload["msDuration"] as? Int, 100)
        XCTAssertEqual(payload["uri"] as? String, "")
    }
}
