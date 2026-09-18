package app.independo.capacitorvoicerecorder.platform;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import app.independo.capacitorvoicerecorder.core.RecordingFailure;
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
import org.mockito.ArgumentCaptor;

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

    /**
     * A handler that runs what it is handed straight away -- which is the main
     * looper winning the race against the background thread that called
     * `startRecording`, the worst case for anything that posts before it is
     * ready to be observed.
     */
    private static final class ImmediateHandlerProvider implements CustomMediaRecorder.HandlerProvider {
        final List<Runnable> reposted = new ArrayList<>();

        @Override
        public void setupHandler() {}

        @Override
        public void post(Runnable r) {
            r.run();
        }

        @Override
        public void postDelayed(Runnable r, long d) {
            reposted.add(r);
        }

        @Override
        public void removeCallbacks(Runnable r) {}
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

    /**
     * A session that dies while running is the whole reason this callback
     * exists: nothing else notices, because nobody is calling anything that
     * could fail. Here the tick that reads the input level is the only thing
     * still touching the recorder, so it is the only thing that can tell.
     */
    @Test
    public void aMeteringTickThatThrowsReportsTheSessionAsFailed() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-fail-metering");
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
        List<RecordingFailure> failures = new ArrayList<>();
        recorder.setOnRecordingFailed(failures::add);

        recorder.startRecording();
        when(mediaRecorder.getMaxAmplitude()).thenThrow(new RuntimeException("getMaxAmplitude failed."));
        handler.posted.get(0).run();

        assertEquals(1, failures.size());
        assertEquals(RecordingFailure.AMPLITUDE_READ_FAILED, failures.get(0).code());
        assertEquals(CurrentRecordingStatus.ERROR, recorder.getCurrentStatus());
        assertEquals("a failed session is not polled again", 0, handler.reposted.size());
    }

    /** MediaRecorder's own error callback, which nothing used to be listening to. */
    @Test
    public void theMediaRecorderErrorCallbackReportsAFailedSession() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-fail-server");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, false),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        List<RecordingFailure> failures = new ArrayList<>();
        recorder.setOnRecordingFailed(failures::add);
        recorder.startRecording();

        ArgumentCaptor<MediaRecorder.OnErrorListener> listener = ArgumentCaptor.forClass(MediaRecorder.OnErrorListener.class);
        verify(mediaRecorder).setOnErrorListener(listener.capture());
        listener.getValue().onError(mediaRecorder, MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0);

        assertEquals(1, failures.size());
        assertEquals(RecordingFailure.SERVER_DIED, failures.get(0).code());
        assertEquals(CurrentRecordingStatus.ERROR, recorder.getCurrentStatus());
    }

    /**
     * Once. After the first failure the recorder is gone, so everything that
     * touches it afterwards fails too -- and the app only needs telling that the
     * session is over one time.
     */
    @Test
    public void aFailedSessionIsReportedOnlyOnce() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-fail-once");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, false),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        List<RecordingFailure> failures = new ArrayList<>();
        recorder.setOnRecordingFailed(failures::add);
        recorder.startRecording();

        ArgumentCaptor<MediaRecorder.OnErrorListener> listener = ArgumentCaptor.forClass(MediaRecorder.OnErrorListener.class);
        verify(mediaRecorder).setOnErrorListener(listener.capture());
        listener.getValue().onError(mediaRecorder, MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0);
        listener.getValue().onError(mediaRecorder, MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0);
        recorder.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        assertEquals(1, failures.size());
    }

    /**
     * The point of not tearing the recorder down in `fail`: whatever was
     * captured before it died is on disk, and stopping is what collects it.
     */
    @Test
    public void aFailedSessionCanStillBeStoppedToCollectItsFile() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-fail-stop");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, false),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        recorder.startRecording();
        File outputFile = recorder.getOutputFile();

        ArgumentCaptor<MediaRecorder.OnErrorListener> listener = ArgumentCaptor.forClass(MediaRecorder.OnErrorListener.class);
        verify(mediaRecorder).setOnErrorListener(listener.capture());
        listener.getValue().onError(mediaRecorder, MediaRecorder.MEDIA_ERROR_SERVER_DIED, 0);

        // `stop()` is not attempted on a recorder that is already gone -- it
        // would only throw -- but the session is released and closed out.
        recorder.stopRecording();

        verify(mediaRecorder, never()).stop();
        verify(mediaRecorder).release();
        assertEquals(CurrentRecordingStatus.NONE, recorder.getCurrentStatus());
        assertEquals("the file is still there to be collected", outputFile, recorder.getOutputFile());
    }

    /** A pause that throws on an interruption means the recorder was already gone. */
    @Test
    public void anInterruptionThatCannotPauseReportsAFailedSession() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-fail-interruption");
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, false),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest
        );
        List<RecordingFailure> failures = new ArrayList<>();
        recorder.setOnRecordingFailed(failures::add);
        recorder.startRecording();

        doThrow(new IllegalStateException("pause failed")).when(mediaRecorder).pause();
        recorder.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);

        assertEquals(1, failures.size());
        assertEquals(RecordingFailure.INTERRUPTION_FAILED, failures.get(0).code());
        assertEquals(CurrentRecordingStatus.ERROR, recorder.getCurrentStatus());
    }

    /**
     * Metering has to survive its own first tick running before `startRecording`
     * has finished.
     *
     * `startVolumeMetering` posts to the main looper while `startRecording` runs
     * on Capacitor's background thread, so the first tick really can execute
     * mid-method. It used to land while the status still said NONE, and because
     * a tick that fails its guard does not reschedule, the loop died right there
     * -- leaving a recording that worked perfectly next to a volume meter that
     * never moved. Intermittent, silent, and impossible to guess at from the
     * dashboard.
     */
    @Test
    public void volumeMeteringSurvivesItsFirstTickRunningBeforeStartReturns() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-metering-start-race");
        ImmediateHandlerProvider handler = new ImmediateHandlerProvider();
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, true),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest,
            handler
        );
        when(mediaRecorder.getMaxAmplitude()).thenReturn(8000);

        recorder.startRecording();

        assertEquals("the first tick kept the loop alive", 1, handler.reposted.size());
    }

    /** The same race on the way back from an interruption. */
    @Test
    public void volumeMeteringSurvivesItsFirstTickAfterResuming() throws Exception {
        MediaRecorder mediaRecorder = mock(MediaRecorder.class);
        AudioManager audioManager = mock(AudioManager.class);
        AudioFocusRequest focusRequest = mock(AudioFocusRequest.class);
        File cacheDir = tempFolder.newFolder("cache-metering-resume-race");
        ImmediateHandlerProvider handler = new ImmediateHandlerProvider();
        CustomMediaRecorder recorder = createRecorder(
            new RecordOptions(null, null, true),
            mediaRecorder,
            audioManager,
            cacheDir,
            android.os.Build.VERSION_CODES.N,
            focusRequest,
            handler
        );
        when(mediaRecorder.getMaxAmplitude()).thenReturn(8000);

        recorder.startRecording();
        recorder.pauseRecording();
        handler.reposted.clear();
        recorder.resumeRecording();

        assertEquals("the first tick after resuming kept the loop alive", 1, handler.reposted.size());
    }
}
