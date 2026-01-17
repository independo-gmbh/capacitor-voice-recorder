import Foundation
import AVFoundation

protocol AudioSessionProtocol: AnyObject {
    var category: AVAudioSession.Category { get }
    func setCategory(_ category: AVAudioSession.Category) throws
    func setActive(_ active: Bool, options: AVAudioSession.SetActiveOptions) throws
}

protocol AudioRecorderProtocol: AnyObject {
    @discardableResult
    func record() -> Bool
    func stop()
    func pause()

    var isMeteringEnabled: Bool { get set }
    func updateMeters()
    func averagePower(forChannel channelNumber: Int) -> Float
}

typealias AudioRecorderFactory = (_ url: URL, _ settings: [String: Any]) throws -> AudioRecorderProtocol

extension AVAudioSession: AudioSessionProtocol {}
extension AVAudioRecorder: AudioRecorderProtocol {}

/// AVAudioRecorder wrapper that supports interruptions and segment merging.
class CustomMediaRecorder: RecorderAdapter {

    private var levelTimer: Timer?
    private var lowPassVolume: Float = 0.0

    private let audioSessionProvider: () -> AudioSessionProtocol
    private let audioRecorderFactory: AudioRecorderFactory

    /// Options provided by the service layer.
    public var options: RecordOptions?
    /// Active audio session for recording.
    private var recordingSession: AudioSessionProtocol!
    /// Active recorder instance for the current segment.
    private var audioRecorder: AudioRecorderProtocol!
    /// Base file path for the merged recording.
    private var baseAudioFilePath: URL!
    /// List of segment files created during interruptions.
    private var audioFileSegments: [URL] = []
    /// Audio session category before recording starts.
    private var originalRecordingSessionCategory: AVAudioSession.Category!
    /// Current recording status.
    private var status = CurrentRecordingStatus.NONE
    /// Notification observer for audio interruptions.
    private var interruptionObserver: NSObjectProtocol?
    /// Callback invoked when interruptions begin.
    var onInterruptionBegan: (() -> Void)?
    /// Callback invoked when interruptions end.
    var onInterruptionEnded: (() -> Void)?
    /// Callback for receiving volume updates.
    var onVolumeChanged: ((Float) -> Void)?

    /// Recorder settings used for all segments.
    private let settings: [String: Any] = [
        AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
        AVSampleRateKey: 44100,
        AVNumberOfChannelsKey: 1,
        AVEncoderAudioQualityKey: AVAudioQuality.high.rawValue
    ]

    init(
        audioSessionProvider: @escaping () -> AudioSessionProtocol = { AVAudioSession.sharedInstance() },
        audioRecorderFactory: @escaping AudioRecorderFactory = { url, settings in
            return try AVAudioRecorder(url: url, settings: settings)
        }
    ) {
        self.audioSessionProvider = audioSessionProvider
        self.audioRecorderFactory = audioRecorderFactory
    }

    /// Resolves the directory where audio files should be saved.
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

    private func calculateVisualLevel(from rawLinear: Float) -> Float {
        let threshold: Float = 0.15
        let kneePoint: Float = 0.8

        if rawLinear <= threshold {
            // Map 0 - 0.15 up to 0 - 0.8 using a square root for sensitivity
            return sqrt(rawLinear / threshold) * kneePoint
        } else {
            // Map 0.15 - 1.0 to 0.8 - 1.0
            let excess = (rawLinear - threshold) / (1.0 - threshold)
            return kneePoint + (excess * (1.0 - kneePoint))
        }
    }

    private func startVolumeMetering() {
        stopVolumeMetering()

        guard options?.volumeMetering == true else {
            return
        }

        self.lowPassVolume = 0.0

        let timer = Timer(timeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self = self, let recorder = self.audioRecorder else {
                self?.stopVolumeMetering()
                return
            }

            recorder.updateMeters()
            let averagePower = recorder.averagePower(forChannel: 0)

            // Convert dB (-160 to 0) to linear (0.0 to 1.0)
            let rawLinearLevel = pow(10, averagePower / 20)

            let visualLevel = self.calculateVisualLevel(from: rawLinearLevel)

            // Apply a Low-Pass Filter for smoothness
            self.lowPassVolume = (0.5 * visualLevel) + (0.5 * self.lowPassVolume)

            DispatchQueue.main.async {
                self.onVolumeChanged?(self.lowPassVolume)
            }
        }

        RunLoop.main.add(timer, forMode: .common)
        self.levelTimer = timer
    }

    private func stopVolumeMetering() {
        levelTimer?.invalidate()
        levelTimer = nil
    }

    /// Starts recording audio and prepares the session.
    public func startRecording(recordOptions: RecordOptions?) -> Bool {
        do {
            options = recordOptions
            recordingSession = audioSessionProvider()
            originalRecordingSessionCategory = recordingSession.category
            try recordingSession.setCategory(AVAudioSession.Category.playAndRecord)
            try recordingSession.setActive(true, options: [])
            baseAudioFilePath = getDirectoryToSaveAudioFile().appendingPathComponent("recording-\(Int(Date().timeIntervalSince1970 * 1000)).aac")
            audioFileSegments = [baseAudioFilePath]
            audioRecorder = try audioRecorderFactory(baseAudioFilePath, settings)
            audioRecorder.isMeteringEnabled = true
            setupInterruptionHandling()
            audioRecorder.record()
            startVolumeMetering()
            status = CurrentRecordingStatus.RECORDING
            return true
        } catch {
            return false
        }
    }

    /// Stops recording and merges segments if needed.
    public func stopRecording(completion: @escaping (Bool) -> Void) {
        removeInterruptionHandling()
        stopVolumeMetering()
        audioRecorder.stop()

        let finalizeStop: (Bool) -> Void = { [weak self] success in
            guard let self = self else {
                completion(false)
                return
            }

            do {
                try self.recordingSession.setActive(false, options: [])
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

    /// Returns the output file for the recording.
    public func getOutputFile() -> URL {
        return baseAudioFilePath
    }

    /// Maps directory strings to FileManager search paths.
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

    /// Pauses recording when currently active.
    public func pauseRecording() -> Bool {
        if(status == CurrentRecordingStatus.RECORDING) {
            stopVolumeMetering()
            audioRecorder.pause()
            status = CurrentRecordingStatus.PAUSED
            return true
        } else {
            return false
        }
    }

    /// Resumes recording after pause or interruption.
    public func resumeRecording() -> Bool {
        if(status == CurrentRecordingStatus.PAUSED || status == CurrentRecordingStatus.INTERRUPTED) {
            let wasInterrupted = status == CurrentRecordingStatus.INTERRUPTED
            do {
                try recordingSession.setActive(true, options: [])
                if status == CurrentRecordingStatus.INTERRUPTED {
                    let directory = getDirectoryToSaveAudioFile()
                    let timestamp = Int(Date().timeIntervalSince1970 * 1000)
                    let segmentNumber = audioFileSegments.count
                    let segmentPath = directory.appendingPathComponent("recording-\(timestamp)-segment-\(segmentNumber).aac")
                    audioRecorder = try audioRecorderFactory(segmentPath, settings)
                    audioFileSegments.append(segmentPath)
                }
                audioRecorder.isMeteringEnabled = true
                audioRecorder.record()
                startVolumeMetering()
                status = CurrentRecordingStatus.RECORDING
                return true
            } catch {
                if wasInterrupted {
                    try? recordingSession.setActive(false, options: [])
                }
                return false
            }
        }

        return false
    }

    /// Returns the current recording status.
    public func getCurrentStatus() -> CurrentRecordingStatus {
        return status
    }

    /// Registers for interruption notifications.
    private func setupInterruptionHandling() {
        interruptionObserver = NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: recordingSession,
            queue: .main
        ) { [weak self] notification in
            self?.handleInterruption(notification: notification)
        }
    }

    /// Removes interruption observers.
    private func removeInterruptionHandling() {
        if let observer = interruptionObserver {
            NotificationCenter.default.removeObserver(observer)
            interruptionObserver = nil
        }
    }

    /// Handles audio session interruptions.
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

    /// Merges recorded segments into a single file when interruptions occur.
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
