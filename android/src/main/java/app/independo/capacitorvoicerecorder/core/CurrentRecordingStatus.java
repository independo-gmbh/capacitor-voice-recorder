package app.independo.capacitorvoicerecorder.core;

/** Represents the current recording state. */
public enum CurrentRecordingStatus {
    RECORDING,
    PAUSED,
    INTERRUPTED,
    /**
     * The session died while it was running (see {@link RecordingFailure}).
     *
     * A state of its own rather than a flag beside `RECORDING`, because callers
     * branch on this: "not NONE" has to keep meaning "there is a file to collect",
     * so that stopping a failed session still hands back whatever was captured.
     */
    ERROR,
    NONE
}
