package app.independo.capacitorvoicerecorder.service;

import app.independo.capacitorvoicerecorder.adapters.PermissionChecker;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.adapters.RecorderPlatform;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.platform.NotSupportedOsVersion;
import java.io.File;

final class VoiceRecorderServiceFixtures {

    private VoiceRecorderServiceFixtures() {}

    static VoiceRecorderService createService(RecorderPlatform platform, PermissionChecker permissionChecker) {
        return new VoiceRecorderService(platform, permissionChecker);
    }

    static FakePlatform createPlatform() {
        return new FakePlatform();
    }

    static class FakePlatform implements RecorderPlatform {
        boolean canDeviceVoiceRecord = true;
        boolean microphoneOccupied = false;
        boolean createThrows = false;
        boolean readFileCalled = false;
        boolean toUriCalled = false;
        boolean readThrows = false;
        String base64Payload = "BASE64";
        String uri = "file:///tmp/recording.aac";
        int durationMs = 1000;
        final FakeRecorder recorder = new FakeRecorder();

        @Override
        public boolean canDeviceVoiceRecord() {
            return canDeviceVoiceRecord;
        }

        @Override
        public boolean isMicrophoneOccupied() {
            return microphoneOccupied;
        }

        @Override
        public RecorderAdapter createRecorder(RecordOptions options) throws Exception {
            if (createThrows) {
                throw new Exception("createRecorder failed");
            }
            recorder.options = options;
            return recorder;
        }

        @Override
        public String readFileAsBase64(File recordedFile) {
            readFileCalled = true;
            if (readThrows) {
                throw new RuntimeException("readFileAsBase64 failed");
            }
            return base64Payload;
        }

        @Override
        public int getDurationMs(File recordedFile) {
            return durationMs;
        }

        @Override
        public String toUri(File recordedFile) {
            toUriCalled = true;
            return uri;
        }
    }

    static class FakeRecorder implements RecorderAdapter {
        File outputFile = new File("build/tmp/recording.aac");
        RecordOptions options;
        CurrentRecordingStatus status = CurrentRecordingStatus.NONE;
        boolean deleteCalled = false;
        boolean pauseThrows = false;
        boolean resumeThrows = false;
        boolean startThrows = false;
        Runnable onInterruptionBegan;
        Runnable onInterruptionEnded;

        @Override
        public void setOnInterruptionBegan(Runnable callback) {
            onInterruptionBegan = callback;
        }

        @Override
        public void setOnInterruptionEnded(Runnable callback) {
            onInterruptionEnded = callback;
        }

        @Override
        public void startRecording() {
            if (startThrows) {
                throw new RuntimeException("startRecording failed");
            }
            status = CurrentRecordingStatus.RECORDING;
        }

        @Override
        public void stopRecording() {
            status = CurrentRecordingStatus.NONE;
        }

        @Override
        public boolean pauseRecording() throws NotSupportedOsVersion {
            if (pauseThrows) {
                throw new NotSupportedOsVersion();
            }
            status = CurrentRecordingStatus.PAUSED;
            return true;
        }

        @Override
        public boolean resumeRecording() throws NotSupportedOsVersion {
            if (resumeThrows) {
                throw new NotSupportedOsVersion();
            }
            status = CurrentRecordingStatus.RECORDING;
            return true;
        }

        @Override
        public CurrentRecordingStatus getCurrentStatus() {
            return status;
        }

        @Override
        public File getOutputFile() {
            return outputFile;
        }

        @Override
        public RecordOptions getRecordOptions() {
            return options;
        }

        @Override
        public boolean deleteOutputFile() {
            deleteCalled = true;
            return true;
        }
    }
}
