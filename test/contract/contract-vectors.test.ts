import fs from 'node:fs';
import path from 'node:path';

const contractPath = path.join(__dirname, 'voice-recorder-contract.json');
const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));

const isUniqueList = (values: string[]): boolean => new Set(values).size === values.length;

describe('voice recorder contract vectors', () => {
    it('defines legacy errors per platform', () => {
        expect(Array.isArray(contract.legacy.errors.web)).toBe(true);
        expect(Array.isArray(contract.legacy.errors.android)).toBe(true);
        expect(Array.isArray(contract.legacy.errors.ios)).toBe(true);
        expect(contract.legacy.errors.web.length).toBeGreaterThan(0);
        expect(contract.legacy.errors.android.length).toBeGreaterThan(0);
        expect(contract.legacy.errors.ios.length).toBeGreaterThan(0);
        expect(isUniqueList(contract.legacy.errors.web)).toBe(true);
        expect(isUniqueList(contract.legacy.errors.android)).toBe(true);
        expect(isUniqueList(contract.legacy.errors.ios)).toBe(true);
    });

    it('defines normalized errors and record data fields', () => {
        expect(Array.isArray(contract.normalized.errors)).toBe(true);
        expect(contract.normalized.errors.length).toBeGreaterThan(0);
        expect(isUniqueList(contract.normalized.errors)).toBe(true);
        expect(contract.normalized.recordData.fields).toEqual(contract.legacy.recordData.fields);
    });
});
