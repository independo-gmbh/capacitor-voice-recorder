import Foundation

/// Helper for building JS payloads in the legacy response shape.
struct ResponseGenerator {

    private static let VALUE_RESPONSE_KEY = "value"
    private static let STATUS_RESPONSE_KEY = "status"

    /// Wraps a boolean value into the response shape.
    public static func fromBoolean(_ value: Bool) -> Dictionary<String, Bool> {
        return value ? successResponse() : failResponse()
    }

    /// Returns a success response with value=true.
    public static func successResponse() -> Dictionary<String, Bool> {
        return [VALUE_RESPONSE_KEY: true]
    }

    /// Returns a failure response with value=false.
    public static func failResponse() -> Dictionary<String, Bool> {
        return [VALUE_RESPONSE_KEY: false]
    }

    /// Wraps arbitrary data into the response shape.
    public static func dataResponse(_ data: Any) -> Dictionary<String, Any> {
        return [VALUE_RESPONSE_KEY: data]
    }

    /// Wraps the recording status into the response shape.
    public static func statusResponse(_ data: CurrentRecordingStatus) -> Dictionary<String, String> {
        return [STATUS_RESPONSE_KEY: data.rawValue]
    }

}
