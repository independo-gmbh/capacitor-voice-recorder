import type { GenericResponse } from '../../definitions';

/** Success wrapper for boolean plugin responses. */
export const successResponse = (): GenericResponse => ({ value: true });
/** Failure wrapper for boolean plugin responses. */
export const failureResponse = (): GenericResponse => ({ value: false });

/** Error for missing microphone permission. */
export const missingPermissionError = (): Error => new Error('MISSING_PERMISSION');
/** Error for attempting to start while already recording. */
export const alreadyRecordingError = (): Error => new Error('ALREADY_RECORDING');
/** Error for microphone in use by another app or recorder. */
export const microphoneBeingUsedError = (): Error => new Error('MICROPHONE_BEING_USED');
/** Error for devices that cannot record audio. */
export const deviceCannotVoiceRecordError = (): Error => new Error('DEVICE_CANNOT_VOICE_RECORD');
/** Error for recorder start failures. */
export const failedToRecordError = (): Error => new Error('FAILED_TO_RECORD');
/** Error for empty or zero-length recordings. */
export const emptyRecordingError = (): Error => new Error('EMPTY_RECORDING');

/** Error for stopping without an active recording. */
export const recordingHasNotStartedError = (): Error => new Error('RECORDING_HAS_NOT_STARTED');
/** Error for failures when fetching recording data. */
export const failedToFetchRecordingError = (): Error => new Error('FAILED_TO_FETCH_RECORDING');

/** Error for browsers that do not support permission queries. */
export const couldNotQueryPermissionStatusError = (): Error => new Error('COULD_NOT_QUERY_PERMISSION_STATUS');
