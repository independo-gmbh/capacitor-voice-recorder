package app.independo.capacitorvoicerecorder.platform;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class CustomMediaRecorderTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private CustomMediaRecorder createRecorder(
        RecordOptions options,
        MediaRecorder mediaRecorder,
        AudioManager audioManager,
        File cacheDir,
        int sdkInt,
        AudioFocusRequest focusRequest
    ) throws Exception {
        Context context = mock(Context.class);
        CustomMediaRecorder.MediaRecorderFactory mediaRecorderFactory = () -> mediaRecorder;
        CustomMediaRecorder.AudioManagerProvider audioManagerProvider = ignored -> audioManager;
        CustomMediaRecorder.DirectoryProvider directoryProvider = new CustomMediaRecorder.DirectoryProvider() {
            @Override
            public File getDocumentsDirectory() {
                return cacheDir;
            }

            @Override
            public File getFilesDir(Context context) {
                return cacheDir;
            }

            @Override
            public File getCacheDir(Context context) {
                return cacheDir;
            }

            @Override
            public File getExternalFilesDir(Context context) {
                return cacheDir;
            }

            @Override
            public File getExternalStorageDirectory() {
                return cacheDir;
            }
        };
        CustomMediaRecorder.SdkIntProvider sdkIntProvider = () -> sdkInt;
        CustomMediaRecorder.AudioFocusRequestFactory audioFocusRequestFactory = ignored -> focusRequest;
        CustomMediaRecorder.HandlerProvider fakeHandlerProvider = new CustomMediaRecorder.HandlerProvider() {
            @Override public void setupHandler() {}
            @Override public void post(Runnable r) {}
            @Override public void postDelayed(Runnable r, long d) {}
            @Override public void removeCallbacks(Runnable r) {}
        };
        return new CustomMediaRecorder(
            context,
            options,
            mediaRecorderFactory,
            audioManagerProvider,
            directoryProvider,
            sdkIntProvider,
            audioFocusRequestFactory,
            fakeHandlerProvider
        );
    }

    @Test
    public void startRecordingRequestsAudioFocusAndStartsRecorder() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        recorder.startRecording();

        verify(audioManager).requestAudioFocus(recorder, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        verify(mediaRecorder).start();
        assertEquals(CurrentRecordingStatus.RECORDING, recorder.getCurrentStatus());
    }

    @Test
    public void startRecordingUsesAudioFocusRequestOnOAndAbove() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-o");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.O,
            focusRequest
        );

        recorder.startRecording();

        verify(audioManager).requestAudioFocus(focusRequest);
        verify(mediaRecorder).start();
        assertEquals(CurrentRecordingStatus.RECORDING, recorder.getCurrentStatus());
    }

    @Test
    public void stopRecordingReleasesRecorderAndAbandonsFocus() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-stop");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        recorder.startRecording();
        recorder.stopRecording();

        verify(mediaRecorder).stop();
        verify(mediaRecorder).release();
        verify(audioManager).abandonAudioFocus(recorder);
        assertEquals(CurrentRecordingStatus.NONE, recorder.getCurrentStatus());
    }

    @Test
    public void stopRecordingAbandonsFocusRequestOnOAndAbove() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-stop-o");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.O,
            focusRequest
        );

        recorder.startRecording();
        recorder.stopRecording();

        verify(audioManager).abandonAudioFocusRequest(focusRequest);
        assertEquals(CurrentRecordingStatus.NONE, recorder.getCurrentStatus());
    }

    @Test
    public void stopRecordingReleasesRecorderWhenStopThrows() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-stop-fail");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        doThrow(new IllegalStateException("stop failed")).when(mediaRecorder).stop();

        recorder.startRecording();
        recorder.stopRecording();

        verify(mediaRecorder).release();
        assertEquals(CurrentRecordingStatus.NONE, recorder.getCurrentStatus());
    }

    @Test
    public void pauseRecordingThrowsWhenUnsupported() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-legacy");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.M,
            focusRequest
        );

        recorder.startRecording();

        try {
            recorder.pauseRecording();
            fail("Expected NotSupportedOsVersion");
        } catch (NotSupportedOsVersion ignored) {
        }
    }

    @Test
    public void pauseRecordingUpdatesStatusAndCallsPause() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-pause");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        recorder.startRecording();
        boolean paused = recorder.pauseRecording();

        assertTrue(paused);
        verify(mediaRecorder).pause();
        assertEquals(CurrentRecordingStatus.PAUSED, recorder.getCurrentStatus());
    }

    @Test
    public void resumeRecordingRequestsAudioFocusAndCallsResume() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-resume");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        recorder.startRecording();
        recorder.pauseRecording();
        boolean resumed = recorder.resumeRecording();

        assertTrue(resumed);
        verify(audioManager, times(2))
            .requestAudioFocus(recorder, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        verify(mediaRecorder).resume();
        assertEquals(CurrentRecordingStatus.RECORDING, recorder.getCurrentStatus());
    }

    @Test
    public void onAudioFocusChangeLossPausesAndNotifies() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-focus-loss");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        Runnable interruption = mock(Runnable.class);
        recorder.setOnInterruptionBegan(interruption);

        recorder.startRecording();
        recorder.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        verify(mediaRecorder).pause();
        verify(interruption).run();
        assertEquals(CurrentRecordingStatus.INTERRUPTED, recorder.getCurrentStatus());
    }

    @Test
    public void onAudioFocusChangeGainNotifiesWhenInterrupted() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-focus-gain");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        Runnable interruptionEnded = mock(Runnable.class);
        recorder.setOnInterruptionEnded(interruptionEnded);

        recorder.startRecording();
        recorder.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        recorder.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN);

        verify(interruptionEnded).run();
        assertEquals(CurrentRecordingStatus.INTERRUPTED, recorder.getCurrentStatus());
    }

    @Test
    public void setRecorderOutputFileUsesSubDirectory() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-output");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions("CACHE", "/voice-tests/"),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        File outputFile = recorder.getOutputFile();
        File parentDir = outputFile.getParentFile();

        assertEquals("voice-tests", parentDir.getName());
        assertTrue(parentDir.exists());
        verify(mediaRecorder).setOutputFile(outputFile.getAbsolutePath());
    }
}
