import Foundation
import AVFoundation

/// Default platform adapter for file IO and duration lookup.
final class DefaultRecorderPlatform: RecorderPlatform {
    /// Returns whether the device can record audio.
    func canDeviceVoiceRecord() -> Bool {
        return true
    }

    /// Reads a file as base64, returning nil on failure.
    func readFileAsBase64(_ filePath: URL?) -> String? {
        guard let filePath = filePath else {
            return nil
        }

        do {
            let fileData = try Data(contentsOf: filePath)
            return fileData.base64EncodedString(options: NSData.Base64EncodingOptions(rawValue: 0))
        } catch {
            return nil
        }
    }

    /// Returns the file duration in milliseconds, or -1 on failure.
    func getDurationMs(_ filePath: URL?) -> Int {
        guard let filePath = filePath else {
            return -1
        }

        return Int(CMTimeGetSeconds(AVURLAsset(url: filePath).duration) * 1000)
    }
}
