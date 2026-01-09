import Foundation
import Capacitor

/// Supported response payload shapes.
enum ResponseFormat: String {
    case legacy
    case normalized

    /// Converts a raw config value into a response format.
    static func from(value: String?) -> ResponseFormat {
        guard let value = value?.lowercased(), value == "normalized" else {
            return .legacy
        }
        return .normalized
    }

    /// Reads the response format from plugin configuration.
    init(config: PluginConfig) {
        let value = config.getString("responseFormat", "legacy") ?? "legacy"
        self = ResponseFormat.from(value: value)
    }
}
