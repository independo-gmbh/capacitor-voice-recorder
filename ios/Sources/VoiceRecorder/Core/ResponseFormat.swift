import Foundation
import Capacitor

/// Supported response payload shapes.
enum ResponseFormat: String {
    case legacy
    case normalized

    /// Reads the response format from plugin configuration.
    init(config: PluginConfig) {
        let value = config.getString("responseFormat", "legacy") ?? "legacy"
        self = ResponseFormat(rawValue: value.lowercased()) ?? .legacy
    }
}
