package app.independo.capacitorvoicerecorder.adapters;

import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.platform.NotSupportedOsVersion;
import java.io.File;
import java.util.function.Consumer;

/** Recorder abstraction used by the service layer. */
public interface RecorderAdapter {
    /** Sets a callback invoked when interruptions begin. */
    void setOnInterruptionBegan(Runnable callback);

    /** Sets a callback invoked when interruptions end. */
    void setOnInterruptionEnded(Runnable callback);

    /** Sets the callback for real-time volume updates. */
    void setOnVolumeChanged(Consumer<Float> callback);

    /** Starts recording audio. */
    void startRecording();

    /** Stops recording audio. */
    void stopRecording();

    /** Pauses recording if supported. */
    boolean pauseRecording() throws NotSupportedOsVersion;

    /** Resumes recording if supported. */
    boolean resumeRecording() throws NotSupportedOsVersion;

    /** Returns the current recording status. */
    CurrentRecordingStatus getCurrentStatus();

    /** Returns the output file for the recording. */
    File getOutputFile();

    /** Returns the options used to start recording. */
    RecordOptions getRecordOptions();

    /** Deletes the output file from disk. */
    boolean deleteOutputFile();
}
