import AVFoundation
import XCTest
@testable import VoiceRecorder

final class CustomMediaRecorderTests: XCTestCase {
    private enum TestError: Error {
        case sessionFailure
        case recorderFailure
    }

    private final class FakeAudioSession: AudioSessionProtocol {
        var category: AVAudioSession.Category
        var setCategoryCalls: [AVAudioSession.Category] = []
        var setActiveCalls: [Bool] = []
        var setActiveOptionsCalls: [AVAudioSession.SetActiveOptions] = []
        var setCategoryShouldThrow = false
        var setActiveShouldThrow = false

        init(category: AVAudioSession.Category = .ambient) {
            self.category = category
        }

        func setCategory(_ category: AVAudioSession.Category) throws {
            setCategoryCalls.append(category)
            if setCategoryShouldThrow {
                throw TestError.sessionFailure
            }
            self.category = category
        }

        func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws {
            setActiveCalls.append(active)
            setActiveOptionsCalls.append(options)
            if setActiveShouldThrow {
                throw TestError.sessionFailure
            }
        }
    }

    private final class FakeAudioRecorder: AudioRecorderProtocol {
        var isMeteringEnabled = false
        var recordCallCount = 0
        var stopCallCount = 0
        var pauseCallCount = 0
        var updateMetersCallCount = 0
        var averagePower: Float = -6

        func record() -> Bool {
            recordCallCount += 1
            return true
        }

        func stop() {
            stopCallCount += 1
        }

        func pause() {
            pauseCallCount += 1
        }

        func updateMeters() {
            updateMetersCallCount += 1
        }

        func averagePower(forChannel channelNumber: Int) -> Float {
            return averagePower
        }
    }

    private final class AudioRecorderFactorySpy {
        var createdRecorders: [FakeAudioRecorder] = []
        var receivedURLs: [URL] = []
        var shouldThrow = false

        func makeRecorder(url: URL, settings: [String: Any]) throws -> AudioRecorderProtocol {
            if shouldThrow {
                throw TestError.recorderFailure
            }
            receivedURLs.append(url)
            let recorder = FakeAudioRecorder()
            createdRecorders.append(recorder)
            return recorder
        }
    }

    private func postInterruption(
        type: AVAudioSession.InterruptionType,
        session: FakeAudioSession
    ) {
        NotificationCenter.default.post(
            name: AVAudioSession.interruptionNotification,
            object: session,
            userInfo: [AVAudioSessionInterruptionTypeKey: type.rawValue]
        )
    }

    func testGetDirectoryMapsStrings() {
        let recorder = CustomMediaRecorder()

        XCTAssertEqual(recorder.getDirectory(directory: "CACHE"), .cachesDirectory)
        XCTAssertEqual(recorder.getDirectory(directory: "LIBRARY"), .libraryDirectory)
        XCTAssertEqual(recorder.getDirectory(directory: "DOCS"), .documentDirectory)
        XCTAssertNil(recorder.getDirectory(directory: nil))
    }

    func testStartRecordingUsesDirectoryAndStartsRecorder() {
        let session = FakeAudioSession(category: .soloAmbient)
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )
        let options = RecordOptions(directory: "CACHE", subDirectory: "voice-tests/")

        XCTAssertTrue(recorder.startRecording(recordOptions: options))
        XCTAssertEqual(recorder.getCurrentStatus(), .RECORDING)
        XCTAssertEqual(factory.createdRecorders.count, 1)
        XCTAssertEqual(factory.createdRecorders.first?.recordCallCount, 1)
        XCTAssertEqual(factory.createdRecorders.first?.isMeteringEnabled, true)
        XCTAssertEqual(session.setCategoryCalls.first, .playAndRecord)
        XCTAssertEqual(session.setActiveCalls.first, true)

        let outputFile = recorder.getOutputFile()
        let expectedDirectory = FileManager.default
            .urls(for: .cachesDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("voice-tests", isDirectory: true)
        XCTAssertTrue(outputFile.path.hasPrefix(expectedDirectory.path))
        XCTAssertEqual(outputFile.pathExtension, "m4a")
    }

    func testStartRecordingReturnsFalseWhenRecorderFactoryThrows() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        factory.shouldThrow = true
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertFalse(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        XCTAssertEqual(recorder.getCurrentStatus(), .NONE)
    }

    func testPauseRecordingReturnsFalseWhenNotRecording() {
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { FakeAudioSession() },
            audioRecorderFactory: AudioRecorderFactorySpy().makeRecorder
        )

        XCTAssertFalse(recorder.pauseRecording())
    }

    func testGetCurrentAmplitudeReturnsZeroWhenNotRecording() {
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { FakeAudioSession() },
            audioRecorderFactory: AudioRecorderFactorySpy().makeRecorder
        )

        XCTAssertEqual(recorder.getCurrentAmplitude(), 0)
    }

    func testGetCurrentAmplitudeUsesAveragePowerWhileRecording() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        factory.createdRecorders.first?.averagePower = -6

        XCTAssertEqual(recorder.getCurrentAmplitude(), pow(10, -6.0 / 20.0), accuracy: 0.0001)
        XCTAssertEqual(factory.createdRecorders.first?.updateMetersCallCount, 1)
    }

    func testGetCurrentAmplitudeReturnsZeroForSilenceFloor() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        factory.createdRecorders.first?.averagePower = -160

        XCTAssertEqual(recorder.getCurrentAmplitude(), 0)
    }

    func testGetCurrentAmplitudeClampsAboveMaximum() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        factory.createdRecorders.first?.averagePower = 6

        XCTAssertEqual(recorder.getCurrentAmplitude(), 1)
    }

    func testPauseRecordingUpdatesStatusAndCallsPause() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        XCTAssertTrue(recorder.pauseRecording())

        XCTAssertEqual(factory.createdRecorders.first?.pauseCallCount, 1)
        XCTAssertEqual(recorder.getCurrentStatus(), .PAUSED)
    }

    func testResumeRecordingFromPausedReusesRecorder() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        XCTAssertTrue(recorder.pauseRecording())
        XCTAssertTrue(recorder.resumeRecording())

        XCTAssertEqual(factory.createdRecorders.count, 1)
        XCTAssertEqual(factory.createdRecorders.first?.recordCallCount, 2)
        XCTAssertEqual(session.setActiveCalls.last, true)
        XCTAssertEqual(recorder.getCurrentStatus(), .RECORDING)
    }

    func testResumeRecordingReturnsFalseWhenNotPausedOrInterrupted() {
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { FakeAudioSession() },
            audioRecorderFactory: AudioRecorderFactorySpy().makeRecorder
        )

        XCTAssertFalse(recorder.resumeRecording())
    }

    func testInterruptionBeganStopsRecorderAndUpdatesStatus() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )
        let expectation = expectation(description: "interruption began")

        recorder.onInterruptionBegan = {
            expectation.fulfill()
        }

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        postInterruption(type: .began, session: session)

        wait(for: [expectation], timeout: 1.0)
        XCTAssertEqual(factory.createdRecorders.first?.stopCallCount, 1)
        XCTAssertEqual(recorder.getCurrentStatus(), .INTERRUPTED)
    }

    func testInterruptionEndedCallsCallbackWhenInterrupted() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )
        let beganExpectation = expectation(description: "interruption began")
        let endedExpectation = expectation(description: "interruption ended")

        recorder.onInterruptionBegan = {
            beganExpectation.fulfill()
        }
        recorder.onInterruptionEnded = {
            endedExpectation.fulfill()
        }

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        postInterruption(type: .began, session: session)
        wait(for: [beganExpectation], timeout: 1.0)

        postInterruption(type: .ended, session: session)
        wait(for: [endedExpectation], timeout: 1.0)

        XCTAssertEqual(recorder.getCurrentStatus(), .INTERRUPTED)
    }

    func testResumeRecordingFromInterruptedCreatesNewSegmentRecorder() {
        let session = FakeAudioSession()
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )
        let interruptionExpectation = expectation(description: "interruption began")

        recorder.onInterruptionBegan = {
            interruptionExpectation.fulfill()
        }

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))
        postInterruption(type: .began, session: session)
        wait(for: [interruptionExpectation], timeout: 1.0)

        XCTAssertTrue(recorder.resumeRecording())
        XCTAssertEqual(factory.createdRecorders.count, 2)
        XCTAssertEqual(factory.createdRecorders.last?.recordCallCount, 1)
        guard let lastPathComponent = factory.receivedURLs.last?.lastPathComponent else {
            XCTFail("Expected a second segment URL")
            return
        }
        XCTAssertTrue(lastPathComponent.contains("segment-1"))
        XCTAssertEqual(recorder.getCurrentStatus(), .RECORDING)
    }

    func testStopRecordingResetsSessionAndStatus() {
        let session = FakeAudioSession(category: .playback)
        let factory = AudioRecorderFactorySpy()
        let recorder = CustomMediaRecorder(
            audioSessionProvider: { session },
            audioRecorderFactory: factory.makeRecorder
        )
        let expectation = expectation(description: "stop completion")

        XCTAssertTrue(recorder.startRecording(recordOptions: RecordOptions(directory: nil, subDirectory: nil)))

        recorder.stopRecording { success in
            XCTAssertTrue(success)
            expectation.fulfill()
        }

        wait(for: [expectation], timeout: 1.0)
        XCTAssertEqual(recorder.getCurrentStatus(), .NONE)
        XCTAssertEqual(factory.createdRecorders.first?.stopCallCount, 1)
        XCTAssertEqual(session.setActiveCalls.last, false)
        XCTAssertEqual(session.setCategoryCalls.last, .playback)
    }
}
