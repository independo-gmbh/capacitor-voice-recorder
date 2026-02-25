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

/**
 * Ordered MIME types to probe for audio recording via `MediaRecorder.isTypeSupported()`.
 *
 * ⚠️ The order is intentional and MUST remain stable unless you also update the
 * selection policy in code and test on Safari/iOS + WebViews.
 *
 * ✅ What this list is used for
 * - Selecting a `mimeType` for `new MediaRecorder(stream, { mimeType })`.
 *
 * ❌ What this list does NOT guarantee
 * - It does NOT guarantee that the recorded output will be playable via the
 *   HTML `<audio>` element in the same browser.
 *
 * Real-world caveat (important):
 * - We have observed cases where `MediaRecorder.isTypeSupported('audio/webm;codecs=opus')`
 *   returned `true`, the recorder produced a Blob, but `<audio>` could not play it.
 *   This can happen due to container/codec playback support differences, platform
 *   quirks (especially Safari/iOS / WKWebView), or incomplete WebM playback support.
 *
 * Current selection behavior in this implementation:
 * - By default, MIME selection treats recorder support and playback support as separate
 *   capabilities and probes both:
 *   - Recorder capability: `MediaRecorder.isTypeSupported(type)`
 *   - Playback capability: `audio.canPlayType(type)`
 * - This default can be disabled via `RecordingOptions.requirePlaybackSupport = false`
 *   to fall back to recorder-only probing.
 *
 * Keeping legacy keys:
 * - Some entries are kept even if they overlap (e.g. `audio/mp4` and explicit codec),
 *   to maximize compatibility across differing browser implementations.
 */
const POSSIBLE_MIME_TYPES: Record<string, string> = {
    // ✅ Most universal
    'audio/mp4;codecs="mp4a.40.2"': '.m4a',      // AAC in MP4 (explicit codec helps detection)
    'audio/mp4': '.m4a',                         // (legacy key kept; broad support)
    'audio/aac': '.aac',                         // (legacy key kept; less common in the wild)
    'audio/mpeg': '.mp3',                        // MP3 (universal)
    'audio/wav': '.wav',                         // WAV (universal, big files)

    // ✅ Modern high-quality (very widely supported, but slightly less “universal” than MP3/AAC)
    'audio/webm;codecs="opus"': '.webm',         // Opus in WebM (explicit codec helps detection)
    'audio/webm;codecs=opus': '.webm',           // (legacy key kept)
    'audio/webm': '.webm',                       // (legacy key kept; container-only, codec-dependent)

    // ⚠️ Least universal (Safari/iOS historically the limiting factor)
    'audio/ogg;codecs=opus': '.ogg',             // (legacy key kept)
    'audio/ogg;codecs=vorbis': '.ogg',           // Ogg Vorbis (weakest mainstream support)
};

/** Creates a promise that never resolves. */
const neverResolvingPromise = (): Promise<any> => new Promise(() => undefined);

/** Browser implementation backed by MediaRecorder and Capacitor Filesystem. */
export class VoiceRecorderImpl {
    /** Default behavior for web MIME selection: require recorder + playback support. */
    private static readonly DEFAULT_REQUIRE_PLAYBACK_SUPPORT = true;
    /** Active MediaRecorder instance, if recording. */
    private mediaRecorder: MediaRecorder | null = null;
    /** Collected data chunks from MediaRecorder. */
    private chunks: any[] = [];
    /** Promise resolved when the recorder stops and payload is ready. */
    private pendingResult: Promise<RecordingData> = neverResolvingPromise();

    /**
     * Returns whether the browser can start a recording session.
     *
     * On web this checks:
     * - `navigator.mediaDevices.getUserMedia`
     * - at least one supported recording MIME type using {@link getSupportedMimeType}
     *
     * The optional `requirePlaybackSupport` flag is forwarded to MIME selection and defaults
     * to `true` when omitted.
     */
    public static async canDeviceVoiceRecord(
        options?: Pick<RecordingOptions, 'requirePlaybackSupport'>,
    ): Promise<GenericResponse> {
        if (
            navigator?.mediaDevices?.getUserMedia == null ||
            VoiceRecorderImpl.getSupportedMimeType({
                requirePlaybackSupport: options?.requirePlaybackSupport,
            }) == null
        ) {
            return failureResponse();
        } else {
            return successResponse();
        }
    }

    /**
     * Starts a recording session using `MediaRecorder`.
     *
     * The selected MIME type is resolved once at start time (using the optional
     * `requirePlaybackSupport` flag from `RecordingOptions`) and reused for the final Blob
     * and file extension to keep the recording payload internally consistent.
     */
    public async startRecording(options?: RecordingOptions): Promise<GenericResponse> {
        if (this.mediaRecorder != null) {
            throw alreadyRecordingError();
        }
        const deviceCanRecord = await VoiceRecorderImpl.canDeviceVoiceRecord(options);
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

    /**
     * Returns the first MIME type (key of {@link POSSIBLE_MIME_TYPES}) that the current
     * environment reports as supported for recording via `MediaRecorder.isTypeSupported()`,
     * optionally requiring native HTML `<audio>` playback support too.
     *
     * The search order is the iteration order of {@link POSSIBLE_MIME_TYPES}.
     *
     * @typeParam T - A MIME type string that exists as a key in {@link POSSIBLE_MIME_TYPES}.
     *
     * @returns The first supported MIME type for `MediaRecorder`, or `null` if:
     * - `MediaRecorder` is unavailable, or
     * - no configured MIME types are supported.
     *
     * ⚠️ Important: `MediaRecorder` support ≠ `<audio>` playback support
     *
     * Some browsers/platforms can claim support for recording a format (notably WebM/Opus)
     * but still fail to play the resulting Blob through the native HTML audio pipeline.
     * This mismatch is especially likely on Safari/iOS / WKWebView variants, so the default
     * behavior also probes `HTMLAudioElement.canPlayType(type)` when available.
     *
     * Selection policy when playback probing is enabled:
     * - keep the global priority order from {@link POSSIBLE_MIME_TYPES}
     * - among recordable types, prefer the first `"probably"` playable candidate
     * - otherwise return the first `"maybe"` playable candidate
     * - treat `""` as not playable
     *
     * Note: The <audio> element is never attached to the DOM, so it won't appear to users or assistive tech.
     *
     * Fallback behavior:
     * - If `document` / `audio.canPlayType` is unavailable (e.g. SSR-like environments),
     *   this falls back to record-only probing.
     */
    public static getSupportedMimeType<T extends keyof typeof POSSIBLE_MIME_TYPES>(
        options?: { requirePlaybackSupport?: boolean },
    ): T | null {
        if (MediaRecorder?.isTypeSupported == null) return null;

        const orderedTypes = Object.keys(POSSIBLE_MIME_TYPES) as T[];
        const recordSupportedTypes = orderedTypes.filter((type) => MediaRecorder.isTypeSupported(type));
        if (recordSupportedTypes.length === 0) return null;

        const requirePlaybackSupport =
            options?.requirePlaybackSupport ?? VoiceRecorderImpl.DEFAULT_REQUIRE_PLAYBACK_SUPPORT;
        if (!requirePlaybackSupport) {
            return recordSupportedTypes[0] ?? null;
        }

        if (typeof document === 'undefined' || typeof document.createElement !== 'function') {
            return recordSupportedTypes[0] ?? null;
        }

        const audioElement = document.createElement('audio') as Partial<HTMLAudioElement>;
        if (typeof audioElement.canPlayType !== 'function') {
            return recordSupportedTypes[0] ?? null;
        }

        let firstProbably: T | null = null;
        let firstMaybe: T | null = null;

        for (const type of recordSupportedTypes) {
            const playbackSupport = audioElement.canPlayType(type);
            if (playbackSupport === 'probably') {
                firstProbably = type;
                break;
            }
            if (playbackSupport === 'maybe' && firstMaybe == null) {
                firstMaybe = type;
            }
        }

        return firstProbably ?? firstMaybe ?? null;
    }

    /** Initializes MediaRecorder and wires up handlers. */
    private onSuccessfullyStartedRecording(stream: MediaStream, options?: RecordingOptions): GenericResponse {
        this.pendingResult = new Promise((resolve, reject) => {
            const mimeType = VoiceRecorderImpl.getSupportedMimeType({
                requirePlaybackSupport: options?.requirePlaybackSupport,
            });
            if (mimeType == null) {
                this.prepareInstanceForNextOperation();
                reject(failedToRecordError());
                return;
            }

            this.mediaRecorder = new MediaRecorder(stream, {mimeType});
            this.mediaRecorder.onerror = () => {
                this.prepareInstanceForNextOperation();
                reject(failedToRecordError());
            };
            this.mediaRecorder.onstop = async () => {
                const mt = this.mediaRecorder?.mimeType ?? mimeType;
                const blobVoiceRecording = new Blob(this.chunks, {type: mt});
                if (blobVoiceRecording.size <= 0) {
                    this.prepareInstanceForNextOperation();
                    reject(emptyRecordingError());
                    return;
                }

                let uri: string | undefined = undefined;
                let recordDataBase64 = '';
                const fileExtension = (POSSIBLE_MIME_TYPES[mimeType] ?? '').replace(/^\./, '');
                if (options?.directory) {
                    const subDirectory = options.subDirectory?.match(/^\/?(.+[^/])\/?$/)?.[1] ?? '';
                    const path = `${subDirectory}/recording-${new Date().getTime()}${POSSIBLE_MIME_TYPES[mt]}`;

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
                resolve({
                    value: {
                        recordDataBase64,
                        mimeType: mt,
                        fileExtension,
                        msDuration: recordingDuration * 1000,
                        uri
                    }
                });
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
