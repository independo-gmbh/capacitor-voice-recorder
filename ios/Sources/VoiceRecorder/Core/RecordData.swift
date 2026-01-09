import Foundation

/// Recording payload returned to the bridge layer.
struct RecordData {

    /// Base64-encoded recording data (legacy payloads).
    public let recordDataBase64: String?
    /// MIME type of the recorded audio.
    public let mimeType: String
    /// Recording duration in milliseconds.
    public let msDuration: Int
    /// File path or URI to the recorded audio.
    public let uri: String?

    /// Serializes record data into the legacy payload shape.
    public func toDictionary() -> Dictionary<String, Any> {
        return [
            "recordDataBase64": recordDataBase64 ?? "",
            "msDuration": msDuration,
            "mimeType": mimeType,
            "uri": uri ?? ""
        ]
    }

}
