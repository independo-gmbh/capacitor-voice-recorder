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

    public func stopRecording(completion: @escaping (Bool) -> Void) {
        removeInterruptionHandling()
        audioRecorder.stop()

        let finalizeStop: (Bool) -> Void = { [weak self] success in
            guard let self = self else {
                completion(false)
                return
            }

            do {
                try self.recordingSession.setActive(false)
                try self.recordingSession.setCategory(self.originalRecordingSessionCategory)
            } catch {
            }

            self.originalRecordingSessionCategory = nil
            self.audioRecorder = nil
            self.recordingSession = nil
            self.status = CurrentRecordingStatus.NONE
            completion(success)
        }

        if audioFileSegments.count > 1 {
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                guard let self = self else {
                    completion(false)
                    return
                }
                self.mergeAudioSegments { success in
                    finalizeStop(success)
                }
            }
        } else {
            finalizeStop(true)
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
            let wasInterrupted = status == CurrentRecordingStatus.INTERRUPTED
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
                if wasInterrupted {
                    try? recordingSession.setActive(false)
                }
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

    private func mergeAudioSegments(completion: @escaping (Bool) -> Void) {
        if audioFileSegments.count <= 1 {
            completion(true)
            return
        }

        let basePathWithoutExtension = baseAudioFilePath.deletingPathExtension()
        let mergedFilePath = basePathWithoutExtension.appendingPathExtension("m4a")
        let segmentURLs = audioFileSegments
        let keys = ["tracks", "duration"]
        let dispatchGroup = DispatchGroup()
        let syncQueue = DispatchQueue(label: "CustomMediaRecorder.assetSyncQueue")
        var loadedAssets = Array<AVURLAsset?>(repeating: nil, count: segmentURLs.count)
        var loadFailed = false

        for (index, segmentURL) in segmentURLs.enumerated() {
            let asset = AVURLAsset(url: segmentURL)
            dispatchGroup.enter()
            asset.loadValuesAsynchronously(forKeys: keys) {
                var assetIsValid = true
                for key in keys {
                    var error: NSError?
                    if asset.statusOfValue(forKey: key, error: &error) != .loaded {
                        assetIsValid = false
                        break
                    }
                }
                syncQueue.async {
                    if assetIsValid {
                        loadedAssets[index] = asset
                    } else {
                        loadFailed = true
                    }
                    dispatchGroup.leave()
                }
            }
        }

        dispatchGroup.notify(queue: DispatchQueue.global(qos: .userInitiated)) { [weak self] in
            guard let self = self else {
                completion(false)
                return
            }

            var assets: [AVURLAsset] = []
            var didFail = false
            syncQueue.sync {
                if loadFailed || loadedAssets.contains(where: { $0 == nil }) {
                    didFail = true
                } else {
                    assets = loadedAssets.compactMap { $0 }
                }
            }

            if didFail || assets.count != segmentURLs.count {
                completion(false)
                return
            }

            let composition = AVMutableComposition()
            guard let compositionAudioTrack = composition.addMutableTrack(
                withMediaType: .audio,
                preferredTrackID: kCMPersistentTrackID_Invalid
            ) else {
                completion(false)
                return
            }

            var insertTime = CMTime.zero

            for asset in assets {
                guard let assetTrack = asset.tracks(withMediaType: .audio).first else {
                    completion(false)
                    return
                }

                do {
                    let timeRange = CMTimeRange(start: .zero, duration: asset.duration)
                    try compositionAudioTrack.insertTimeRange(timeRange, of: assetTrack, at: insertTime)
                    insertTime = CMTimeAdd(insertTime, asset.duration)
                } catch {
                    completion(false)
                    return
                }
            }

            guard let exportSession = AVAssetExportSession(asset: composition, presetName: AVAssetExportPresetAppleM4A) else {
                completion(false)
                return
            }

            let tempDirectory = self.getDirectoryToSaveAudioFile()
            let tempPath = tempDirectory.appendingPathComponent("temp-merged-\(Int(Date().timeIntervalSince1970 * 1000)).m4a")

            exportSession.outputURL = tempPath
            exportSession.outputFileType = .m4a

            exportSession.exportAsynchronously {
                guard exportSession.status == .completed else {
                    completion(false)
                    return
                }

                if !FileManager.default.fileExists(atPath: tempPath.path) {
                    completion(false)
                    return
                }

                do {
                    if FileManager.default.fileExists(atPath: mergedFilePath.path) {
                        try FileManager.default.removeItem(at: mergedFilePath)
                    }
                    try FileManager.default.moveItem(at: tempPath, to: mergedFilePath)

                    for segmentURL in self.audioFileSegments {
                        if segmentURL != mergedFilePath && FileManager.default.fileExists(atPath: segmentURL.path) {
                            try? FileManager.default.removeItem(at: segmentURL)
                        }
                    }
                    self.baseAudioFilePath = mergedFilePath
                    completion(true)
                } catch {
                    if FileManager.default.fileExists(atPath: tempPath.path) {
                        try? FileManager.default.removeItem(at: tempPath)
                    }
                    completion(false)
                }
            }
        }
    }

}
