import Foundation
import AVFoundation
import Capacitor

typealias PermissionRequester = (@escaping (Bool) -> Void) -> Void
typealias PermissionStatusProvider = () -> AVAudioSession.RecordPermission

/// Capacitor bridge for the VoiceRecorder plugin.
@objc(VoiceRecorder)
public class VoiceRecorder: CAPPlugin, CAPBridgedPlugin {
    /// Plugin identifier used by Capacitor.
    public let identifier = "VoiceRecorder"
    /// JavaScript name used for the plugin proxy.
    public let jsName = "VoiceRecorder"
    /// Supported plugin methods exposed to the JS layer.
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "canDeviceVoiceRecord", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestAudioRecordingPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "hasAudioRecordingPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "pauseRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "resumeRecording", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentStatus", returnType: CAPPluginReturnPromise),
    ]

    /// Service layer that performs recording operations.
    private var service: VoiceRecorderService?
    /// Response format derived from plugin configuration.
    private var responseFormat: ResponseFormat = .legacy
    /// Permission requester used by the bridge.
    private var permissionRequester: PermissionRequester = { completion in
        AVAudioSession.sharedInstance().requestRecordPermission(completion)
    }
    /// Permission status provider used by the bridge.
    private var permissionStatusProvider: PermissionStatusProvider = {
        AVAudioSession.sharedInstance().recordPermission
    }

    /// Initializes dependencies after the plugin loads.
    public override func load() {
        super.load()
        responseFormat = ResponseFormat(config: getConfig())
        service = VoiceRecorderService(
            platform: DefaultRecorderPlatform(),
            permissionChecker: { [weak self] in
                self?.doesUserGaveAudioRecordingPermission() ?? false
            }
        )
    }

    func configureForTesting(
        service: VoiceRecorderService?,
        responseFormat: ResponseFormat? = nil,
        permissionRequester: PermissionRequester? = nil,
        permissionStatusProvider: PermissionStatusProvider? = nil
    ) {
        self.service = service
        if let responseFormat = responseFormat {
            self.responseFormat = responseFormat
        }
        if let permissionRequester = permissionRequester {
            self.permissionRequester = permissionRequester
        }
        if let permissionStatusProvider = permissionStatusProvider {
            self.permissionStatusProvider = permissionStatusProvider
        }
    }

    /// Returns whether the device can record audio.
    @objc func canDeviceVoiceRecord(_ call: CAPPluginCall) {
        let canRecord = service?.canDeviceVoiceRecord() ?? false
        call.resolve(ResponseGenerator.fromBoolean(canRecord))
    }

    /// Requests microphone permission from the user.
    @objc func requestAudioRecordingPermission(_ call: CAPPluginCall) {
        permissionRequester { granted in
            if granted {
                call.resolve(ResponseGenerator.successResponse())
            } else {
                call.resolve(ResponseGenerator.failResponse())
            }
        }
    }

    /// Returns whether the app has microphone permission.
    @objc func hasAudioRecordingPermission(_ call: CAPPluginCall) {
        let hasPermission = service?.hasAudioRecordingPermission() ?? false
        call.resolve(ResponseGenerator.fromBoolean(hasPermission))
    }

    /// Starts a recording session with optional file output.
    @objc func startRecording(_ call: CAPPluginCall) {
        guard let service = service else {
            call.reject(Messages.FAILED_TO_RECORD, ErrorCodes.failedToRecord)
            return
        }

        let directory: String? = call.getString("directory")
        let subDirectory: String? = call.getString("subDirectory")
        let recordOptions = RecordOptions(directory: directory, subDirectory: subDirectory)
        do {
            try service.startRecording(
                options: recordOptions,
                onInterruptionBegan: { [weak self] in
                    self?.notifyListeners("voiceRecordingInterrupted", data: [:])
                },
                onInterruptionEnded: { [weak self] in
                    self?.notifyListeners("voiceRecordingInterruptionEnded", data: [:])
                },
                onVolumeChanged: { [weak self] volume in
                    // volume is a Float between 0.0 and 1.0
                    self?.notifyListeners("volumeChanged", data: ["volume": volume])
                }
            )
            call.resolve(ResponseGenerator.successResponse())
        } catch let error as VoiceRecorderServiceError {
            call.reject(toLegacyMessage(error.code), error.code, error.underlyingError ?? error)
        } catch {
            call.reject(Messages.FAILED_TO_RECORD, ErrorCodes.failedToRecord, error)
        }
    }

    /// Stops recording and returns the audio payload.
    @objc func stopRecording(_ call: CAPPluginCall) {
        guard let service = service else {
            call.reject(Messages.FAILED_TO_FETCH_RECORDING, ErrorCodes.failedToFetchRecording)
            return
        }

        service.stopRecording { [weak self] result in
            DispatchQueue.main.async {
                guard let self = self else {
                    call.reject(Messages.FAILED_TO_FETCH_RECORDING, ErrorCodes.failedToFetchRecording)
                    return
                }

                switch result {
                case .success(let recordData):
                    let payload: Dictionary<String, Any>
                    if self.responseFormat == .normalized {
                        payload = RecordDataMapper.toNormalizedDictionary(recordData)
                    } else {
                        payload = RecordDataMapper.toLegacyDictionary(recordData)
                    }
                    call.resolve(ResponseGenerator.dataResponse(payload))
                case .failure(let error):
                    call.reject(self.toLegacyMessage(error.code), error.code, error.underlyingError ?? error)
                }
            }
        }
    }

    /// Pauses a recording session if supported.
    @objc func pauseRecording(_ call: CAPPluginCall) {
        guard let service = service else {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED, ErrorCodes.recordingHasNotStarted)
            return
        }

        do {
            call.resolve(ResponseGenerator.fromBoolean(try service.pauseRecording()))
        } catch let error as VoiceRecorderServiceError {
            call.reject(toLegacyMessage(error.code), error.code, error.underlyingError ?? error)
        } catch {
            call.reject(Messages.FAILED_TO_RECORD, ErrorCodes.failedToRecord, error)
        }
    }

    /// Resumes a paused recording session if supported.
    @objc func resumeRecording(_ call: CAPPluginCall) {
        guard let service = service else {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED, ErrorCodes.recordingHasNotStarted)
            return
        }

        do {
            call.resolve(ResponseGenerator.fromBoolean(try service.resumeRecording()))
        } catch let error as VoiceRecorderServiceError {
            call.reject(toLegacyMessage(error.code), error.code, error.underlyingError ?? error)
        } catch {
            call.reject(Messages.FAILED_TO_RECORD, ErrorCodes.failedToRecord, error)
        }
    }

    /// Returns the current recording status.
    @objc func getCurrentStatus(_ call: CAPPluginCall) {
        let status = service?.getCurrentStatus() ?? .NONE
        call.resolve(ResponseGenerator.statusResponse(status))
    }

    /// Returns whether AVAudioSession reports granted permission.
    func doesUserGaveAudioRecordingPermission() -> Bool {
        return permissionStatusProvider() == AVAudioSession.RecordPermission.granted
    }

    /// Maps canonical error codes back to legacy messages.
    private func toLegacyMessage(_ canonicalCode: String) -> String {
        if canonicalCode == ErrorCodes.deviceCannotVoiceRecord {
            return Messages.CANNOT_RECORD_ON_THIS_PHONE
        }
        return canonicalCode
    }
}
