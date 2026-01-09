import { Directory } from '@capacitor/filesystem';

import { VoiceRecorderService } from '../../src/service/VoiceRecorderService';
import type { VoiceRecorderPlatform } from '../../src/service/VoiceRecorderService';

const createPlatform = (overrides: Partial<VoiceRecorderPlatform> = {}): VoiceRecorderPlatform => {
    return {
        canDeviceVoiceRecord: jest.fn().mockResolvedValue({ value: true }),
        hasAudioRecordingPermission: jest.fn().mockResolvedValue({ value: true }),
        requestAudioRecordingPermission: jest.fn().mockResolvedValue({ value: true }),
        startRecording: jest.fn().mockResolvedValue({ value: true }),
        stopRecording: jest
            .fn()
            .mockResolvedValue({
                value: {
                    recordDataBase64: '',
                    msDuration: 1234,
                    mimeType: 'audio/aac',
                    uri: 'file:///tmp/recording.aac',
                },
            }),
        pauseRecording: jest.fn().mockResolvedValue({ value: true }),
        resumeRecording: jest.fn().mockResolvedValue({ value: true }),
        getCurrentStatus: jest.fn().mockResolvedValue({ status: 'NONE' }),
        ...overrides,
    };
};

describe('VoiceRecorderService', () => {
    it('delegates to the platform for simple calls', async () => {
        const platform = createPlatform();
        const service = new VoiceRecorderService(platform, 'legacy');

        await service.canDeviceVoiceRecord();
        await service.hasAudioRecordingPermission();
        await service.requestAudioRecordingPermission();
        await service.startRecording({ directory: Directory.Cache, subDirectory: 'tests' });
        await service.pauseRecording();
        await service.resumeRecording();
        await service.getCurrentStatus();

        expect(platform.canDeviceVoiceRecord).toHaveBeenCalledTimes(1);
        expect(platform.hasAudioRecordingPermission).toHaveBeenCalledTimes(1);
        expect(platform.requestAudioRecordingPermission).toHaveBeenCalledTimes(1);
        expect(platform.startRecording).toHaveBeenCalledWith({ directory: Directory.Cache, subDirectory: 'tests' });
        expect(platform.pauseRecording).toHaveBeenCalledTimes(1);
        expect(platform.resumeRecording).toHaveBeenCalledTimes(1);
        expect(platform.getCurrentStatus).toHaveBeenCalledTimes(1);
    });

    it('normalizes record data when configured', async () => {
        const platform = createPlatform({
            stopRecording: jest.fn().mockResolvedValue({
                value: {
                    recordDataBase64: '',
                    msDuration: 42,
                    mimeType: 'audio/aac',
                    uri: 'file:///tmp/recording.aac',
                },
            }),
        });
        const service = new VoiceRecorderService(platform, 'normalized');

        const result = await service.stopRecording();

        expect(result.value).toEqual({
            msDuration: 42,
            mimeType: 'audio/aac',
            uri: 'file:///tmp/recording.aac',
        });
    });

    it('attaches canonical error codes in normalized mode', async () => {
        const error = new Error('CANNOT_RECORD_ON_THIS_PHONE');
        const platform = createPlatform({
            startRecording: jest.fn().mockRejectedValue(error),
        });
        const service = new VoiceRecorderService(platform, 'normalized');

        await expect(service.startRecording()).rejects.toThrow('CANNOT_RECORD_ON_THIS_PHONE');
        expect((error as { code?: string }).code).toBe('DEVICE_CANNOT_VOICE_RECORD');
    });

    it('leaves error codes untouched in legacy mode', async () => {
        const error = new Error('CANNOT_RECORD_ON_THIS_PHONE');
        const platform = createPlatform({
            startRecording: jest.fn().mockRejectedValue(error),
        });
        const service = new VoiceRecorderService(platform, 'legacy');

        await expect(service.startRecording()).rejects.toThrow('CANNOT_RECORD_ON_THIS_PHONE');
        expect((error as { code?: string }).code).toBeUndefined();
    });
});
