import { VoiceRecorderImpl } from '../../src/platform/web/VoiceRecorderImpl';

jest.mock('../../src/platform/web/get-blob-duration', () => ({
    __esModule: true,
    default: jest.fn(async () => 1.5),
}));

const createMockStream = () => ({
    getTracks: () => [
        { stop: jest.fn() },
        { stop: jest.fn() },
    ],
});

class MockMediaRecorder {
    public static isTypeSupported = jest.fn((type: string) => type === 'audio/aac');
    public state: 'inactive' | 'recording' | 'paused' = 'inactive';
    public stream: { getTracks: () => { stop: () => void }[] };
    public onerror?: () => void;
    public onstop?: () => void | Promise<void>;
    public ondataavailable?: (event: { data: Blob }) => void;

    public constructor(stream: { getTracks: () => { stop: () => void }[] }) {
        this.stream = stream;
    }

    public start(): void {
        this.state = 'recording';
    }

    public stop(): void {
        this.state = 'inactive';
        if (this.ondataavailable) {
            this.ondataavailable({ data: new Blob(['data'], { type: 'audio/aac' }) });
        }
        if (this.onstop) {
            void this.onstop();
        }
    }

    public pause(): void {
        if (this.state === 'recording') {
            this.state = 'paused';
        }
    }

    public resume(): void {
        if (this.state === 'paused') {
            this.state = 'recording';
        }
    }
}

class MockFileReader {
    public result: string | ArrayBuffer | null = null;
    public onloadend: (() => void) | null = null;

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    public readAsDataURL(_blob: Blob): void {
        this.result = 'data:audio/aac;base64,BASE64_DATA';
        if (this.onloadend) {
            this.onloadend();
        }
    }
}

const setNavigatorMediaDevices = (value: any) => {
    Object.defineProperty(navigator, 'mediaDevices', {
        value,
        configurable: true,
        writable: true,
    });
};

const setNavigatorPermissions = (value: any) => {
    Object.defineProperty(navigator, 'permissions', {
        value,
        configurable: true,
        writable: true,
    });
};

describe('VoiceRecorderImpl.getSupportedMimeType', () => {
    const originalMediaRecorder = (global as any).MediaRecorder;

    afterEach(() => {
        (global as any).MediaRecorder = originalMediaRecorder;
    });

    it('returns null when MediaRecorder is undefined', () => {
        (global as any).MediaRecorder = undefined;
        expect(VoiceRecorderImpl.getSupportedMimeType()).toBeNull();
    });

    it('returns first supported mime type', () => {
        (global as any).MediaRecorder = {
            isTypeSupported: jest.fn().mockReturnValue(true),
        } as any;
        expect(VoiceRecorderImpl.getSupportedMimeType()).toBe('audio/aac');
    });
});

describe('VoiceRecorderImpl permissions', () => {
    const originalMediaDevices = (navigator as any).mediaDevices;
    const originalPermissions = (navigator as any).permissions;

    afterEach(() => {
        setNavigatorMediaDevices(originalMediaDevices);
        setNavigatorPermissions(originalPermissions);
    });

    it('uses navigator.permissions when available', async () => {
        const query = jest.fn().mockResolvedValue({ state: 'granted' });
        setNavigatorPermissions({ query });

        const result = await VoiceRecorderImpl.hasAudioRecordingPermission();

        expect(query).toHaveBeenCalledWith({ name: 'microphone' });
        expect(result).toEqual({ value: true });
    });

    it('falls back to getUserMedia when permissions are unavailable', async () => {
        const getUserMedia = jest.fn().mockResolvedValue({});
        setNavigatorPermissions({});
        setNavigatorMediaDevices({ getUserMedia });

        const result = await VoiceRecorderImpl.hasAudioRecordingPermission();

        expect(getUserMedia).toHaveBeenCalled();
        expect(result).toEqual({ value: true });
    });

    it('throws a permission error when getUserMedia fails in fallback', async () => {
        const getUserMedia = jest.fn().mockRejectedValue(new Error('nope'));
        setNavigatorPermissions({});
        setNavigatorMediaDevices({ getUserMedia });

        await expect(VoiceRecorderImpl.hasAudioRecordingPermission()).rejects.toThrow(
            'COULD_NOT_QUERY_PERMISSION_STATUS',
        );
    });

    it('avoids prompting when permission is already granted', async () => {
        const hasPermissionSpy = jest
            .spyOn(VoiceRecorderImpl, 'hasAudioRecordingPermission')
            .mockResolvedValue({ value: true });
        const getUserMedia = jest.fn().mockResolvedValue({});
        setNavigatorMediaDevices({ getUserMedia });

        const result = await VoiceRecorderImpl.requestAudioRecordingPermission();

        expect(result).toEqual({ value: true });
        expect(getUserMedia).not.toHaveBeenCalled();
        hasPermissionSpy.mockRestore();
    });

    it('prompts when permission is not granted', async () => {
        const hasPermissionSpy = jest
            .spyOn(VoiceRecorderImpl, 'hasAudioRecordingPermission')
            .mockResolvedValue({ value: false });
        const getUserMedia = jest.fn().mockResolvedValue({});
        setNavigatorMediaDevices({ getUserMedia });

        const result = await VoiceRecorderImpl.requestAudioRecordingPermission();

        expect(getUserMedia).toHaveBeenCalled();
        expect(result).toEqual({ value: true });
        hasPermissionSpy.mockRestore();
    });
});

describe('VoiceRecorderImpl recording flow', () => {
    const originalMediaRecorder = (global as any).MediaRecorder;
    const originalFileReader = (global as any).FileReader;
    const originalMediaDevices = (navigator as any).mediaDevices;
    const originalPermissions = (navigator as any).permissions;

    beforeEach(() => {
        (global as any).MediaRecorder = MockMediaRecorder as any;
        (global as any).FileReader = MockFileReader;
        setNavigatorPermissions({ query: jest.fn().mockResolvedValue({ state: 'granted' }) });
        setNavigatorMediaDevices({ getUserMedia: jest.fn().mockResolvedValue(createMockStream()) });
    });

    afterEach(() => {
        (global as any).MediaRecorder = originalMediaRecorder;
        (global as any).FileReader = originalFileReader;
        setNavigatorMediaDevices(originalMediaDevices);
        setNavigatorPermissions(originalPermissions);
        jest.restoreAllMocks();
    });

    it('records, pauses, resumes, and stops with base64 payload', async () => {
        const recorder = new VoiceRecorderImpl();

        const startResponse = await recorder.startRecording();
        expect(startResponse).toEqual({ value: true });
        expect(await recorder.getCurrentStatus()).toEqual({ status: 'RECORDING' });

        await expect(recorder.pauseRecording()).resolves.toEqual({ value: true });
        expect(await recorder.getCurrentStatus()).toEqual({ status: 'PAUSED' });

        await expect(recorder.resumeRecording()).resolves.toEqual({ value: true });
        expect(await recorder.getCurrentStatus()).toEqual({ status: 'RECORDING' });

        const data = await recorder.stopRecording();
        expect(data.value.recordDataBase64).toBe('BASE64_DATA');
        expect(data.value.mimeType).toBe('audio/aac');
        expect(data.value.msDuration).toBe(1500);
        expect(data.value.uri).toBeUndefined();

        expect(await recorder.getCurrentStatus()).toEqual({ status: 'NONE' });
    });

    it('throws when starting while already recording', async () => {
        const recorder = new VoiceRecorderImpl();
        await recorder.startRecording();
        await expect(recorder.startRecording()).rejects.toThrow('ALREADY_RECORDING');
    });

    it('throws when stopping without an active recording', async () => {
        const recorder = new VoiceRecorderImpl();
        await expect(recorder.stopRecording()).rejects.toThrow('RECORDING_HAS_NOT_STARTED');
    });

    it('throws when device cannot record', async () => {
        const canRecordSpy = jest
            .spyOn(VoiceRecorderImpl, 'canDeviceVoiceRecord')
            .mockResolvedValue({ value: false });
        const recorder = new VoiceRecorderImpl();

        await expect(recorder.startRecording()).rejects.toThrow('DEVICE_CANNOT_VOICE_RECORD');

        canRecordSpy.mockRestore();
    });

    it('throws when microphone permission is missing', async () => {
        const canRecordSpy = jest
            .spyOn(VoiceRecorderImpl, 'canDeviceVoiceRecord')
            .mockResolvedValue({ value: true });
        const hasPermissionSpy = jest
            .spyOn(VoiceRecorderImpl, 'hasAudioRecordingPermission')
            .mockResolvedValue({ value: false });
        const recorder = new VoiceRecorderImpl();

        await expect(recorder.startRecording()).rejects.toThrow('MISSING_PERMISSION');

        canRecordSpy.mockRestore();
        hasPermissionSpy.mockRestore();
    });

    it('throws when getUserMedia fails', async () => {
        setNavigatorMediaDevices({ getUserMedia: jest.fn().mockRejectedValue(new Error('nope')) });
        const recorder = new VoiceRecorderImpl();

        await expect(recorder.startRecording()).rejects.toThrow('FAILED_TO_RECORD');
    });
});
