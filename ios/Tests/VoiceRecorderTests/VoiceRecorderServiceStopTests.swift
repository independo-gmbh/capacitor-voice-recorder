import XCTest
@testable import VoiceRecorder

final class VoiceRecorderServiceStopTests: XCTestCase {
    func testStopRecordingFailsWhenNotStarted() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .failure(let error):
                XCTAssertEqual(error.code, ErrorCodes.recordingHasNotStarted)
            case .success:
                XCTFail("Expected stopRecording to fail when not started")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingFailsWhenMergeFails() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.stopSuccess = false
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .failure(let error):
                XCTAssertEqual(error.code, ErrorCodes.failedToMergeRecording)
            case .success:
                XCTFail("Expected stopRecording to fail when merge fails")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingReturnsBase64Payload() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.base64Payload = "BASE64"
        platform.durationMs = 1500
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.outputFile = URL(fileURLWithPath: "/tmp/recording.aac")
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .success(let recordData):
                XCTAssertEqual(recordData.recordDataBase64, "BASE64")
                XCTAssertEqual(recordData.mimeType, "audio/aac")
                XCTAssertEqual(recordData.msDuration, 1500)
                XCTAssertNil(recordData.uri)
                XCTAssertTrue(platform.readFileCalled)
            case .failure(let error):
                XCTFail("Unexpected error: \(error)")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingReturnsUriPayloadWhenDirectorySet() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.durationMs = 2000
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.outputFile = URL(fileURLWithPath: "/tmp/recording.aac")
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: "CACHE", subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .success(let recordData):
                XCTAssertNil(recordData.recordDataBase64)
                XCTAssertEqual(recordData.uri, "/tmp/recording.aac")
                XCTAssertEqual(recordData.mimeType, "audio/aac")
                XCTAssertEqual(recordData.msDuration, 2000)
                XCTAssertFalse(platform.readFileCalled)
            case .failure(let error):
                XCTFail("Unexpected error: \(error)")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingMapsM4aToAudioMp4() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.base64Payload = "BASE64"
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.outputFile = URL(fileURLWithPath: "/tmp/recording.m4a")
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .success(let recordData):
                XCTAssertEqual(recordData.mimeType, "audio/mp4")
            case .failure(let error):
                XCTFail("Unexpected error: \(error)")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingFailsWhenPayloadEmpty() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.base64Payload = nil
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .failure(let error):
                XCTAssertEqual(error.code, ErrorCodes.emptyRecording)
            case .success:
                XCTFail("Expected stopRecording to fail for empty payload")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingFailsWhenDurationInvalid() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.durationMs = -1
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { result in
            switch result {
            case .failure(let error):
                XCTAssertEqual(error.code, ErrorCodes.emptyRecording)
            case .success:
                XCTFail("Expected stopRecording to fail for invalid duration")
            }
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)
    }

    func testStopRecordingClearsRecorderAfterSuccess() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        let expectation = XCTestExpectation(description: "stopRecording")
        service.stopRecording { _ in
            expectation.fulfill()
        }
        wait(for: [expectation], timeout: 1)

        XCTAssertEqual(service.getCurrentStatus(), .NONE)
    }
}
