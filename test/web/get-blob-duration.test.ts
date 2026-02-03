import { base64ToArrayBuffer } from '../../src/platform/web/get-blob-duration';

describe('base64ToArrayBuffer', () => {
    it('correctly converts a valid base64 string to ArrayBuffer', () => {
        // "Hello" in base64
        const base64 = 'SGVsbG8=';
        const buffer = base64ToArrayBuffer(base64);
        const uint8 = new Uint8Array(buffer);
        expect(uint8).toEqual(new Uint8Array([72, 101, 108, 108, 111]));
    });

    it('correctly converts a valid base64 string with data URI prefix', () => {
        const base64 = 'data:audio/aac;base64,QUJDRA=='; // "ABCD"
        const buffer = base64ToArrayBuffer(base64);
        const uint8 = new Uint8Array(buffer);
        expect(uint8).toEqual(new Uint8Array([65, 66, 67, 68]));
    });

    it('returns an empty ArrayBuffer for empty string', () => {
        const buffer = base64ToArrayBuffer('');
        expect(buffer.byteLength).toBe(0);
    });

    it('throws for invalid base64 input', () => {
        expect(() => base64ToArrayBuffer('!@#$%^&*()')).toThrow();
    });
});
