import XCTest
@testable import VoiceRecorder

final class VoiceRecorderServicePauseResumeTests: XCTestCase {
    func testPauseRecordingThrowsWhenNotStarted() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        XCTAssertThrowsError(try service.pauseRecording()) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.recordingHasNotStarted)
        }
    }

    func testPauseRecordingReturnsFalseWhenRecorderReturnsFalse() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.pauseResult = false
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil, volumeMetering: false),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        XCTAssertFalse(try service.pauseRecording())
        XCTAssertEqual(service.getCurrentStatus(), .RECORDING)
    }

    func testPauseRecordingUpdatesStatus() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil, volumeMetering: false),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})

        XCTAssertTrue(try service.pauseRecording())
        XCTAssertEqual(service.getCurrentStatus(), .PAUSED)
    }

    func testResumeRecordingThrowsWhenNotStarted() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        XCTAssertThrowsError(try service.resumeRecording()) { error in
            XCTAssertEqual((error as? VoiceRecorderServiceError)?.code, ErrorCodes.recordingHasNotStarted)
        }
    }

    func testResumeRecordingReturnsFalseWhenRecorderReturnsFalse() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.resumeResult = false
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil, volumeMetering: false),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})
        XCTAssertTrue(try service.pauseRecording())

        XCTAssertFalse(try service.resumeRecording())
        XCTAssertEqual(service.getCurrentStatus(), .PAUSED)
    }

    func testResumeRecordingUpdatesStatus() throws {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: true
        )

        try service.startRecording(options: RecordOptions(directory: nil, subDirectory: nil, volumeMetering: false),
                                   onInterruptionBegan: {},
                                   onInterruptionEnded: {})
        XCTAssertTrue(try service.pauseRecording())
        XCTAssertTrue(try service.resumeRecording())

        XCTAssertEqual(service.getCurrentStatus(), .RECORDING)
    }
}
