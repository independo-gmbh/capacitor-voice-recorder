import {Filesystem} from '@capacitor/filesystem';
import write_blob from 'capacitor-blob-writer';

import type {
    Base64String,
    CurrentRecordingStatus,
    GenericResponse,
    RecordingData,
    RecordingOptions,
} from '../../definitions';

import getBlobDuration from './get-blob-duration';
import {
    alreadyRecordingError,
    couldNotQueryPermissionStatusError,
    deviceCannotVoiceRecordError,
    emptyRecordingError,
    failedToFetchRecordingError,
    failedToRecordError,
    failureResponse,
    missingPermissionError,
    recordingHasNotStartedError,
    successResponse,
} from './predefined-web-responses';

/** Preferred MIME types to probe in order of fallback. */
const POSSIBLE_MIME_TYPES = {
    'audio/aac': '.aac',
    'audio/webm;codecs=opus': '.ogg',
    'audio/mp4': '.mp3',
    'audio/webm': '.ogg',
    'audio/ogg;codecs=opus': '.ogg',
};

/** Creates a promise that never resolves. */
const neverResolvingPromise = (): Promise<any> => new Promise(() => undefined);

/** Browser implementation backed by MediaRecorder and Capacitor Filesystem. */
export class VoiceRecorderImpl {
    /** Active MediaRecorder instance, if recording. */
    private mediaRecorder: MediaRecorder | null = null;
    /** Collected data chunks from MediaRecorder. */
    private chunks: any[] = [];
    /** Promise resolved when the recorder stops and payload is ready. */
    private pendingResult: Promise<RecordingData> = neverResolvingPromise();

    /** Returns whether the browser can start a recording session. */
    public static async canDeviceVoiceRecord(): Promise<GenericResponse> {
        if (navigator?.mediaDevices?.getUserMedia == null || VoiceRecorderImpl.getSupportedMimeType() == null) {
            return failureResponse();
        } else {
            return successResponse();
        }
    }

    /** Starts a recording session using MediaRecorder. */
    public async startRecording(options?: RecordingOptions): Promise<GenericResponse> {
        if (this.mediaRecorder != null) {
            throw alreadyRecordingError();
        }
        const deviceCanRecord = await VoiceRecorderImpl.canDeviceVoiceRecord();
        if (!deviceCanRecord.value) {
            throw deviceCannotVoiceRecordError();
        }
        const havingPermission = await VoiceRecorderImpl.hasAudioRecordingPermission().catch(() => successResponse());
        if (!havingPermission.value) {
            throw missingPermissionError();
        }

        return navigator.mediaDevices
            .getUserMedia({audio: true})
            .then((stream) => this.onSuccessfullyStartedRecording(stream, options))
            .catch(this.onFailedToStartRecording.bind(this));
    }

    /** Stops the current recording and resolves the pending payload. */
    public async stopRecording(): Promise<RecordingData> {
        if (this.mediaRecorder == null) {
            throw recordingHasNotStartedError();
        }
        try {
            this.mediaRecorder.stop();
            this.mediaRecorder.stream.getTracks().forEach((track) => track.stop());
            return this.pendingResult;
        } catch (ignore) {
            throw failedToFetchRecordingError();
        } finally {
            this.prepareInstanceForNextOperation();
        }
    }

    /** Returns whether the browser has microphone permission. */
    public static async hasAudioRecordingPermission(): Promise<GenericResponse> {
        // Safari does not support navigator.permissions.query
        if (!navigator.permissions.query) {
            if (navigator.mediaDevices !== undefined) {
                return navigator.mediaDevices
                    .getUserMedia({audio: true})
                    .then(() => successResponse())
                    .catch(() => {
                        throw couldNotQueryPermissionStatusError();
                    });
            }
        }
        return navigator.permissions
            .query({name: 'microphone' as any})
            .then((result) => ({value: result.state === 'granted'}))
            .catch(() => {
                throw couldNotQueryPermissionStatusError();
            });
    }

    /** Requests microphone permission from the browser. */
    public static async requestAudioRecordingPermission(): Promise<GenericResponse> {
        const havingPermission = await VoiceRecorderImpl.hasAudioRecordingPermission().catch(() => failureResponse());
        if (havingPermission.value) {
            return successResponse();
        }

        return navigator.mediaDevices
            .getUserMedia({audio: true})
            .then(() => successResponse())
            .catch(() => failureResponse());
    }

    /** Pauses the recording session when supported. */
    public pauseRecording(): Promise<GenericResponse> {
        if (this.mediaRecorder == null) {
            throw recordingHasNotStartedError();
        } else if (this.mediaRecorder.state === 'recording') {
            this.mediaRecorder.pause();
            return Promise.resolve(successResponse());
        } else {
            return Promise.resolve(failureResponse());
        }
    }

    /** Resumes a paused recording session when supported. */
    public resumeRecording(): Promise<GenericResponse> {
        if (this.mediaRecorder == null) {
            throw recordingHasNotStartedError();
        } else if (this.mediaRecorder.state === 'paused') {
            this.mediaRecorder.resume();
            return Promise.resolve(successResponse());
        } else {
            return Promise.resolve(failureResponse());
        }
    }

    /** Returns the current recording status from MediaRecorder. */
    public getCurrentStatus(): Promise<CurrentRecordingStatus> {
        if (this.mediaRecorder == null) {
            return Promise.resolve({status: 'NONE'});
        } else if (this.mediaRecorder.state === 'recording') {
            return Promise.resolve({status: 'RECORDING'});
        } else if (this.mediaRecorder.state === 'paused') {
            return Promise.resolve({status: 'PAUSED'});
        } else {
            return Promise.resolve({status: 'NONE'});
        }
    }

    /** Returns the first supported MIME type, if any. */
    public static getSupportedMimeType<T extends keyof typeof POSSIBLE_MIME_TYPES>(): T | null {
        if (MediaRecorder?.isTypeSupported == null) return null;

        const foundSupportedType = Object.keys(POSSIBLE_MIME_TYPES).find((type) => MediaRecorder.isTypeSupported(type)) as
            | T
            | undefined;

        return foundSupportedType ?? null;
    }

    /** Initializes MediaRecorder and wires up handlers. */
    private onSuccessfullyStartedRecording(stream: MediaStream, options?: RecordingOptions): GenericResponse {
        this.pendingResult = new Promise((resolve, reject) => {
            this.mediaRecorder = new MediaRecorder(stream);
            this.mediaRecorder.onerror = () => {
                this.prepareInstanceForNextOperation();
                reject(failedToRecordError());
            };
            this.mediaRecorder.onstop = async () => {
                const mimeType = VoiceRecorderImpl.getSupportedMimeType();
                if (mimeType == null) {
                    this.prepareInstanceForNextOperation();
                    reject(failedToFetchRecordingError());
                    return;
                }
                const blobVoiceRecording = new Blob(this.chunks, {type: mimeType});
                if (blobVoiceRecording.size <= 0) {
                    this.prepareInstanceForNextOperation();
                    reject(emptyRecordingError());
                    return;
                }

                let uri: string | undefined = undefined;
                let recordDataBase64 = '';
                if (options?.directory) {
                    const subDirectory = options.subDirectory?.match(/^\/?(.+[^/])\/?$/)?.[1] ?? '';
                    const path = `${subDirectory}/recording-${new Date().getTime()}${POSSIBLE_MIME_TYPES[mimeType]}`;

                    await write_blob({
                        blob: blobVoiceRecording,
                        directory: options.directory,
                        fast_mode: true,
                        path,
                        recursive: true,
                    });

                    ({uri} = await Filesystem.getUri({directory: options.directory, path}));
                } else {
                    recordDataBase64 = await VoiceRecorderImpl.blobToBase64(blobVoiceRecording);
                }

                const recordingDuration = await getBlobDuration(blobVoiceRecording);
                this.prepareInstanceForNextOperation();
                resolve({value: {recordDataBase64, mimeType, msDuration: recordingDuration * 1000, uri}});
            };
            this.mediaRecorder.ondataavailable = (event: any) => this.chunks.push(event.data);
            this.mediaRecorder.start();
        });
        return successResponse();
    }

    /** Handles failures from getUserMedia. */
    private onFailedToStartRecording(): GenericResponse {
        this.prepareInstanceForNextOperation();
        throw failedToRecordError();
    }

    /** Converts a Blob payload into a base64 string. */
    private static blobToBase64(blob: Blob): Promise<Base64String> {
        return new Promise((resolve) => {
            const reader = new FileReader();
            reader.onloadend = () => {
                const recordingResult = String(reader.result);
                const splitResult = recordingResult.split('base64,');
                const toResolve = splitResult.length > 1 ? splitResult[1] : recordingResult;
                resolve(toResolve.trim());
            };
            reader.readAsDataURL(blob);
        });
    }

    /** Resets state for the next recording attempt. */
    private prepareInstanceForNextOperation(): void {
        if (this.mediaRecorder != null && this.mediaRecorder.state === 'recording') {
            try {
                this.mediaRecorder.stop();
            } catch (ignore) {
                console.warn('Failed to stop recording during cleanup');
            }
        }
        this.pendingResult = neverResolvingPromise();
        this.mediaRecorder = null;
        this.chunks = [];
    }
}
