import Foundation
import AVFoundation
import Capacitor

@objc(VoiceRecorder)
public class VoiceRecorder: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "VoiceRecorder"
    public let jsName = "VoiceRecorder"
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

    private var customMediaRecorder: CustomMediaRecorder? = nil

    @objc func canDeviceVoiceRecord(_ call: CAPPluginCall) {
        call.resolve(ResponseGenerator.successResponse())
    }

    @objc func requestAudioRecordingPermission(_ call: CAPPluginCall) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            if granted {
                call.resolve(ResponseGenerator.successResponse())
            } else {
                call.resolve(ResponseGenerator.failResponse())
            }
        }
    }

    @objc func hasAudioRecordingPermission(_ call: CAPPluginCall) {
        call.resolve(ResponseGenerator.fromBoolean(doesUserGaveAudioRecordingPermission()))
    }

    @objc func startRecording(_ call: CAPPluginCall) {
        if(!doesUserGaveAudioRecordingPermission()) {
            call.reject(Messages.MISSING_PERMISSION)
            return
        }

        if(customMediaRecorder != nil) {
            call.reject(Messages.ALREADY_RECORDING)
            return
        }

        customMediaRecorder = CustomMediaRecorder()
        if(customMediaRecorder == nil) {
            call.reject(Messages.CANNOT_RECORD_ON_THIS_PHONE)
            return
        }

        customMediaRecorder?.onInterruptionBegan = { [weak self] in
            self?.notifyListeners("voiceRecordingInterrupted", data: [:])
        }

        customMediaRecorder?.onInterruptionEnded = { [weak self] in
            self?.notifyListeners("voiceRecordingInterruptionEnded", data: [:])
        }

        let directory: String? = call.getString("directory")
        let subDirectory: String? = call.getString("subDirectory")
        let recordOptions = RecordOptions(directory: directory, subDirectory: subDirectory)
        let successfullyStartedRecording = customMediaRecorder!.startRecording(recordOptions: recordOptions)
        if successfullyStartedRecording == false {
            call.reject(Messages.CANNOT_RECORD_ON_THIS_PHONE)
        } else {
            call.resolve(ResponseGenerator.successResponse())
        }
    }

    @objc func stopRecording(_ call: CAPPluginCall) {
        if(customMediaRecorder == nil) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED)
            return
        }

        let stopSuccess = customMediaRecorder?.stopRecording() ?? false
        if !stopSuccess {
            customMediaRecorder = nil
            call.reject(Messages.FAILED_TO_MERGE_RECORDING)
            return
        }

        let audioFileUrl = customMediaRecorder?.getOutputFile()
        if(audioFileUrl == nil) {
            customMediaRecorder = nil
            call.reject(Messages.FAILED_TO_FETCH_RECORDING)
            return
        }

        let fileExtension = audioFileUrl!.pathExtension.lowercased()
        let mimeType = fileExtension == "m4a" ? "audio/mp4" : "audio/aac"
        let sendDataAsBase64 = customMediaRecorder?.options?.directory == nil
        let recordData = RecordData(
            recordDataBase64: sendDataAsBase64 ? readFileAsBase64(audioFileUrl) : nil,
            mimeType: mimeType,
            msDuration: getMsDurationOfAudioFile(audioFileUrl),
            uri: sendDataAsBase64 ? nil : audioFileUrl!.path
        )
        customMediaRecorder = nil
        if (sendDataAsBase64 && recordData.recordDataBase64 == nil) || recordData.msDuration < 0 {
            call.reject(Messages.EMPTY_RECORDING)
        } else {
            call.resolve(ResponseGenerator.dataResponse(recordData.toDictionary()))
        }
    }

    @objc func pauseRecording(_ call: CAPPluginCall) {
        if(customMediaRecorder == nil) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED)
        } else {
            call.resolve(ResponseGenerator.fromBoolean(customMediaRecorder?.pauseRecording() ?? false))
        }
    }

    @objc func resumeRecording(_ call: CAPPluginCall) {
        if(customMediaRecorder == nil) {
            call.reject(Messages.RECORDING_HAS_NOT_STARTED)
        } else {
            call.resolve(ResponseGenerator.fromBoolean(customMediaRecorder?.resumeRecording() ?? false))
        }
    }

    @objc func getCurrentStatus(_ call: CAPPluginCall) {
        if(customMediaRecorder == nil) {
            call.resolve(ResponseGenerator.statusResponse(CurrentRecordingStatus.NONE))
        } else {
            call.resolve(ResponseGenerator.statusResponse(customMediaRecorder?.getCurrentStatus() ?? CurrentRecordingStatus.NONE))
        }
    }

    func doesUserGaveAudioRecordingPermission() -> Bool {
        return AVAudioSession.sharedInstance().recordPermission == AVAudioSession.RecordPermission.granted
    }

    func readFileAsBase64(_ filePath: URL?) -> String? {
        if(filePath == nil) {
            return nil
        }

        do {
            let fileData = try Data.init(contentsOf: filePath!)
            let fileStream = fileData.base64EncodedString(options: NSData.Base64EncodingOptions.init(rawValue: 0))
            return fileStream
        } catch {}

        return nil
    }

    func getMsDurationOfAudioFile(_ filePath: URL?) -> Int {
        if filePath == nil {
            return -1
        }
        return Int(CMTimeGetSeconds(AVURLAsset(url: filePath!).duration) * 1000)
    }

}
