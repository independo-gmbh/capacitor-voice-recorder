import Foundation

/// Canonical error codes returned by the plugin.
struct ErrorCodes {
    static let missingPermission = "MISSING_PERMISSION"
    static let alreadyRecording = "ALREADY_RECORDING"
    static let microphoneBeingUsed = "MICROPHONE_BEING_USED"
    static let deviceCannotVoiceRecord = "DEVICE_CANNOT_VOICE_RECORD"
    static let failedToRecord = "FAILED_TO_RECORD"
    static let emptyRecording = "EMPTY_RECORDING"
    static let recordingHasNotStarted = "RECORDING_HAS_NOT_STARTED"
    static let failedToFetchRecording = "FAILED_TO_FETCH_RECORDING"
    static let failedToMergeRecording = "FAILED_TO_MERGE_RECORDING"
    static let notSupportedOsVersion = "NOT_SUPPORTED_OS_VERSION"
    static let couldNotQueryPermissionStatus = "COULD_NOT_QUERY_PERMISSION_STATUS"
}
