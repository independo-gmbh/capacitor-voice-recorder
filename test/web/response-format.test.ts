import { getResponseFormatFromConfig, resolveResponseFormat } from '../../src/core/response-format';

describe('response format helpers', () => {
    it('resolves normalized format case-insensitively', () => {
        expect(resolveResponseFormat('normalized')).toBe('normalized');
        expect(resolveResponseFormat('Normalized')).toBe('normalized');
        expect(resolveResponseFormat('NORMALIZED')).toBe('normalized');
    });

    it('defaults to legacy for unsupported values', () => {
        expect(resolveResponseFormat('legacy')).toBe('legacy');
        expect(resolveResponseFormat('unknown')).toBe('legacy');
        expect(resolveResponseFormat(42)).toBe('legacy');
    });

    it('reads responseFormat from config objects', () => {
        expect(getResponseFormatFromConfig({ responseFormat: 'normalized' })).toBe('normalized');
        expect(getResponseFormatFromConfig({ responseFormat: 'LEGACY' })).toBe('legacy');
        expect(getResponseFormatFromConfig({})).toBe('legacy');
        expect(getResponseFormatFromConfig(undefined)).toBe('legacy');
    });
});
