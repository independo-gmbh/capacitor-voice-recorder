package app.independo.capacitorvoicerecorder;

import android.Manifest;
import app.independo.capacitorvoicerecorder.adapters.PermissionChecker;
import app.independo.capacitorvoicerecorder.adapters.RecordDataMapper;
import app.independo.capacitorvoicerecorder.adapters.RecorderPlatform;
import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.Messages;
import app.independo.capacitorvoicerecorder.core.RecordData;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.core.ResponseFormat;
import app.independo.capacitorvoicerecorder.core.ResponseGenerator;
import app.independo.capacitorvoicerecorder.platform.DefaultRecorderPlatform;
import app.independo.capacitorvoicerecorder.service.VoiceRecorderService;
import app.independo.capacitorvoicerecorder.service.VoiceRecorderServiceException;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "VoiceRecorder",
    permissions = { @Permission(alias = VoiceRecorder.RECORD_AUDIO_ALIAS, strings = { Manifest.permission.RECORD_AUDIO }) }
)
/** Capacitor bridge for the VoiceRecorder plugin. */
public class VoiceRecorder extends Plugin {

    /** Permission alias used by the Capacitor permission API. */
    static final String RECORD_AUDIO_ALIAS = "voice recording";
    /** Service layer that owns recording flows and validation. */
    private VoiceRecorderService service;
    /** Response format derived from plugin configuration. */
    private ResponseFormat responseFormat;

    @Override
    public void load() {
        super.load();
        responseFormat = ResponseFormat.fromConfig(getConfig());
        RecorderPlatform platform = new DefaultRecorderPlatform(getContext());
        PermissionChecker permissionChecker = this::doesUserGaveAudioRecordingPermission;
        service = new VoiceRecorderService(platform, permissionChecker);
    }

    /** Checks whether the device can record audio. */
    @PluginMethod
    public void canDeviceVoiceRecord(PluginCall call) {
        call.resolve(ResponseGenerator.fromBoolean(service.canDeviceVoiceRecord()));
    }

    /** Requests microphone permission or returns success if already granted. */
    @PluginMethod
    public void requestAudioRecordingPermission(PluginCall call) {
        if (service.hasAudioRecordingPermission()) {
            call.resolve(ResponseGenerator.successResponse());
        } else {
            requestPermissionForAlias(RECORD_AUDIO_ALIAS, call, "recordAudioPermissionCallback");
        }
    }

    /** Forwards permission results back to the JS call. */
    @PermissionCallback
    private void recordAudioPermissionCallback(PluginCall call) {
        this.hasAudioRecordingPermission(call);
    }

    /** Returns whether the app has microphone permission. */
    @PluginMethod
    public void hasAudioRecordingPermission(PluginCall call) {
        call.resolve(ResponseGenerator.fromBoolean(service.hasAudioRecordingPermission()));
    }

    /** Starts a recording session. */
    @PluginMethod
    public void startRecording(PluginCall call) {
        try {
            String directory = call.getString("directory");
            String subDirectory = call.getString("subDirectory");
            RecordOptions options = new RecordOptions(directory, subDirectory);
            service.startRecording(
                options,
                () -> notifyListeners("voiceRecordingInterrupted", null),
                () -> notifyListeners("voiceRecordingInterruptionEnded", null)
            );
            call.resolve(ResponseGenerator.successResponse());
        } catch (VoiceRecorderServiceException exp) {
            call.reject(toLegacyMessage(exp.getCode()), exp.getCode(), exp);
        }
    }

    /** Stops recording and returns the recording payload. */
    @PluginMethod
    public void stopRecording(PluginCall call) {
        try {
            RecordData recordData = service.stopRecording();
            if (responseFormat == ResponseFormat.NORMALIZED) {
                call.resolve(ResponseGenerator.dataResponse(RecordDataMapper.toNormalizedJSObject(recordData)));
            } else {
                call.resolve(ResponseGenerator.dataResponse(RecordDataMapper.toLegacyJSObject(recordData)));
            }
        } catch (VoiceRecorderServiceException exp) {
            call.reject(toLegacyMessage(exp.getCode()), exp.getCode(), exp);
        }
    }

    /** Pauses an active recording session if supported. */
    @PluginMethod
    public void pauseRecording(PluginCall call) {
        try {
            call.resolve(ResponseGenerator.fromBoolean(service.pauseRecording()));
        } catch (VoiceRecorderServiceException exception) {
            call.reject(toLegacyMessage(exception.getCode()), exception.getCode(), exception);
        }
    }

    /** Resumes a paused recording session if supported. */
    @PluginMethod
    public void resumeRecording(PluginCall call) {
        try {
            call.resolve(ResponseGenerator.fromBoolean(service.resumeRecording()));
        } catch (VoiceRecorderServiceException exception) {
            call.reject(toLegacyMessage(exception.getCode()), exception.getCode(), exception);
        }
    }

    /** Returns the current recording status. */
    @PluginMethod
    public void getCurrentStatus(PluginCall call) {
        call.resolve(ResponseGenerator.statusResponse(service.getCurrentStatus()));
    }

    /** Checks whether the app has the RECORD_AUDIO permission. */
    private boolean doesUserGaveAudioRecordingPermission() {
        return getPermissionState(VoiceRecorder.RECORD_AUDIO_ALIAS).equals(PermissionState.GRANTED);
    }

    /** Maps canonical error codes back to legacy error messages. */
    private String toLegacyMessage(String canonicalCode) {
        if (ErrorCodes.DEVICE_CANNOT_VOICE_RECORD.equals(canonicalCode)) {
            return Messages.CANNOT_RECORD_ON_THIS_PHONE;
        }
        return canonicalCode;
    }
}
