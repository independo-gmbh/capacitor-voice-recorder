import Foundation

/// Error wrapper that carries a canonical error code.
struct VoiceRecorderServiceError: Error {
    /// Canonical error code for bridge mapping.
    let code: String
    /// Underlying error, when available.
    let underlyingError: Error?

    init(code: String, underlyingError: Error? = nil) {
        self.code = code
        self.underlyingError = underlyingError
    }
}
