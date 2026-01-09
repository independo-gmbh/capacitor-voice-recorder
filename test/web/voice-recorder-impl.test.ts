import { VoiceRecorderImpl } from '../../src/platform/web/VoiceRecorderImpl';

describe('VoiceRecorderImpl.getSupportedMimeType', () => {
  const original = (global as any).MediaRecorder;

  afterEach(() => {
    (global as any).MediaRecorder = original;
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
