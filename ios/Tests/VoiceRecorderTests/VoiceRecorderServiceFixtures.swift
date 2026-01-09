import Foundation
@testable import VoiceRecorder

final class VoiceRecorderServiceFixtures {
    final class FakePlatform: RecorderPlatform {
        var canRecord = true
        var base64Payload: String? = "BASE64"
        var durationMs = 1000
        var readFileCalled = false

        func canDeviceVoiceRecord() -> Bool {
            return canRecord
        }

        func readFileAsBase64(_ filePath: URL?) -> String? {
            readFileCalled = true
            return base64Payload
        }

        func getDurationMs(_ filePath: URL?) -> Int {
            return durationMs
        }
    }

    final class FakeRecorder: RecorderAdapter {
        var options: RecordOptions?
        var onInterruptionBegan: (() -> Void)?
        var onInterruptionEnded: (() -> Void)?
        var outputFile: URL = URL(fileURLWithPath: "/tmp/recording.aac")
        var status: CurrentRecordingStatus = .NONE
        var stopSuccess = true
        var startSuccess = true
        var pauseResult = true
        var resumeResult = true

        func startRecording(recordOptions: RecordOptions?) -> Bool {
            options = recordOptions
            if startSuccess {
                status = .RECORDING
            }
            return startSuccess
        }

        func stopRecording(completion: @escaping (Bool) -> Void) {
            status = .NONE
            completion(stopSuccess)
        }

        func pauseRecording() -> Bool {
            if pauseResult {
                status = .PAUSED
            }
            return pauseResult
        }

        func resumeRecording() -> Bool {
            if resumeResult {
                status = .RECORDING
            }
            return resumeResult
        }

        func getCurrentStatus() -> CurrentRecordingStatus {
            return status
        }

        func getOutputFile() -> URL {
            return outputFile
        }
    }

    static func makeService(
        platform: FakePlatform,
        recorder: FakeRecorder,
        permissionGranted: Bool
    ) -> VoiceRecorderService {
        return VoiceRecorderService(
            platform: platform,
            permissionChecker: { permissionGranted },
            recorderFactory: { recorder }
        )
    }
}
