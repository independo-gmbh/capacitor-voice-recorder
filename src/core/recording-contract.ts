import type { RecordingData } from '../definitions';

/** Normalizes recording payloads into a stable contract shape. */
export const normalizeRecordingData = (data: RecordingData): RecordingData => {
    const { recordDataBase64, uri, msDuration, mimeType } = data.value;
    const normalizedValue: Record<string, unknown> = { msDuration, mimeType };
    const trimmedUri = typeof uri === 'string' && uri.length > 0 ? uri : undefined;
    const trimmedBase64 = typeof recordDataBase64 === 'string' && recordDataBase64.length > 0 ? recordDataBase64 : undefined;

    if (trimmedUri) {
        normalizedValue.uri = trimmedUri;
    } else if (trimmedBase64) {
        normalizedValue.recordDataBase64 = trimmedBase64;
    }

    return { value: normalizedValue } as RecordingData;
};
