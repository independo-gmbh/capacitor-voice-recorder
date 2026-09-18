import Foundation

/// Represents the current recording state.
enum CurrentRecordingStatus: String {

    case RECORDING
    case PAUSED
    case INTERRUPTED
    /// The session died while it was running (see `RecordingFailure`). A state
    /// of its own so that "not NONE" keeps meaning "there is a file to collect".
    case ERROR
    case NONE

}
