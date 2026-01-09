package app.independo.capacitorvoicerecorder.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import app.independo.capacitorvoicerecorder.adapters.PermissionChecker;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.adapters.RecorderPlatform;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.RecordData;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.platform.NotSupportedOsVersion;
import java.io.File;
import org.junit.Test;

public class VoiceRecorderServiceTest {

    @Test
    public void startRecordingFailsWhenMissingPermission() {
        FakePlatform platform = new FakePlatform();
        PermissionChecker permissionChecker = () -> false;
        VoiceRecorderService service = new VoiceRecorderService(platform, permissionChecker);

        assertServiceError(
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}),
            ErrorCodes.MISSING_PERMISSION
        );
    }

    @Test
    public void startRecordingFailsWhenAlreadyRecording() throws VoiceRecorderServiceException {
        FakePlatform platform = new FakePlatform();
        PermissionChecker permissionChecker = () -> true;
        VoiceRecorderService service = new VoiceRecorderService(platform, permissionChecker);

        service.startRecording(new RecordOptions(null, null), () -> {}, () -> {});

        assertServiceError(
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}),
            ErrorCodes.ALREADY_RECORDING
        );
    }

    @Test
    public void stopRecordingReturnsBase64WhenNoDirectory() throws VoiceRecorderServiceException {
        FakePlatform platform = new FakePlatform();
        PermissionChecker permissionChecker = () -> true;
        VoiceRecorderService service = new VoiceRecorderService(platform, permissionChecker);

        service.startRecording(new RecordOptions(null, null), () -> {}, () -> {});

        RecordData result = service.stopRecording();
        assertNotNull(result);
        assertEquals("base64-data", result.getRecordDataBase64());
        assertEquals(1234, result.getMsDuration());
        assertEquals("audio/aac", result.getMimeType());
        assertEquals(null, result.getUri());
        assertTrue(platform.recorder.deleteCalled);
        assertEquals(1, platform.readFileCalls);
        assertEquals(0, platform.toUriCalls);
    }

    @Test
    public void stopRecordingReturnsUriWhenDirectoryProvided() throws VoiceRecorderServiceException {
        FakePlatform platform = new FakePlatform();
        PermissionChecker permissionChecker = () -> true;
        VoiceRecorderService service = new VoiceRecorderService(platform, permissionChecker);

        service.startRecording(new RecordOptions("CACHE", null), () -> {}, () -> {});

        RecordData result = service.stopRecording();
        assertNotNull(result);
        assertEquals(null, result.getRecordDataBase64());
        assertEquals("file://recording.aac", result.getUri());
        assertEquals(1234, result.getMsDuration());
        assertEquals("audio/aac", result.getMimeType());
        assertFalse(platform.recorder.deleteCalled);
        assertEquals(0, platform.readFileCalls);
        assertEquals(1, platform.toUriCalls);
    }

    @Test
    public void stopRecordingFailsWhenNotStarted() {
        FakePlatform platform = new FakePlatform();
        PermissionChecker permissionChecker = () -> true;
        VoiceRecorderService service = new VoiceRecorderService(platform, permissionChecker);

        assertServiceError(service::stopRecording, ErrorCodes.RECORDING_HAS_NOT_STARTED);
    }

    private void assertServiceError(ThrowingRunnable runnable, String expectedCode) {
        try {
            runnable.run();
            fail("Expected VoiceRecorderServiceException");
        } catch (VoiceRecorderServiceException exception) {
            assertEquals(expectedCode, exception.getCode());
        } catch (Exception exception) {
            fail("Unexpected exception: " + exception.getClass().getName());
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeRecorder implements RecorderAdapter {
        private final RecordOptions options;
        private final File outputFile;
        private boolean deleteCalled;

        FakeRecorder(RecordOptions options, File outputFile) {
            this.options = options;
            this.outputFile = outputFile;
        }

        @Override
        public void setOnInterruptionBegan(Runnable callback) {}

        @Override
        public void setOnInterruptionEnded(Runnable callback) {}

        @Override
        public void startRecording() {}

        @Override
        public void stopRecording() {}

        @Override
        public boolean pauseRecording() throws NotSupportedOsVersion {
            return false;
        }

        @Override
        public boolean resumeRecording() throws NotSupportedOsVersion {
            return false;
        }

        @Override
        public CurrentRecordingStatus getCurrentStatus() {
            return CurrentRecordingStatus.RECORDING;
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

    private static final class FakePlatform implements RecorderPlatform {
        private FakeRecorder recorder;
        private int readFileCalls;
        private int toUriCalls;

        @Override
        public boolean canDeviceVoiceRecord() {
            return true;
        }

        @Override
        public boolean isMicrophoneOccupied() {
            return false;
        }

        @Override
        public RecorderAdapter createRecorder(RecordOptions options) {
            recorder = new FakeRecorder(options, new File("recording.aac"));
            return recorder;
        }

        @Override
        public String readFileAsBase64(File recordedFile) {
            readFileCalls++;
            return "base64-data";
        }

        @Override
        public int getDurationMs(File recordedFile) {
            return 1234;
        }

        @Override
        public String toUri(File recordedFile) {
            toUriCalls++;
            return "file://recording.aac";
        }
    }
}
