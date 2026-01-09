package app.independo.capacitorvoicerecorder.adapters;

import app.independo.capacitorvoicerecorder.core.RecordOptions;
import java.io.File;

/** Platform abstraction for device and file operations. */
public interface RecorderPlatform {
    /** Returns whether the device can record audio. */
    boolean canDeviceVoiceRecord();

    /** Returns true when the microphone is in use elsewhere. */
    boolean isMicrophoneOccupied();

    /** Creates a recorder instance for the given options. */
    RecorderAdapter createRecorder(RecordOptions options) throws Exception;

    /** Reads the recording file as base64, or null on failure. */
    String readFileAsBase64(File recordedFile);

    /** Returns the recording duration in milliseconds. */
    int getDurationMs(File recordedFile);

    /** Returns the URI for the recording file. */
    String toUri(File recordedFile);
}
