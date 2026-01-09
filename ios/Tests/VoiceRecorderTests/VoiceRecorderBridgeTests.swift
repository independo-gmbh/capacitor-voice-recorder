import AVFoundation
import Capacitor
import XCTest
@testable import VoiceRecorder

final class VoiceRecorderBridgeTests: XCTestCase {
    private final class TestVoiceRecorder: VoiceRecorder {
        private(set) var notifiedEvents: [String] = []

        override func notifyListeners(_ eventName: String, data: [String : Any]?) {
            notifiedEvents.append(eventName)
        }
    }

    private final class PluginCallSpy {
        private final class Storage {
            var resolvedData: [String: Any]?
            var rejected: (message: String, code: String?, error: Error?, data: [String: Any]?)?
        }

        let call: CAPPluginCall
        private let storage: Storage

        init(
            options: [String: Any] = [:],
            methodName: String = "test",
            onResolve: (() -> Void)? = nil,
            onReject: (() -> Void)? = nil
        ) {
            let storage = Storage()
            self.storage = storage
            call = CAPPluginCall(
                callbackId: "callback",
                methodName: methodName,
                options: options,
                success: { result, _ in
                    storage.resolvedData = result?.data
                    onResolve?()
                },
                error: { error in
                    storage.rejected = (
                        message: error?.message ?? "Unknown error",
                        code: error?.code,
                        error: error?.error,
                        data: error?.data
                    )
                    onReject?()
                }
            )
        }

        var resolvedData: [String: Any]? {
            return storage.resolvedData
        }

        var rejected: (message: String, code: String?, error: Error?, data: [String: Any]?)? {
            return storage.rejected
        }

        var resolvedValue: Any? {
            return resolvedData?["value"]
        }

        var resolvedPayload: [String: Any]? {
            return resolvedValue as? [String: Any]
        }
    }

    private func makePlugin(
        platform: VoiceRecorderServiceFixtures.FakePlatform = VoiceRecorderServiceFixtures.FakePlatform(),
        recorder: VoiceRecorderServiceFixtures.FakeRecorder = VoiceRecorderServiceFixtures.FakeRecorder(),
        permissionGranted: Bool = true,
        responseFormat: ResponseFormat = .legacy,
        permissionRequester: PermissionRequester? = nil,
        permissionStatusProvider: PermissionStatusProvider? = nil
    ) -> (TestVoiceRecorder, VoiceRecorderServiceFixtures.FakePlatform, VoiceRecorderServiceFixtures.FakeRecorder) {
        let service = VoiceRecorderServiceFixtures.makeService(
            platform: platform,
            recorder: recorder,
            permissionGranted: permissionGranted
        )
        let plugin = TestVoiceRecorder()
        plugin.configureForTesting(
            service: service,
            responseFormat: responseFormat,
            permissionRequester: permissionRequester,
            permissionStatusProvider: permissionStatusProvider
        )
        return (plugin, platform, recorder)
    }

    func testCanDeviceVoiceRecordResolvesValue() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.canRecord = true
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let (plugin, _, _) = makePlugin(platform: platform, recorder: recorder)
        let callSpy = PluginCallSpy()

        plugin.canDeviceVoiceRecord(callSpy.call)

        XCTAssertEqual(callSpy.resolvedValue as? Bool, true)
        XCTAssertNil(callSpy.rejected)
    }

    func testHasAudioRecordingPermissionResolvesValue() {
        let (plugin, _, _) = makePlugin(permissionGranted: false)
        let callSpy = PluginCallSpy()

        plugin.hasAudioRecordingPermission(callSpy.call)

        XCTAssertEqual(callSpy.resolvedValue as? Bool, false)
        XCTAssertNil(callSpy.rejected)
    }

    func testRequestAudioRecordingPermissionResolvesSuccess() {
        let expectation = expectation(description: "permission resolve")
        let plugin = TestVoiceRecorder()
        plugin.configureForTesting(
            service: nil,
            permissionRequester: { completion in
                completion(true)
            }
        )
        let callSpy = PluginCallSpy(onResolve: { expectation.fulfill() })

        plugin.requestAudioRecordingPermission(callSpy.call)

        wait(for: [expectation], timeout: 1.0)
        XCTAssertEqual(callSpy.resolvedValue as? Bool, true)
        XCTAssertNil(callSpy.rejected)
    }

    func testStartRecordingResolvesAndNotifiesInterruptionEvents() {
        let (plugin, _, recorder) = makePlugin()
        let callSpy = PluginCallSpy(options: [
            "directory": "CACHE",
            "subDirectory": "voice-tests"
        ])

        plugin.startRecording(callSpy.call)

        XCTAssertEqual(callSpy.resolvedValue as? Bool, true)
        XCTAssertEqual(recorder.options?.directory, "CACHE")
        XCTAssertEqual(recorder.options?.subDirectory, "voice-tests")

        recorder.onInterruptionBegan?()
        recorder.onInterruptionEnded?()
        XCTAssertEqual(
            plugin.notifiedEvents,
            ["voiceRecordingInterrupted", "voiceRecordingInterruptionEnded"]
        )
    }

    func testStartRecordingRejectsDeviceCannotRecordWithLegacyMessage() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.canRecord = false
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let (plugin, _, _) = makePlugin(platform: platform, recorder: recorder)
        let callSpy = PluginCallSpy()

        plugin.startRecording(callSpy.call)

        XCTAssertEqual(callSpy.rejected?.message, Messages.CANNOT_RECORD_ON_THIS_PHONE)
        XCTAssertEqual(callSpy.rejected?.code, ErrorCodes.deviceCannotVoiceRecord)
        XCTAssertNil(callSpy.resolvedData)
    }

    func testStopRecordingResolvesLegacyPayload() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.base64Payload = "BASE64"
        platform.durationMs = 1234
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let (plugin, _, _) = makePlugin(platform: platform, recorder: recorder, responseFormat: .legacy)
        let startCall = PluginCallSpy()
        plugin.startRecording(startCall.call)

        let stopExpectation = expectation(description: "stop resolve")
        let stopCall = PluginCallSpy(onResolve: { stopExpectation.fulfill() })

        plugin.stopRecording(stopCall.call)

        wait(for: [stopExpectation], timeout: 1.0)
        guard let payload = stopCall.resolvedPayload else {
            XCTFail("Expected legacy payload")
            return
        }
        XCTAssertEqual(payload["recordDataBase64"] as? String, "BASE64")
        XCTAssertEqual(payload["mimeType"] as? String, "audio/aac")
        XCTAssertEqual(payload["msDuration"] as? Int, 1234)
        XCTAssertEqual(payload["uri"] as? String, "")
        XCTAssertNil(stopCall.rejected)
    }

    func testStopRecordingResolvesNormalizedPayloadWithUri() {
        let platform = VoiceRecorderServiceFixtures.FakePlatform()
        platform.durationMs = 2000
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        let (plugin, _, _) = makePlugin(
            platform: platform,
            recorder: recorder,
            responseFormat: .normalized
        )
        let startCall = PluginCallSpy(options: [
            "directory": "CACHE"
        ])
        plugin.startRecording(startCall.call)

        let stopExpectation = expectation(description: "stop resolve normalized")
        let stopCall = PluginCallSpy(onResolve: { stopExpectation.fulfill() })

        plugin.stopRecording(stopCall.call)

        wait(for: [stopExpectation], timeout: 1.0)
        guard let payload = stopCall.resolvedPayload else {
            XCTFail("Expected normalized payload")
            return
        }
        XCTAssertEqual(payload["msDuration"] as? Int, 2000)
        XCTAssertEqual(payload["mimeType"] as? String, "audio/aac")
        XCTAssertNil(payload["recordDataBase64"])
        let uri = payload["uri"] as? String
        XCTAssertTrue(uri?.hasPrefix("file://") == true)
    }

    func testStopRecordingRejectsOnFailure() {
        let recorder = VoiceRecorderServiceFixtures.FakeRecorder()
        recorder.stopSuccess = false
        let (plugin, _, _) = makePlugin(recorder: recorder)
        let startCall = PluginCallSpy()
        plugin.startRecording(startCall.call)

        let stopExpectation = expectation(description: "stop reject")
        let stopCall = PluginCallSpy(onReject: { stopExpectation.fulfill() })

        plugin.stopRecording(stopCall.call)

        wait(for: [stopExpectation], timeout: 1.0)
        XCTAssertEqual(stopCall.rejected?.code, ErrorCodes.failedToMergeRecording)
        XCTAssertEqual(stopCall.rejected?.message, ErrorCodes.failedToMergeRecording)
    }

    func testPauseRecordingRejectsWhenServiceMissing() {
        let plugin = TestVoiceRecorder()
        plugin.configureForTesting(service: nil)
        let callSpy = PluginCallSpy()

        plugin.pauseRecording(callSpy.call)

        XCTAssertEqual(callSpy.rejected?.message, Messages.RECORDING_HAS_NOT_STARTED)
        XCTAssertEqual(callSpy.rejected?.code, ErrorCodes.recordingHasNotStarted)
        XCTAssertNil(callSpy.resolvedData)
    }

    func testGetCurrentStatusResolvesStatus() {
        let (plugin, _, _) = makePlugin()
        let startCall = PluginCallSpy()
        plugin.startRecording(startCall.call)
        let statusCall = PluginCallSpy()

        plugin.getCurrentStatus(statusCall.call)

        let statusPayload = statusCall.resolvedData?["status"] as? String
        XCTAssertEqual(statusPayload, CurrentRecordingStatus.RECORDING.rawValue)
    }
}
