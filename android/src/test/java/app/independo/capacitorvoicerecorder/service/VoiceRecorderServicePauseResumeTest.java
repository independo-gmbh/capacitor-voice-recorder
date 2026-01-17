package app.independo.capacitorvoicerecorder.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import org.junit.Test;

public class VoiceRecorderServicePauseResumeTest {

    @Test
    public void pauseRecordingThrowsWhenNotStarted() {
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(
            VoiceRecorderServiceFixtures.createPlatform(),
            () -> true
        );

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::pauseRecording
        );

        assertEquals(ErrorCodes.RECORDING_HAS_NOT_STARTED, exception.getCode());
    }

    @Test
    public void pauseRecordingThrowsWhenNotSupported() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.recorder.pauseThrows = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::pauseRecording
        );

        assertEquals(ErrorCodes.NOT_SUPPORTED_OS_VERSION, exception.getCode());
    }

    @Test
    public void pauseRecordingReturnsTrueWhenSupported() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});

        assertTrue(service.pauseRecording());
        assertEquals(CurrentRecordingStatus.PAUSED, service.getCurrentStatus());
    }

    @Test
    public void resumeRecordingThrowsWhenNotStarted() {
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(
            VoiceRecorderServiceFixtures.createPlatform(),
            () -> true
        );

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::resumeRecording
        );

        assertEquals(ErrorCodes.RECORDING_HAS_NOT_STARTED, exception.getCode());
    }

    @Test
    public void resumeRecordingThrowsWhenNotSupported() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.recorder.resumeThrows = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            service::resumeRecording
        );

        assertEquals(ErrorCodes.NOT_SUPPORTED_OS_VERSION, exception.getCode());
    }

    @Test
    public void resumeRecordingReturnsTrueWhenSupported() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null, false), () -> {}, () -> {}, (Float volume) -> {});
        service.pauseRecording();

        assertTrue(service.resumeRecording());
        assertEquals(CurrentRecordingStatus.RECORDING, service.getCurrentStatus());
    }

    @Test
    public void getCurrentStatusReturnsNoneWhenIdle() {
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(
            VoiceRecorderServiceFixtures.createPlatform(),
            () -> true
        );

        assertEquals(CurrentRecordingStatus.NONE, service.getCurrentStatus());
    }
}
