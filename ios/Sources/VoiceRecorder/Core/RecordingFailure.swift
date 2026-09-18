import Foundation

/// A recording session that died while it was running.
///
/// Not the same thing as a call that failed: every error in `ErrorCodes` is
/// reported back through the call that caused it, and the caller knows at once.
/// This is the other kind -- the recorder is invalidated between calls, by
/// something nobody asked for, and nothing surfaces it unless it is pushed.
struct RecordingFailure {
    let code: String
    let message: String?

    /// The recorder stopped without anyone asking it to: an encoder error, the
    /// session torn down under us, the input taken away.
    static let stoppedUnexpectedly = "RECORDER_STOPPED_UNEXPECTEDLY"

    /// Asked for, to rehearse the failure path. See `simulateFailure`.
    static let simulated = "SIMULATED_FAILURE"
}
