package app.independo.capacitorvoicerecorder.core;

/**
 * A recording session that died while it was running.
 *
 * Not the same thing as a call that failed: every error in {@link ErrorCodes} is
 * reported back through the call that caused it, and the caller knows at once.
 * This is the other kind -- the recorder is invalidated between calls, by
 * something nobody asked for, and nothing surfaces it unless it is pushed.
 */
public record RecordingFailure(String code, String message) {
    /** The media server died and took the recording session with it. */
    public static final String SERVER_DIED = "RECORDER_SERVER_DIED";
    /** MediaRecorder reported an error it declined to be specific about. */
    public static final String UNKNOWN_ERROR = "RECORDER_UNKNOWN_ERROR";
    /**
     * Reading the input level threw, which means the recorder is gone: the
     * session was torn down (another app taking the microphone, a hardware
     * fault) without an error callback ever arriving.
     */
    public static final String AMPLITUDE_READ_FAILED = "AMPLITUDE_READ_FAILED";
    /** Pausing for an audio focus loss threw, so the recorder was already gone. */
    public static final String INTERRUPTION_FAILED = "INTERRUPTION_FAILED";
    /** Asked for, to rehearse the failure path. See `simulateFailure`. */
    public static final String SIMULATED = "SIMULATED_FAILURE";
}
