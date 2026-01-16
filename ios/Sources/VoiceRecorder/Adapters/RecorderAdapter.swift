import Foundation

/// Recorder abstraction used by the service layer.
protocol RecorderAdapter: AnyObject {
    /// Options supplied when recording starts.
    var options: RecordOptions? { get }
    /// Callback invoked when interruptions begin.
    var onInterruptionBegan: (() -> Void)? { get set }
    /// Callback invoked when interruptions end.
    var onInterruptionEnded: (() -> Void)? { get set }
    /// Callback for receiving volume updates.
    var onVolumeChanged: ((Float) -> Void)? { get set }

    /// Starts recording audio.
    func startRecording(recordOptions: RecordOptions?) -> Bool
    /// Stops recording and returns success via callback.
    func stopRecording(completion: @escaping (Bool) -> Void)
    /// Pauses recording if supported.
    func pauseRecording() -> Bool
    /// Resumes recording if supported.
    func resumeRecording() -> Bool
    /// Returns the current recording status.
    func getCurrentStatus() -> CurrentRecordingStatus
    /// Returns the output file for the current session.
    func getOutputFile() -> URL
}
