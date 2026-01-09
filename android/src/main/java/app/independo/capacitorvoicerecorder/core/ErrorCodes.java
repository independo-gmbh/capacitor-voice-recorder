package app.independo.capacitorvoicerecorder.core;

/** Canonical error codes returned by the plugin. */
public final class ErrorCodes {

    public static final String MISSING_PERMISSION = "MISSING_PERMISSION";
    public static final String ALREADY_RECORDING = "ALREADY_RECORDING";
    public static final String MICROPHONE_BEING_USED = "MICROPHONE_BEING_USED";
    public static final String DEVICE_CANNOT_VOICE_RECORD = "DEVICE_CANNOT_VOICE_RECORD";
    public static final String FAILED_TO_RECORD = "FAILED_TO_RECORD";
    public static final String EMPTY_RECORDING = "EMPTY_RECORDING";
    public static final String RECORDING_HAS_NOT_STARTED = "RECORDING_HAS_NOT_STARTED";
    public static final String FAILED_TO_FETCH_RECORDING = "FAILED_TO_FETCH_RECORDING";
    public static final String FAILED_TO_MERGE_RECORDING = "FAILED_TO_MERGE_RECORDING";
    public static final String NOT_SUPPORTED_OS_VERSION = "NOT_SUPPORTED_OS_VERSION";
    public static final String COULD_NOT_QUERY_PERMISSION_STATUS = "COULD_NOT_QUERY_PERMISSION_STATUS";

    private ErrorCodes() {}
}
