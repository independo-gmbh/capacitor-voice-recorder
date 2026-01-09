import Foundation

/// Maps record data into legacy or normalized dictionary payloads.
struct RecordDataMapper {
    /// Converts record data to the legacy payload shape.
    static func toLegacyDictionary(_ recordData: RecordData) -> Dictionary<String, Any> {
        return recordData.toDictionary()
    }

    /// Converts record data to the normalized payload shape.
    static func toNormalizedDictionary(_ recordData: RecordData) -> Dictionary<String, Any> {
        var normalized: Dictionary<String, Any> = [
            "msDuration": recordData.msDuration,
            "mimeType": recordData.mimeType,
        ]

        if let uri = normalizedUri(from: recordData.uri) {
            normalized["uri"] = uri
        } else if let base64 = recordData.recordDataBase64, !base64.isEmpty {
            normalized["recordDataBase64"] = base64
        }

        return normalized
    }

    /// Normalizes legacy file paths into file:// URIs.
    private static func normalizedUri(from legacyUri: String?) -> String? {
        guard let legacyUri = legacyUri, !legacyUri.isEmpty else {
            return nil
        }

        if legacyUri.hasPrefix("file://") {
            return legacyUri
        }

        return URL(fileURLWithPath: legacyUri).absoluteString
    }
}
