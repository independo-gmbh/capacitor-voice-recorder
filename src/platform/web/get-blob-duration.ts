/**
 * @param {Blob | string} blob
 * @returns {Promise<number>} Blob duration in seconds.
 */
export default async function getBlobDuration(blob: Blob | string): Promise<number> {
    // Check for AudioContext or webkitAudioContext (Safari)
    const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
    if (!AudioCtx) {
        throw new Error('AudioContext is not supported in this environment.');
    }
    let audioContext: AudioContext | null = null;
    try {
        audioContext = new AudioCtx();
        let arrayBuffer: ArrayBuffer;
        if (typeof blob === 'string') {
            arrayBuffer = base64ToArrayBuffer(blob);
        } else {
            arrayBuffer = await blob.arrayBuffer();
        }
        const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);
        return audioBuffer.duration;
    } catch (err) {
        throw new Error('Failed to get audio duration (AudioContext may require user interaction or is not supported): ' + (err instanceof Error ? err.message : String(err)));
    } finally {
        if (audioContext) {
            await audioContext.close();
        }
    }
}

/**
 * Convert base64 string to ArrayBuffer.
 * @param base64 The base64 string to convert.
 * @returns The converted ArrayBuffer.
 * @remarks This function is exported for test coverage purposes.
 */
export function base64ToArrayBuffer(base64: string): ArrayBuffer {
    const cleanBase64 = base64.replace(/^data:[^;]+;base64,/, '');
    const binaryString = atob(cleanBase64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
        bytes[i] = binaryString.charCodeAt(i);
    }
    return bytes.buffer;
}