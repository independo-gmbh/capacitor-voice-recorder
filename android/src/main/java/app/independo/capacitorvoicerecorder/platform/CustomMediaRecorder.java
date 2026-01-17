package app.independo.capacitorvoicerecorder.platform;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.core.CurrentRecordingStatus;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.os.Handler;
import android.os.Looper;
import java.util.function.Consumer;

/** MediaRecorder wrapper that manages audio focus and interruptions. */
public class CustomMediaRecorder implements AudioManager.OnAudioFocusChangeListener, RecorderAdapter {

    interface HandlerProvider {
        void setupHandler();
        void post(Runnable runnable);
        void postDelayed(Runnable runnable, long delayMillis);
        void removeCallbacks(Runnable runnable);
    }

    private static final class DefaultHandlerProvider implements HandlerProvider {
        private Handler handler;

        @Override
        public void setupHandler() {
            // This is only called when startVolumeMetering() runs
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
        }

        @Override
        public void post(Runnable r) {
            if (handler != null) {
                handler.post(r);
            }
        }

        @Override
        public void postDelayed(Runnable r, long d) {
            if (handler != null) {
                handler.postDelayed(r, d);
            }
        }

        @Override
        public void removeCallbacks(Runnable r) {
            if (handler != null) {
                handler.removeCallbacks(r);
            }
        }
    }

    interface MediaRecorderFactory {
        MediaRecorder create();
    }

    interface AudioManagerProvider {
        AudioManager getAudioManager(Context context);
    }

    interface DirectoryProvider {
        File getDocumentsDirectory();

        File getFilesDir(Context context);

        File getCacheDir(Context context);

        File getExternalFilesDir(Context context);

        File getExternalStorageDirectory();
    }

    interface SdkIntProvider {
        int getSdkInt();
    }

    interface AudioFocusRequestFactory {
        AudioFocusRequest create(AudioManager.OnAudioFocusChangeListener listener);
    }

    private static final class DefaultMediaRecorderFactory implements MediaRecorderFactory {
        @Override
        public MediaRecorder create() {
            return new MediaRecorder();
        }
    }

    private static final class DefaultAudioManagerProvider implements AudioManagerProvider {
        @Override
        public AudioManager getAudioManager(Context context) {
            return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        }
    }

    private static final class DefaultDirectoryProvider implements DirectoryProvider {
        @Override
        public File getDocumentsDirectory() {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        }

        @Override
        public File getFilesDir(Context context) {
            return context.getFilesDir();
        }

        @Override
        public File getCacheDir(Context context) {
            return context.getCacheDir();
        }

        @Override
        public File getExternalFilesDir(Context context) {
            return context.getExternalFilesDir(null);
        }

        @Override
        public File getExternalStorageDirectory() {
            return Environment.getExternalStorageDirectory();
        }
    }

    private static final class DefaultSdkIntProvider implements SdkIntProvider {
        @Override
        public int getSdkInt() {
            return Build.VERSION.SDK_INT;
        }
    }

    private static final class DefaultAudioFocusRequestFactory implements AudioFocusRequestFactory {
        @Override
        public AudioFocusRequest create(AudioManager.OnAudioFocusChangeListener listener) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

            return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener(listener)
                .build();
        }
    }

    /** Android context for file paths and system services. */
    private final Context context;
    /** Recording options passed from the service layer. */
    private final RecordOptions options;
    /** Factory for MediaRecorder instances. */
    private final MediaRecorderFactory mediaRecorderFactory;
    /** Provider for Handler. */
    private final HandlerProvider handlerProvider;
    /** Directory provider for file output. */
    private final DirectoryProvider directoryProvider;
    /** SDK version provider for API gating. */
    private final SdkIntProvider sdkIntProvider;
    /** Audio focus request factory for O and above. */
    private final AudioFocusRequestFactory audioFocusRequestFactory;
    /** Active MediaRecorder instance for the session. */
    private MediaRecorder mediaRecorder;
    /** Output file for the current recording session. */
    private File outputFile;
    /** Current session status tracked locally. */
    private CurrentRecordingStatus currentRecordingStatus = CurrentRecordingStatus.NONE;
    /** Audio manager for focus changes. */
    private AudioManager audioManager;
    /** Focus request for Android O and above. */
    private AudioFocusRequest audioFocusRequest;
    /** Callback invoked when an interruption begins. */
    private Runnable onInterruptionBegan;
    /** Callback invoked when an interruption ends. */
    private Runnable onInterruptionEnded;
    /** Callback invoked with volume changes. */
    private Consumer<Float> onVolumeChanged;

    private Runnable volumeRunnable;
    private float lowPassVolume = 0.0f;
    private static final int POLL_INTERVAL_MS = 50;

    public CustomMediaRecorder(Context context, RecordOptions options) throws IOException {
        this(
            context,
            options,
            new DefaultMediaRecorderFactory(),
            new DefaultAudioManagerProvider(),
            new DefaultDirectoryProvider(),
            new DefaultSdkIntProvider(),
            new DefaultAudioFocusRequestFactory(),
            new DefaultHandlerProvider()
        );
    }

    CustomMediaRecorder(
        Context context,
        RecordOptions options,
        MediaRecorderFactory mediaRecorderFactory,
        AudioManagerProvider audioManagerProvider,
        DirectoryProvider directoryProvider,
        SdkIntProvider sdkIntProvider,
        AudioFocusRequestFactory audioFocusRequestFactory,
        HandlerProvider handlerProvider
    ) throws IOException {
        this.context = context;
        this.options = options;
        this.mediaRecorderFactory = mediaRecorderFactory;
        this.directoryProvider = directoryProvider;
        this.sdkIntProvider = sdkIntProvider;
        this.audioFocusRequestFactory = audioFocusRequestFactory;
        this.handlerProvider = handlerProvider;
        this.audioManager = audioManagerProvider.getAudioManager(context);
        generateMediaRecorder();
    }

    /** Sets the callback for interruption begin events. */
    public void setOnInterruptionBegan(Runnable callback) {
        this.onInterruptionBegan = callback;
    }

    /** Sets the callback for interruption end events. */
    public void setOnInterruptionEnded(Runnable callback) {
        this.onInterruptionEnded = callback;
    }

    /** Sets the callback for real-time volume updates. */
    public void setOnVolumeChanged(Consumer<Float> callback) {
        this.onVolumeChanged = callback;
    }

    /** Configures the MediaRecorder with audio settings. */
    private void generateMediaRecorder() throws IOException {
        mediaRecorder = mediaRecorderFactory.create();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(96000);
        mediaRecorder.setAudioSamplingRate(44100);
        setRecorderOutputFile();
        mediaRecorder.prepare();
    }

    /** Picks a directory and allocates the output file for this session. */
    private void setRecorderOutputFile() throws IOException {
        File outputDir = directoryProvider.getCacheDir(context);

        String directory = options.directory();
        String subDirectory = options.subDirectory();

        if (directory != null) {
            outputDir = this.getDirectory(directory);
            if (subDirectory != null) {
                Pattern pattern = Pattern.compile("^/?(.+[^/])/?$");
                Matcher matcher = pattern.matcher(subDirectory);
                if (matcher.matches()) {
                    outputDir = new File(outputDir, matcher.group(1));
                    if (!outputDir.exists()) {
                        outputDir.mkdirs();
                    }
                }
            }
        }

        outputFile = File.createTempFile(String.format("recording-%d", System.currentTimeMillis()), ".aac", outputDir);

        if (directory == null) {
            outputFile.deleteOnExit();
        }

        mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
    }

    /** Maps directory strings to Android file locations. */
    private File getDirectory(String directory) {
        return switch (directory) {
            case "DOCUMENTS" -> directoryProvider.getDocumentsDirectory();
            case "DATA", "LIBRARY" -> directoryProvider.getFilesDir(context);
            case "CACHE" -> directoryProvider.getCacheDir(context);
            case "EXTERNAL" -> directoryProvider.getExternalFilesDir(context);
            case "EXTERNAL_STORAGE" -> directoryProvider.getExternalStorageDirectory();
            default -> null;
        };
    }

    private void startVolumeMetering() {
        if (!options.volumeMetering()) {
            return;
        }

        handlerProvider.setupHandler();

        lowPassVolume = 0.0f;

        volumeRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaRecorder != null && currentRecordingStatus == CurrentRecordingStatus.RECORDING) {
                    int maxAmplitude = mediaRecorder.getMaxAmplitude();
                    float rawLinear = (float) maxAmplitude / 32767f;

                    // Consistency check: Same "Knee" logic used in Swift
                    float targetLevel = calculateVisualLevel(rawLinear);

                    // Low-pass filter for smoothing
                    lowPassVolume = (0.5f * targetLevel) + (0.5f * lowPassVolume);

                    if (onVolumeChanged != null) {
                        onVolumeChanged.accept(lowPassVolume);
                    }
                    handlerProvider.postDelayed(this, POLL_INTERVAL_MS);
                }
            }
        };
        handlerProvider.post(volumeRunnable);
    }

    private void stopVolumeMetering() {
        if (volumeRunnable != null) {
            handlerProvider.removeCallbacks(volumeRunnable);
        }
    }

    private float calculateVisualLevel(float rawLinear) {
        float threshold = 0.15f;
        float kneePoint = 0.8f;

        if (rawLinear <= threshold) {
            return (float) Math.sqrt(rawLinear / threshold) * kneePoint;
        } else {
            float excess = (rawLinear - threshold) / (1.0f - threshold);
            return kneePoint + (excess * (1.0f - kneePoint));
        }
    }

    /** Starts recording and requests audio focus. */
    public void startRecording() {
        requestAudioFocus();
        mediaRecorder.start();
        startVolumeMetering();
        currentRecordingStatus = CurrentRecordingStatus.RECORDING;
    }

    /** Stops recording and releases audio resources. */
    public void stopRecording() {
        stopVolumeMetering();

        if (mediaRecorder == null) {
            abandonAudioFocus();
            currentRecordingStatus = CurrentRecordingStatus.NONE;
            return;
        }

        try {
            if (currentRecordingStatus == CurrentRecordingStatus.RECORDING
                || currentRecordingStatus == CurrentRecordingStatus.PAUSED
                || currentRecordingStatus == CurrentRecordingStatus.INTERRUPTED) {
                mediaRecorder.stop();
            }
        } catch (IllegalStateException ignore) {
        } finally {
            mediaRecorder.release();
            mediaRecorder = null;
            abandonAudioFocus();
            currentRecordingStatus = CurrentRecordingStatus.NONE;
        }
    }

    /** Returns the output file for the current session. */
    public File getOutputFile() {
        return outputFile;
    }

    /** Returns the options provided at start time. */
    public RecordOptions getRecordOptions() {
        return options;
    }

    /** Pauses recording when supported by the OS version. */
    public boolean pauseRecording() throws NotSupportedOsVersion {
        if (sdkIntProvider.getSdkInt() < Build.VERSION_CODES.N) {
            throw new NotSupportedOsVersion();
        }

        if (currentRecordingStatus == CurrentRecordingStatus.RECORDING) {
            mediaRecorder.pause();
            stopVolumeMetering();
            currentRecordingStatus = CurrentRecordingStatus.PAUSED;
            return true;
        } else {
            return false;
        }
    }

    /** Resumes a paused or interrupted recording session. */
    public boolean resumeRecording() throws NotSupportedOsVersion {
        if (sdkIntProvider.getSdkInt() < Build.VERSION_CODES.N) {
            throw new NotSupportedOsVersion();
        }

        if (currentRecordingStatus == CurrentRecordingStatus.PAUSED || currentRecordingStatus == CurrentRecordingStatus.INTERRUPTED) {
            requestAudioFocus();
            mediaRecorder.resume();
            startVolumeMetering();
            currentRecordingStatus = CurrentRecordingStatus.RECORDING;
            return true;
        } else {
            return false;
        }
    }

    /** Returns the current recording status. */
    public CurrentRecordingStatus getCurrentStatus() {
        return currentRecordingStatus;
    }

    /** Deletes the output file from disk. */
    public boolean deleteOutputFile() {
        return outputFile.delete();
    }

    /** Simple capability check used for device validation. */
    public static boolean canPhoneCreateMediaRecorder(Context context) {
        return true;
    }

    /** Attempts to record a short sample to validate permission and hardware. */
    private static boolean canPhoneCreateMediaRecorderWhileHavingPermission(Context context) {
        CustomMediaRecorder tempMediaRecorder = null;
        try {
            tempMediaRecorder = new CustomMediaRecorder(context, new RecordOptions(null, null, false));
            tempMediaRecorder.startRecording();
            tempMediaRecorder.stopRecording();
            return true;
        } catch (Exception exp) {
            return exp.getMessage().startsWith("stop failed");
        } finally {
            if (tempMediaRecorder != null) tempMediaRecorder.deleteOutputFile();
        }
    }

    /** Requests audio focus for the recording session. */
    private void requestAudioFocus() {
        if (audioManager == null) {
            return;
        }

        if (sdkIntProvider.getSdkInt() >= Build.VERSION_CODES.O) {
            audioFocusRequest = audioFocusRequestFactory.create(this);
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    /** Releases audio focus when recording completes. */
    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }

        if (sdkIntProvider.getSdkInt() >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        } else {
            audioManager.abandonAudioFocus(this);
        }
    }

    /** Handles audio focus changes as recording interruptions. */
    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // For voice recording, ducking still degrades captured audio, so treat all loss types as interruptions.
                if (currentRecordingStatus == CurrentRecordingStatus.RECORDING) {
                    try {
                        if (sdkIntProvider.getSdkInt() >= Build.VERSION_CODES.N) {
                            mediaRecorder.pause();
                            stopVolumeMetering();
                            currentRecordingStatus = CurrentRecordingStatus.INTERRUPTED;
                            if (onInterruptionBegan != null) {
                                onInterruptionBegan.run();
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (currentRecordingStatus == CurrentRecordingStatus.INTERRUPTED) {
                    if (onInterruptionEnded != null) {
                        onInterruptionEnded.run();
                    }
                }
                break;
            default:
                break;
        }
    }
}
