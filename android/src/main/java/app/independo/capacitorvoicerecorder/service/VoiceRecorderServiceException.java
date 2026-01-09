package app.independo.capacitorvoicerecorder.service;

/** Exception wrapper that carries a canonical error code. */
public class VoiceRecorderServiceException extends Exception {

    /** Canonical error code mapped to plugin responses. */
    private final String code;

    public VoiceRecorderServiceException(String code) {
        super(code);
        this.code = code;
    }

    public VoiceRecorderServiceException(String code, Exception cause) {
        super(code, cause);
        this.code = code;
    }

    /** Returns the canonical error code for this failure. */
    public String getCode() {
        return code;
    }
}
