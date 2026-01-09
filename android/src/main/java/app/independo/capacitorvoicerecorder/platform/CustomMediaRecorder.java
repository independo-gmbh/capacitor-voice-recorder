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

/** MediaRecorder wrapper that manages audio focus and interruptions. */
public class CustomMediaRecorder implements AudioManager.OnAudioFocusChangeListener, RecorderAdapter {

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

    public CustomMediaRecorder(Context context, RecordOptions options) throws IOException {
        this(
            context,
            options,
            new DefaultMediaRecorderFactory(),
            new DefaultAudioManagerProvider(),
            new DefaultDirectoryProvider(),
            new DefaultSdkIntProvider(),
            new DefaultAudioFocusRequestFactory()
        );
    }

    CustomMediaRecorder(
        Context context,
        RecordOptions options,
        MediaRecorderFactory mediaRecorderFactory,
        AudioManagerProvider audioManagerProvider,
        DirectoryProvider directoryProvider,
        SdkIntProvider sdkIntProvider,
        AudioFocusRequestFactory audioFocusRequestFactory
    ) throws IOException {
        this.context = context;
        this.options = options;
        this.mediaRecorderFactory = mediaRecorderFactory;
        this.directoryProvider = directoryProvider;
        this.sdkIntProvider = sdkIntProvider;
        this.audioFocusRequestFactory = audioFocusRequestFactory;
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

    /** Starts recording and requests audio focus. */
    public void startRecording() {
        requestAudioFocus();
        mediaRecorder.start();
        currentRecordingStatus = CurrentRecordingStatus.RECORDING;
    }

    /** Stops recording and releases audio resources. */
    public void stopRecording() {
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
            tempMediaRecorder = new CustomMediaRecorder(context, new RecordOptions(null, null));
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
