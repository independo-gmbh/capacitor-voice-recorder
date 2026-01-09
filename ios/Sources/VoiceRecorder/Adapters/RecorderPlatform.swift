import Foundation

/// Platform abstraction for device and file operations.
protocol RecorderPlatform {
    /// Returns whether the device can record audio.
    func canDeviceVoiceRecord() -> Bool
    /// Reads the file as base64, returning nil on failure.
    func readFileAsBase64(_ filePath: URL?) -> String?
    /// Returns the file duration in milliseconds, or -1 on failure.
    func getDurationMs(_ filePath: URL?) -> Int
}
