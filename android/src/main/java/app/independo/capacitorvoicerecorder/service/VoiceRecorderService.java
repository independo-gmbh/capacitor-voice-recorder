package app.independo.capacitorvoicerecorder.service;

import app.independo.capacitorvoicerecorder.adapters.PermissionChecker;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.adapters.RecorderPlatform;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.RecordData;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.platform.NotSupportedOsVersion;
import java.io.File;
import java.util.function.Consumer;

/** Service layer that orchestrates recording operations. */
public class VoiceRecorderService {

    /** Platform adapter that owns file and recorder creation. */
    private final RecorderPlatform platform;
    /** Permission checker injected from the bridge layer. */
    private final PermissionChecker permissionChecker;
    /** Current recorder instance for an active session. */
    private RecorderAdapter recorder;

    public VoiceRecorderService(RecorderPlatform platform, PermissionChecker permissionChecker) {
        this.platform = platform;
        this.permissionChecker = permissionChecker;
    }

    /** Returns whether the device can record audio. */
    public boolean canDeviceVoiceRecord() {
        return platform.canDeviceVoiceRecord();
    }

    /** Returns whether the app has microphone permission. */
    public boolean hasAudioRecordingPermission() {
        return permissionChecker.hasAudioPermission();
    }

    /** Starts a recording session or throws a service exception. */
    public void startRecording(
        RecordOptions options,
        Runnable onInterruptionBegan,
        Runnable onInterruptionEnded,
        Consumer<Float> onVolumeChanged
    ) throws VoiceRecorderServiceException {
        if (!platform.canDeviceVoiceRecord()) {
            throw new VoiceRecorderServiceException(ErrorCodes.DEVICE_CANNOT_VOICE_RECORD);
        }

        if (!permissionChecker.hasAudioPermission()) {
            throw new VoiceRecorderServiceException(ErrorCodes.MISSING_PERMISSION);
        }

        if (platform.isMicrophoneOccupied()) {
            throw new VoiceRecorderServiceException(ErrorCodes.MICROPHONE_BEING_USED);
        }

        if (recorder != null) {
            throw new VoiceRecorderServiceException(ErrorCodes.ALREADY_RECORDING);
        }

        try {
            recorder = platform.createRecorder(options);
            recorder.setOnInterruptionBegan(onInterruptionBegan);
            recorder.setOnInterruptionEnded(onInterruptionEnded);
            recorder.setOnVolumeChanged(onVolumeChanged);
            recorder.startRecording();
        } catch (Exception exp) {
            recorder = null;
            throw new VoiceRecorderServiceException(ErrorCodes.FAILED_TO_RECORD, exp);
        }
    }

    /** Stops the active recording session and returns the payload. */
    public RecordData stopRecording() throws VoiceRecorderServiceException {
        if (recorder == null) {
            throw new VoiceRecorderServiceException(ErrorCodes.RECORDING_HAS_NOT_STARTED);
        }

        RecordOptions options = recorder.getRecordOptions();

        try {
            recorder.stopRecording();
            File recordedFile = recorder.getOutputFile();
            if (recordedFile == null) {
                throw new VoiceRecorderServiceException(ErrorCodes.FAILED_TO_FETCH_RECORDING);
            }

            String recordDataBase64 = null;
            String uri = null;
            if (options.directory() != null) {
                uri = platform.toUri(recordedFile);
            } else {
                recordDataBase64 = platform.readFileAsBase64(recordedFile);
            }

            int duration = platform.getDurationMs(recordedFile);
            RecordData recordData = new RecordData(recordDataBase64, duration, "audio/aac", uri);
            if ((recordDataBase64 == null && uri == null) || recordData.getMsDuration() < 0) {
                throw new VoiceRecorderServiceException(ErrorCodes.EMPTY_RECORDING);
            }

            return recordData;
        } catch (VoiceRecorderServiceException exp) {
            throw exp;
        } catch (Exception exp) {
            throw new VoiceRecorderServiceException(ErrorCodes.FAILED_TO_FETCH_RECORDING, exp);
        } finally {
            if (options.directory() == null && recorder != null) {
                recorder.deleteOutputFile();
            }
            recorder = null;
        }
    }

    /** Pauses the active recording session. */
    public boolean pauseRecording() throws VoiceRecorderServiceException {
        if (recorder == null) {
            throw new VoiceRecorderServiceException(ErrorCodes.RECORDING_HAS_NOT_STARTED);
        }
        try {
            return recorder.pauseRecording();
        } catch (NotSupportedOsVersion exception) {
            throw new VoiceRecorderServiceException(ErrorCodes.NOT_SUPPORTED_OS_VERSION, exception);
        }
    }

    /** Resumes a paused recording session. */
    public boolean resumeRecording() throws VoiceRecorderServiceException {
        if (recorder == null) {
            throw new VoiceRecorderServiceException(ErrorCodes.RECORDING_HAS_NOT_STARTED);
        }
        try {
            return recorder.resumeRecording();
        } catch (NotSupportedOsVersion exception) {
            throw new VoiceRecorderServiceException(ErrorCodes.NOT_SUPPORTED_OS_VERSION, exception);
        }
    }

    /** Returns the current recording status. */
    public CurrentRecordingStatus getCurrentStatus() {
        if (recorder == null) {
            return CurrentRecordingStatus.NONE;
        }
        return recorder.getCurrentStatus();
    }
}
