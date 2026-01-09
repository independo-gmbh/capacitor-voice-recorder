import XCTest
@testable import VoiceRecorder

final class VoiceRecorderServiceStartTests: XCTestCase {
    func testStartRecordingThrowsWhenDeviceCannotRecord() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.canRecord = false
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        XCTAssertThrowsError(
            try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                       onInterruptionBegan: {},
                                       onInterruptionEnded: {})
        ) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.deviceCannotVoiceRecord)
        }
    }

    func testStartRecordingThrowsWhenPermissionMissing() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: false
        )

        XCTAssertThrowsError(
            try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                       onInterruptionBegan: {},
                                       onInterruptionEnded: {})
        ) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.missingPermission)
        }
    }

    func testStartRecordingThrowsWhenAlreadyRecording() throws {
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

        XCTAssertThrowsError(
            try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                       onInterruptionBegan: {},
                                       onInterruptionEnded: {})
        ) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.alreadyRecording)
        }
    }

    func testStartRecordingThrowsWhenRecorderFailsToStart() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.startSuccess = false
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        XCTAssertThrowsError(
            try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil),
                                       onInterruptionBegan: {},
                                       onInterruptionEnded: {})
        ) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.deviceCannotVoiceRecord)
        }
    }

    func testStartRecordingSetsInterruptionCallbacks() throws {
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

        XCTAssertNotNil(recorder.onInterruptionBegan)
        XCTAssertNotNil(recorder.onInterruptionEnded)
        XCTAssertEqual(service.getCurrentStatus(), .RECORDING)
    }
}
