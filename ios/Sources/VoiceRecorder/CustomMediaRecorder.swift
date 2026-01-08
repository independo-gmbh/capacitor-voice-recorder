import Foundation
import AVFoundation

class CustomMediaRecorder {

    public var options: RecordOptions?
    private var recordingSession: AVAudioSession!
    private var audioRecorder: AVAudioRecorder!
    private var baseAudioFilePath: URL!
    private var audioFileSegments: [URL] = []
    private var originalRecordingSessionCategory: AVAudioSession.Category!
    private var status = CurrentRecordingStatus.NONE
    private var interruptionObserver: NSObjectProtocol?
    var onInterruptionBegan: (() -> Void)?
    var onInterruptionEnded: (() -> Void)?

    private let settings = [
        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
        AVSampleRateKey: 44100,
        AVNumberOfChannelsKey: 1,
        AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
    ]

    private func getDirectoryToSaveAudioFile() -> URL {
	if options?.directory != nil,
	   let directory = getDirectory(directory: options?.directory),
           var outputDirURL = FileManager.default.urls(for: directory, in: .userDomainMask).first {
            if let subDirectory = options?.subDirectory?.trimmingCharacters(in: CharacterSet(charactersIn: "/")) {
                outputDirURL = outputDirURL.appendingPathComponent(subDirectory, isDirectory: true)

                do {
                    if !FileManager.default.fileExists(atPath: outputDirURL.path) {
                        try FileManager.default.createDirectory(at: outputDirURL, withIntermediateDirectories: true)
                    }
                } catch {
                    print("Error creating directory: \(error)")
                }
            }

            return outputDirURL
        }

        return URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
    }

    public func startRecording(recordOptions: RecordOptions?) -> Bool {
        do {
            options = recordOptions
            recordingSession = AVAudioSession.sharedInstance()
            originalRecordingSessionCategory = recordingSession.category
            try recordingSession.setCategory(AVAudioSession.Category.playAndRecord)
            try recordingSession.setActive(true)
            baseAudioFilePath = getDirectoryToSaveAudioFile().appendingPathComponent("recording-\(Int(Date().timeIntervalSince1970 * 1000)).aac")
            audioFileSegments = [baseAudioFilePath]
            audioRecorder = try AVAudioRecorder(url: baseAudioFilePath, settings: settings)
            setupInterruptionHandling()
            audioRecorder.record()
            status = CurrentRecordingStatus.RECORDING
            return true
        } catch {
            return false
        }
    }

    public func stopRecording() -> Bool {
        do {
            removeInterruptionHandling()
            audioRecorder.stop()
            if audioFileSegments.count > 1 {
                if !mergeAudioSegments() {
                    return false
                }
            }
            try recordingSession.setActive(false)
            try recordingSession.setCategory(originalRecordingSessionCategory)
            originalRecordingSessionCategory = nil
            audioRecorder = nil
            recordingSession = nil
            status = CurrentRecordingStatus.NONE
            return true
        } catch {
            return false
        }
    }

    public func getOutputFile() -> URL {
        return baseAudioFilePath
    }

    public func getDirectory(directory: String?) -> FileManager.SearchPathDirectory? {
        if let directory = directory {
            switch directory {
            case "CACHE":
                return .cachesDirectory
            case "LIBRARY":
                return .libraryDirectory
            default:
                return .documentDirectory
            }
        }
        return nil
    }

    public func pauseRecording() -> Bool {
        if(status == CurrentRecordingStatus.RECORDING) {
            audioRecorder.pause()
            status = CurrentRecordingStatus.PAUSED
            return true
        } else {
            return false
        }
    }

    public func resumeRecording() -> Bool {
        if(status == CurrentRecordingStatus.PAUSED || status == CurrentRecordingStatus.INTERRUPTED) {
            do {
                try recordingSession.setActive(true)
                if status == CurrentRecordingStatus.INTERRUPTED {
                    let directory = getDirectoryToSaveAudioFile()
                    let timestamp = Int(Date().timeIntervalSince1970 * 1000)
                    let segmentNumber = audioFileSegments.count
                    let segmentPath = directory.appendingPathComponent("recording-\(timestamp)-segment-\(segmentNumber).aac")
                    audioRecorder = try AVAudioRecorder(url: segmentPath, settings: settings)
                    audioFileSegments.append(segmentPath)
                }
                audioRecorder.record()
                status = CurrentRecordingStatus.RECORDING
                return true
            } catch {
                return false
            }
        }

        return false
    }

    public func getCurrentStatus() -> CurrentRecordingStatus {
        return status
    }

    private func setupInterruptionHandling() {
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: .main
        ) { [weak self] notification in
            self?.handleInterruption(notification: notification)
        }
    }

    private func removeInterruptionHandling() {
        if let observer = interruptionObserver {
            NotificationCenter.default.removeObserver(observer)
            interruptionObserver = nil
        }
    }

    private func handleInterruption(notification: Notification) {
        guard let userInfo = notification.userInfo,
              let interruptionTypeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
              let interruptionType = AVAudioSession.InterruptionType(rawValue: interruptionTypeValue) else {
            return
        }

        switch interruptionType {
        case .began:
            if status == CurrentRecordingStatus.RECORDING {
                audioRecorder.stop()
                status = CurrentRecordingStatus.INTERRUPTED
                onInterruptionBegan?()
            }

        case .ended:
            if status == CurrentRecordingStatus.INTERRUPTED {
                onInterruptionEnded?()
            }

        @unknown default:
            break
        }
    }

    private func mergeAudioSegments() -> Bool {
        if audioFileSegments.count <= 1 {
            return true
        }

        let basePathWithoutExtension = baseAudioFilePath.deletingPathExtension()
        baseAudioFilePath = basePathWithoutExtension.appendingPathExtension("m4a")

        let composition = AVMutableComposition()
        guard let compositionAudioTrack = composition.addMutableTrack(
            withMediaType: .audio,
            preferredTrackID: kCMPersistentTrackID_Invalid
        ) else {
            return false
        }

        var insertTime = CMTime.zero

        for segmentURL in audioFileSegments {
            let asset = AVURLAsset(url: segmentURL)
            asset.loadValuesAsynchronously(forKeys: ["tracks", "duration"]) {}
            guard let assetTrack = asset.tracks(withMediaType: .audio).first else {
                return false
            }

            do {
                let timeRange = CMTimeRange(start: .zero, duration: asset.duration)
                try compositionAudioTrack.insertTimeRange(timeRange, of: assetTrack, at: insertTime)
                insertTime = CMTimeAdd(insertTime, asset.duration)
            } catch {
                return false
            }
        }

        guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
            return false
        }

        let tempDirectory = getDirectoryToSaveAudioFile()
        let tempPath = tempDirectory.appendingPathComponent("temp-merged-\(Int(Date().timeIntervalSince1970 * 1000)).m4a")

        exportSession.outputURL = tempPath
        exportSession.outputFileType = .m4a

        let semaphore = DispatchSemaphore(value: 0)

        exportSession.exportAsynchronously {
            semaphore.signal()
        }

        semaphore.wait()

        guard exportSession.status == .completed else {
            return false
        }

        if !FileManager.default.fileExists(atPath: tempPath.path) {
            return false
        }

        do {
            if FileManager.default.fileExists(atPath: baseAudioFilePath.path) {
                try FileManager.default.removeItem(at: baseAudioFilePath)
            }
            try FileManager.default.moveItem(at: tempPath, to: baseAudioFilePath)

            for segmentURL in audioFileSegments {
                if segmentURL != baseAudioFilePath && FileManager.default.fileExists(atPath: segmentURL.path) {
                    try? FileManager.default.removeItem(at: segmentURL)
                }
            }
            return true
        } catch {
            return false
        }
    }

}
