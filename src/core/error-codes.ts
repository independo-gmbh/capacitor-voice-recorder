/** Maps legacy error messages to canonical error codes. */
const legacyToCanonical: Record<string, string> = {
    CANNOT_RECORD_ON_THIS_PHONE: 'DEVICE_CANNOT_VOICE_RECORD',
};

/** Normalizes legacy error messages into canonical error codes. */
export const toCanonicalErrorCode = (legacyMessage: string): string => {
    return legacyToCanonical[legacyMessage] ?? legacyMessage;
};

/** Adds a canonical `code` field to Error-like objects when possible. */
export const attachCanonicalErrorCode = (error: unknown): void => {
    if (!error || typeof error !== 'object') {
        return;
    }
    const messageValue = (error as { message?: unknown }).message;
    if (typeof messageValue !== 'string') {
        return;
    }
    (error as { code?: string }).code = toCanonicalErrorCode(messageValue);
};
