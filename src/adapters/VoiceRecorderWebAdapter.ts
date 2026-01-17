import type {
    CurrentRecordingStatus,
    GenericResponse,
    RecordingData,
    RecordingOptions,
} from '../definitions';
import { VoiceRecorderImpl } from '../platform/web/VoiceRecorderImpl';
import type { VoiceRecorderPlatform } from '../service/VoiceRecorderService';

/** Web adapter that delegates to the browser-specific implementation. */
export class VoiceRecorderWebAdapter implements VoiceRecorderPlatform {
    /** Browser implementation that talks to MediaRecorder APIs. */
    private readonly voiceRecorderImpl = new VoiceRecorderImpl();

    /** Checks whether the browser can record audio. */
    public canDeviceVoiceRecord(): Promise<GenericResponse> {
        return VoiceRecorderImpl.canDeviceVoiceRecord();
    }

    /** Returns whether the browser has microphone permission. */
    public hasAudioRecordingPermission(): Promise<GenericResponse> {
        return VoiceRecorderImpl.hasAudioRecordingPermission();
    }

    /** Requests microphone permission through the browser. */
    public requestAudioRecordingPermission(): Promise<GenericResponse> {
        return VoiceRecorderImpl.requestAudioRecordingPermission();
    }

    /** Starts a recording session using MediaRecorder. */
    public startRecording(options?: RecordingOptions, onVolumeChanged?: (volume: number) => void): Promise<GenericResponse> {
        return this.voiceRecorderImpl.startRecording(options, onVolumeChanged);
    }

    /** Stops the recording session and returns the payload. */
    public stopRecording(): Promise<RecordingData> {
        return this.voiceRecorderImpl.stopRecording();
    }

    /** Pauses the recording session when supported. */
    public pauseRecording(): Promise<GenericResponse> {
        return this.voiceRecorderImpl.pauseRecording();
    }

    /** Resumes a paused recording session when supported. */
    public resumeRecording(): Promise<GenericResponse> {
        return this.voiceRecorderImpl.resumeRecording();
    }

    /** Returns the current recording state. */
    public getCurrentStatus(): Promise<CurrentRecordingStatus> {
        return this.voiceRecorderImpl.getCurrentStatus();
    }
}
