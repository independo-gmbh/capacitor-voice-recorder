/** Supported response shapes for plugin results. */
export type ResponseFormat = 'legacy' | 'normalized';

/** Default response shape when no config is provided. */
export const DEFAULT_RESPONSE_FORMAT: ResponseFormat = 'legacy';

/** Parses a user-provided response format into a supported value. */
export const resolveResponseFormat = (value: unknown): ResponseFormat => {
    if (typeof value === 'string' && value.toLowerCase() === 'normalized') {
        return 'normalized';
    }
    return DEFAULT_RESPONSE_FORMAT;
};

/** Reads the response format from a Capacitor plugin config object. */
export const getResponseFormatFromConfig = (config: unknown): ResponseFormat => {
    if (config && typeof config === 'object' && 'responseFormat' in config) {
        return resolveResponseFormat((config as { responseFormat?: unknown }).responseFormat);
    }
    return DEFAULT_RESPONSE_FORMAT;
};
