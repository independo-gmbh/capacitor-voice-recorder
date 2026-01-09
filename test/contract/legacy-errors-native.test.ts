import fs from 'node:fs';
import path from 'node:path';

const contractPath = path.join(__dirname, 'voice-recorder-contract.json');
const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));
const repoRoot = path.resolve(__dirname, '..', '..');

const androidMessagesPath = path.join(
    repoRoot,
    'android',
    'src',
    'main',
    'java',
    'com',
    'tchvu3',
    'capacitorvoicerecorder',
    'Messages.java',
);
const iosMessagesPath = path.join(
    repoRoot,
    'ios',
    'Sources',
    'VoiceRecorder',
    'Messages.swift',
);

describe('native legacy error messages', () => {
    it('android Messages.java contains legacy error codes', () => {
        const androidMessages = fs.readFileSync(androidMessagesPath, 'utf8');
        const expected: string[] = contract.legacy.errors.android;
        expected.forEach((code) => {
            expect(androidMessages).toContain(code);
        });
    });

    it('ios Messages.swift contains legacy error codes', () => {
        const iosMessages = fs.readFileSync(iosMessagesPath, 'utf8');
        const expected: string[] = contract.legacy.errors.ios;
        expected.forEach((code) => {
            expect(iosMessages).toContain(code);
        });
    });
});
