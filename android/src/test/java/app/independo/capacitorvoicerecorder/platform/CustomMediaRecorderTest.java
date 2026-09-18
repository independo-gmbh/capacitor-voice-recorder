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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CustomMediaRecorderTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** Captures what the volume metering loop posts, so a tick can be run by hand. */
    private static final class CapturingHandlerProvider implements CustomMediaRecorder.HandlerProvider {
        final List<Runnable> posted = new ArrayList<>();
        final List<Runnable> reposted = new ArrayList<>();
        final List<Runnable> removed = new ArrayList<>();

        @Override
        public void setupHandler() {}

        @Override
        public void post(Runnable r) {
            posted.add(r);
        }

        @Override
        public void postDelayed(Runnable r, long d) {
            reposted.add(r);
        }

        @Override
        public void removeCallbacks(Runnable r) {
            removed.add(r);
        }
    }

    private CustomMediaRecorder createRecorder(
        RecordOptions options,
        MediaRecorder mediaRecorder,
        AudioManager audioManager,
        File cacheDir,
        int sdkInt,
        AudioFocusRequest focusRequest
    ) throws Exception {
        return createRecorder(options, mediaRecorder, audioManager, cacheDir, sdkInt, focusRequest, null);
    }

    private CustomMediaRecorder createRecorder(
        RecordOptions options,
        MediaRecorder mediaRecorder,
        AudioManager audioManager,
        File cacheDir,
        int sdkInt,
        AudioFocusRequest focusRequest,
        CustomMediaRecorder.HandlerProvider handlerProvider
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
            handlerProvider != null ? handlerProvider : fakeHandlerProvider
        );
    }

    @Test
    public void startRecordingRequestsAudioFocusAndStartsRecorder() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions(null, null, false),
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
            new RecordOptions("CACHE", "/voice-tests/", false),
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

    /**
     * `LIBRARY_NO_CLOUD` used to fall through `getDirectory`'s `default ->
     * null`, and `new File(null, subDirectory)` then threw before a single
     * byte was recorded. It resolves like `DATA` here (Capacitor documents it
     * as the app files directory on Android); the iCloud distinction it draws
     * only exists on iOS.
     */
    @Test
    public void setRecorderOutputFileResolvesLibraryNoCloud() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File filesDir = tempFolder.newFolder("files-no-cloud");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions("LIBRARY_NO_CLOUD", "/voice-tests/", false),
            mediaRecorder,
            audioManager,
            filesDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );

        File outputFile = recorder.getOutputFile();
        File parentDir = outputFile.getParentFile();

        assertEquals("voice-tests", parentDir.getName());
        assertEquals(filesDir, parentDir.getParentFile());
        assertTrue(parentDir.exists());
    }

    /**
     * The metering loop runs on the main looper, but `stopRecording()` arrives on
     * Capacitor's background thread, so the recorder can be stopped and released
     * between the tick's guard and its `getMaxAmplitude()` call -- and a tick that
     * is already running cannot be cancelled by `removeCallbacks()`. That lost
     * race crashed the app with `RuntimeException: getMaxAmplitude failed.`
     */
    @Test
    public void volumeMeteringTickSurvivesRecorderDyingMidTick() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-metering-race");
        CapturingHandlerProvider handler = new CapturingHandlerProvider();
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, true),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest,
            handler
        );
        Consumer<Float> onVolumeChanged = mock(Consumer.class);
        recorder.setOnVolumeChanged(onVolumeChanged);

        recorder.startRecording();
        assertEquals(1, handler.posted.size());

        when(mediaRecorder.getMaxAmplitude()).thenThrow(new RuntimeException("getMaxAmplitude failed."));
        handler.posted.get(0).run();

        verify(onVolumeChanged, never()).accept(org.mockito.ArgumentMatchers.anyFloat());
        assertEquals("a dead recorder must not be polled again", 0, handler.reposted.size());
    }

    /** A tick still queued after `stopRecording()` must be a no-op, not a null deref. */
    @Test
    public void volumeMeteringTickAfterStopIsANoOp() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-metering-after-stop");
        CapturingHandlerProvider handler = new CapturingHandlerProvider();
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, true),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest,
            handler
        );

        recorder.startRecording();
        Runnable tick = handler.posted.get(0);
        recorder.stopRecording();

        tick.run();

        assertEquals(CurrentRecordingStatus.NONE, recorder.getCurrentStatus());
        assertEquals(0, handler.reposted.size());
        verify(mediaRecorder, never()).getMaxAmplitude();
    }

    /** The happy path keeps polling: a healthy tick reports a level and reschedules. */
    @Test
    public void volumeMeteringTickReportsLevelAndReschedules() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-metering-ok");
        CapturingHandlerProvider handler = new CapturingHandlerProvider();
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, true),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest,
            handler
        );
        List<Float> levels = new ArrayList<>();
        recorder.setOnVolumeChanged(levels::add);

        recorder.startRecording();
        when(mediaRecorder.getMaxAmplitude()).thenReturn(16000);
        handler.posted.get(0).run();

        assertEquals(1, levels.size());
        assertTrue(levels.get(0) > 0f);
        assertEquals(1, handler.reposted.size());
    }
}
