import { attachCanonicalErrorCode } from '../core/error-codes';
import { normalizeRecordingData } from '../core/recording-contract';
import type { ResponseFormat } from '../core/response-format';
import type {
    CurrentRecordingStatus,
    GenericResponse,
    RecordingData,
    RecordingOptions,
} from '../definitions';

/** Platform abstraction used by the service layer. */
export interface VoiceRecorderPlatform {
    /** Checks whether the device can record audio. */
    canDeviceVoiceRecord(): Promise<GenericResponse>;
    /** Returns whether microphone permission is currently granted. */
    hasAudioRecordingPermission(): Promise<GenericResponse>;
    /** Requests microphone permission from the user. */
    requestAudioRecordingPermission(): Promise<GenericResponse>;
    /** Starts a recording session. */
    startRecording(options?: RecordingOptions, onVolumeChanged?: (volume: number) => void): Promise<GenericResponse>;
    /** Stops the current recording session and returns the payload. */
    stopRecording(): Promise<RecordingData>;
    /** Pauses the recording session when supported. */
    pauseRecording(): Promise<GenericResponse>;
    /** Resumes a paused recording session when supported. */
    resumeRecording(): Promise<GenericResponse>;
    /** Returns the current recording state. */
    getCurrentStatus(): Promise<CurrentRecordingStatus>;
}

/** Orchestrates platform calls and normalizes responses when requested. */
export class VoiceRecorderService {
    /** Selected response format derived from plugin config. */
    private readonly responseFormat: ResponseFormat;
    /** Platform adapter that performs the actual recording work. */
    private readonly platform: VoiceRecorderPlatform;

    public constructor(platform: VoiceRecorderPlatform, responseFormat: ResponseFormat) {
        this.platform = platform;
        this.responseFormat = responseFormat;
    }

    /** Checks whether the device can record audio. */
    public canDeviceVoiceRecord(): Promise<GenericResponse> {
        return this.execute(() => this.platform.canDeviceVoiceRecord());
    }

    /** Returns whether microphone permission is currently granted. */
    public hasAudioRecordingPermission(): Promise<GenericResponse> {
        return this.execute(() => this.platform.hasAudioRecordingPermission());
    }

    /** Requests microphone permission from the user. */
    public requestAudioRecordingPermission(): Promise<GenericResponse> {
        return this.execute(() => this.platform.requestAudioRecordingPermission());
    }

    /** Starts a recording session. */
    public startRecording(options?: RecordingOptions, onVolumeChanged?: (volume: number) => void): Promise<GenericResponse> {
        return this.execute(() => this.platform.startRecording(options, onVolumeChanged));
    }

    /** Stops the recording session and formats the payload if needed. */
    public async stopRecording(): Promise<RecordingData> {
        return this.execute(async () => {
            const data = await this.platform.stopRecording();
            if (this.responseFormat === 'normalized') {
                return normalizeRecordingData(data);
            }
            return data;
        });
    }

    /** Pauses the recording session when supported. */
    public pauseRecording(): Promise<GenericResponse> {
        return this.execute(() => this.platform.pauseRecording());
    }

    /** Resumes a paused recording session when supported. */
    public resumeRecording(): Promise<GenericResponse> {
        return this.execute(() => this.platform.resumeRecording());
    }

    /** Returns the current recording state. */
    public getCurrentStatus(): Promise<CurrentRecordingStatus> {
        return this.execute(() => this.platform.getCurrentStatus());
    }

    /** Wraps calls to apply canonical error codes when requested. */
    private async execute<T>(fn: () => Promise<T>): Promise<T> {
        try {
            return await fn();
        } catch (error) {
            if (this.responseFormat === 'normalized') {
                attachCanonicalErrorCode(error);
            }
            throw error;
        }
    }
}
