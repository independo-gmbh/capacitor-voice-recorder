import Foundation

/// Service layer that orchestrates recording operations.
final class VoiceRecorderService {
    /// Platform adapter for device and file operations.
    private let platform: RecorderPlatform
    /// Closure used to check microphone permission.
    private let permissionChecker: () -> Bool
    /// Active recorder instance for the current session.
    private var recorder: RecorderAdapter?

    init(platform: RecorderPlatform, permissionChecker: @escaping () -> Bool) {
        self.platform = platform
        self.permissionChecker = permissionChecker
    }

    /// Returns whether the device can record audio.
    func canDeviceVoiceRecord() -> Bool {
        return platform.canDeviceVoiceRecord()
    }

    /// Returns whether the app has microphone permission.
    func hasAudioRecordingPermission() -> Bool {
        return permissionChecker()
    }

    /// Starts a recording session or throws a service error.
    func startRecording(
        options: RecordOptions?,
        onInterruptionBegan: @escaping () -> Void,
        onInterruptionEnded: @escaping () -> Void
    ) throws {
        if !platform.canDeviceVoiceRecord() {
            throw VoiceRecorderServiceError(code: ErrorCodes.deviceCannotVoiceRecord)
        }

        if !permissionChecker() {
            throw VoiceRecorderServiceError(code: ErrorCodes.missingPermission)
        }

        if recorder != nil {
            throw VoiceRecorderServiceError(code: ErrorCodes.alreadyRecording)
        }

        let nextRecorder = CustomMediaRecorder()
        nextRecorder.onInterruptionBegan = onInterruptionBegan
        nextRecorder.onInterruptionEnded = onInterruptionEnded
        let started = nextRecorder.startRecording(recordOptions: options)
        if !started {
            recorder = nil
            throw VoiceRecorderServiceError(code: ErrorCodes.deviceCannotVoiceRecord)
        }

        recorder = nextRecorder
    }

    /// Stops recording and returns the payload asynchronously.
    func stopRecording(completion: @escaping (Result<RecordData, VoiceRecorderServiceError>) -> Void) {
        guard let recorder = recorder else {
            completion(.failure(VoiceRecorderServiceError(code: ErrorCodes.recordingHasNotStarted)))
            return
        }

        recorder.stopRecording { [weak self] stopSuccess in
            guard let self = self else {
                completion(.failure(VoiceRecorderServiceError(code: ErrorCodes.failedToFetchRecording)))
                return
            }

            if !stopSuccess {
                self.recorder = nil
                completion(.failure(VoiceRecorderServiceError(code: ErrorCodes.failedToMergeRecording)))
                return
            }

            let audioFileUrl = recorder.getOutputFile()
            let fileExtension = audioFileUrl.pathExtension.lowercased()
            let mimeType = fileExtension == "m4a" ? "audio/mp4" : "audio/aac"
            let sendDataAsBase64 = recorder.options?.directory == nil
            let recordDataBase64 = sendDataAsBase64 ? self.platform.readFileAsBase64(audioFileUrl) : nil
            let uri = sendDataAsBase64 ? nil : audioFileUrl.path
            let recordData = RecordData(
                recordDataBase64: recordDataBase64,
                mimeType: mimeType,
                msDuration: self.platform.getDurationMs(audioFileUrl),
                uri: uri
            )

            self.recorder = nil
            if (sendDataAsBase64 && recordData.recordDataBase64 == nil) || recordData.msDuration < 0 {
                completion(.failure(VoiceRecorderServiceError(code: ErrorCodes.emptyRecording)))
            } else {
                completion(.success(recordData))
            }
        }
    }

    /// Pauses the active recording session.
    func pauseRecording() throws -> Bool {
        guard let recorder = recorder else {
            throw VoiceRecorderServiceError(code: ErrorCodes.recordingHasNotStarted)
        }
        return recorder.pauseRecording()
    }

    /// Resumes a paused recording session.
    func resumeRecording() throws -> Bool {
        guard let recorder = recorder else {
            throw VoiceRecorderServiceError(code: ErrorCodes.recordingHasNotStarted)
        }
        return recorder.resumeRecording()
    }

    /// Returns the current recording status.
    func getCurrentStatus() -> CurrentRecordingStatus {
        guard let recorder = recorder else {
            return .NONE
        }
        return recorder.getCurrentStatus()
    }
}
