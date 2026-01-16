package app.independo.capacitorvoicerecorder.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.ErrorCodes;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import org.junit.Test;

public class VoiceRecorderServiceStartTest {

    @Test
    public void startRecordingThrowsWhenDeviceCannotRecord() {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.canDeviceVoiceRecord = false;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.DEVICE_CANNOT_VOICE_RECORD, exception.getCode());
    }

    @Test
    public void startRecordingThrowsWhenPermissionMissing() {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> false);

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.MISSING_PERMISSION, exception.getCode());
    }

    @Test
    public void startRecordingThrowsWhenMicrophoneInUse() {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.microphoneOccupied = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.MICROPHONE_BEING_USED, exception.getCode());
    }

    @Test
    public void startRecordingThrowsWhenAlreadyRecording() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {});

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.ALREADY_RECORDING, exception.getCode());
    }

    @Test
    public void startRecordingThrowsWhenRecorderCreationFails() {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.createThrows = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.FAILED_TO_RECORD, exception.getCode());
    }

    @Test
    public void startRecordingThrowsWhenRecorderStartFails() {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        platform.recorder.startThrows = true;
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        VoiceRecorderServiceException exception = assertThrows(
            VoiceRecorderServiceException.class,
            () -> service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {})
        );

        assertEquals(ErrorCodes.FAILED_TO_RECORD, exception.getCode());
    }

    @Test
    public void startRecordingSetsInterruptionCallbacks() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {});

        assertNotNull(platform.recorder.onInterruptionBegan);
        assertNotNull(platform.recorder.onInterruptionEnded);
    }

    @Test
    public void startRecordingUpdatesStatus() throws Exception {
        VoiceRecorderServiceFixtures.FakePlatform platform = VoiceRecorderServiceFixtures.createPlatform();
        VoiceRecorderService service = VoiceRecorderServiceFixtures.createService(platform, () -> true);

        service.startRecording(new RecordOptions(null, null), () -> {}, () -> {}, (Float volume) -> {});

        assertEquals(CurrentRecordingStatus.RECORDING, service.getCurrentStatus());
    }
}
