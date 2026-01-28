import { Capacitor, WebPlugin } from '@capacitor/core';

import { VoiceRecorderWebAdapter } from './adapters/VoiceRecorderWebAdapter';
import { getResponseFormatFromConfig } from './core/response-format';
import type {
  CurrentRecordingStatus,
  GenericResponse,
  RecordingData,
  RecordingOptions,
  VoiceRecorderPlugin,
} from './definitions';
import { VoiceRecorderService } from './service/VoiceRecorderService';

/** Web implementation of the VoiceRecorder Capacitor plugin. */
export class VoiceRecorderWeb extends WebPlugin implements VoiceRecorderPlugin {
  /** Service layer that normalizes behavior and errors. */
  private readonly service: VoiceRecorderService;

  public constructor() {
    super();
    const pluginConfig = (Capacitor as any)?.config?.plugins?.VoiceRecorder;
    const responseFormat = getResponseFormatFromConfig(pluginConfig);
    this.service = new VoiceRecorderService(new VoiceRecorderWebAdapter(), responseFormat);
  }

  /** Checks whether the browser can record audio. */
  public canDeviceVoiceRecord(): Promise<GenericResponse> {
    return this.service.canDeviceVoiceRecord();
  }

  /** Returns whether microphone permission is currently granted. */
  public hasAudioRecordingPermission(): Promise<GenericResponse> {
    return this.service.hasAudioRecordingPermission();
  }

  /** Requests microphone permission from the user. */
  public requestAudioRecordingPermission(): Promise<GenericResponse> {
    return this.service.requestAudioRecordingPermission();
  }

  /** Starts a recording session. */
  public startRecording(options?: RecordingOptions): Promise<GenericResponse> {
    return this.service.startRecording(
      options,
      volume => this.notifyListeners('volumeChanged', { volume }),
    );
  }

  /** Stops the current recording session and returns the payload. */
  public stopRecording(): Promise<RecordingData> {
    return this.service.stopRecording();
  }

  /** Pauses the recording session when supported. */
  public pauseRecording(): Promise<GenericResponse> {
    return this.service.pauseRecording();
  }

  /** Resumes a paused recording session when supported. */
  public resumeRecording(): Promise<GenericResponse> {
    return this.service.resumeRecording();
  }

  /** Returns the current recording state. */
  public getCurrentStatus(): Promise<CurrentRecordingStatus> {
    return this.service.getCurrentStatus();
  }
}
