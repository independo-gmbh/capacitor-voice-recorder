package app.independo.capacitorvoicerecorder.core;

import com.getcapacitor.JSObject;

/** Helper for building JS payloads in the legacy response shape. */
public class ResponseGenerator {

    private static final String VALUE_RESPONSE_KEY = "value";
    private static final String STATUS_RESPONSE_KEY = "status";

    /** Wraps a boolean value into the response shape. */
    public static JSObject fromBoolean(boolean value) {
        return value ? successResponse() : failResponse();
    }

    /** Returns a success response with value=true. */
    public static JSObject successResponse() {
        JSObject success = new JSObject();
        success.put(VALUE_RESPONSE_KEY, true);
        return success;
    }

    /** Returns a failure response with value=false. */
    public static JSObject failResponse() {
        JSObject success = new JSObject();
        success.put(VALUE_RESPONSE_KEY, false);
        return success;
    }

    /** Wraps arbitrary data into the response shape. */
    public static JSObject dataResponse(Object data) {
        JSObject success = new JSObject();
        success.put(VALUE_RESPONSE_KEY, data);
        return success;
    }

    /** Wraps the recording status into the response shape. */
    public static JSObject statusResponse(CurrentRecordingStatus status) {
        JSObject success = new JSObject();
        success.put(STATUS_RESPONSE_KEY, status.name());
        return success;
    }
}
