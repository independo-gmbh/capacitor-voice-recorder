import fs from 'node:fs';
import path from 'node:path';

const contractPath = path.join(__dirname, 'voice-recorder-contract.json');
const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));
const repoRoot = path.resolve(__dirname, '..', '..');

const definitionsPath = path.join(repoRoot, 'src', 'definitions.ts');
const androidRecordDataPath = path.join(
    repoRoot,
    'android',
    'src',
    'main',
    'java',
    'app',
    'independo',
    'capacitorvoicerecorder',
    'core',
    'RecordData.java',
);
const iosRecordDataPath = path.join(
    repoRoot,
    'ios',
    'Sources',
    'VoiceRecorder',
    'Core',
    'RecordData.swift',
);

describe('record data keys', () => {
    const fields: string[] = contract.legacy.recordData.fields;

    it('appear in src/definitions.ts', () => {
        const definitions = fs.readFileSync(definitionsPath, 'utf8');
        fields.forEach((field) => {
            expect(definitions).toContain(field);
        });
    });

    it('appear in android RecordData.java', () => {
        const androidRecordData = fs.readFileSync(androidRecordDataPath, 'utf8');
        fields.forEach((field) => {
            expect(androidRecordData).toContain(field);
        });
    });

    it('appear in ios RecordData.swift', () => {
        const iosRecordData = fs.readFileSync(iosRecordDataPath, 'utf8');
        fields.forEach((field) => {
            expect(iosRecordData).toContain(field);
        });
    });
});
