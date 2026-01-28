package app.independo.capacitorvoicerecorder.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.RecordData;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import org.junit.Test;

public class VoiceRecorderServiceStopTest {

    @Test
    public void stopRecordingThrowsWhenNotStarted() {
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(
            VoiceRecorderServiceFixtures.createPlatform(),
            () -> true
        );

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::stopRecording
        );

        assertEquals(ErrorCodes.RECORDING_HAS_NOT_STARTED, exception.getCode());
    }

    @Test
    public void stopRecordingThrowsWhenOutputFileMissing() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.recorder.outputFile = null;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::stopRecording
        );

        assertEquals(ErrorCodes.FAILED_TO_FETCH_RECORDING, exception.getCode());
    }

    @Test
    public void stopRecordingReturnsBase64AndDeletesFileForInlineRecording() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.base64Payload = "BASE64";
        platform.durationMs = 1500;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        RecordData data = service.stopRecording();

        assertEquals("BASE64", data.getRecordDataBase64());
        assertEquals("audio/aac", data.getMimeType());
        assertEquals(1500, data.getMsDuration());
        assertNull(data.getUri());
        assertTrue(platform.readFileCalled);
        assertFalse(platform.toUriCalled);
        assertTrue(platform.recorder.deleteCalled);
    }

    @Test
    public void stopRecordingReturnsUriWhenDirectoryProvided() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.uri = "file:///tmp/recording.aac";
        platform.durationMs = 2000;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions("CACHE", null, false), () -> {}, () -> {}, (Float volume) -> {});
        RecordData data = service.stopRecording();

        assertNull(data.getRecordDataBase64());
        assertEquals("file:///tmp/recording.aac", data.getUri());
        assertFalse(platform.readFileCalled);
        assertTrue(platform.toUriCalled);
        assertFalse(platform.recorder.deleteCalled);
    }

    @Test
    public void stopRecordingThrowsWhenPayloadIsEmpty() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.base64Payload = null;
        platform.durationMs = 1200;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::stopRecording
        );

        assertEquals(ErrorCodes.EMPTY_RECORDING, exception.getCode());
    }

    @Test
    public void stopRecordingThrowsWhenDurationIsInvalid() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.base64Payload = "BASE64";
        platform.durationMs = -1;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::stopRecording
        );

        assertEquals(ErrorCodes.EMPTY_RECORDING, exception.getCode());
    }

    @Test
    public void stopRecordingWrapsUnexpectedExceptions() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.readThrows = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::stopRecording
        );

        assertEquals(ErrorCodes.FAILED_TO_FETCH_RECORDING, exception.getCode());
    }
}
