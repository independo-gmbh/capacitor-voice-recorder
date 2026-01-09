import fs from 'node:fs';
import path from 'node:path';

import {
    alreadyRecordingError,
    couldNotQueryPermissionStatusError,
    deviceCannotVoiceRecordError,
    emptyRecordingError,
    failedToFetchRecordingError,
    failedToRecordError,
    microphoneBeingUsedError,
    missingPermissionError,
    recordingHasNotStartedError,
} from '../../src/platform/web/predefined-web-responses';

const contractPath = path.join(__dirname, 'voice-recorder-contract.json');
const contract = JSON.parse(fs.readFileSync(contractPath, 'utf8'));

describe('web legacy error messages', () => {
    it('matches the legacy web error codes', () => {
        const actual = [
            missingPermissionError(),
            alreadyRecordingError(),
            microphoneBeingUsedError(),
            deviceCannotVoiceRecordError(),
            failedToRecordError(),
            emptyRecordingError(),
            recordingHasNotStartedError(),
            failedToFetchRecordingError(),
            couldNotQueryPermissionStatusError(),
        ].map((error) => error.message);

        const expected: string[] = contract.legacy.errors.web;
        expect(new Set(actual)).toEqual(new Set(expected));
    });
});
