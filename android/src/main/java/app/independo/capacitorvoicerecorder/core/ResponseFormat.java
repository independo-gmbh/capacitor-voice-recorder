package app.independo.capacitorvoicerecorder.core;

import com.getcapacitor.PluginConfig;

/** Supported response payload shapes. */
public enum ResponseFormat {
    LEGACY,
    NORMALIZED;

    /** Reads the response format from plugin configuration. */
    public static ResponseFormat fromConfig(PluginConfig config) {
        String value = config.getString("responseFormat", "legacy");
        if ("normalized".equalsIgnoreCase(value)) {
            return NORMALIZED;
        }
        return LEGACY;
    }
}
