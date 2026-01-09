package app.independo.capacitorvoicerecorder.platform;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Base64;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.adapters.RecorderPlatform;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/** Default Android platform adapter for recording and file IO. */
public class DefaultRecorderPlatform implements RecorderPlatform {

    interface RecorderFactory {
        RecorderAdapter create(Context context, RecordOptions options) throws Exception;
    }

    interface MediaPlayerFactory {
        MediaPlayer create();
    }

    interface UriConverter {
        String toUri(File recordedFile);
    }

    interface Base64Encoder {
        String encode(byte[] data);
    }

    private static final class DefaultRecorderFactory implements RecorderFactory {
        @Override
        public RecorderAdapter create(Context context, RecordOptions options) throws Exception {
            return new CustomMediaRecorder(context, options);
        }
    }

    private static final class DefaultMediaPlayerFactory implements MediaPlayerFactory {
        @Override
        public MediaPlayer create() {
            return new MediaPlayer();
        }
    }

    private static final class DefaultUriConverter implements UriConverter {
        @Override
        public String toUri(File recordedFile) {
            return Uri.fromFile(recordedFile).toString();
        }
    }

    private static final class DefaultBase64Encoder implements Base64Encoder {
        @Override
        public String encode(byte[] data) {
            return Base64.encodeToString(data, Base64.DEFAULT);
        }
    }

    /** Android context used to access system services. */
    private final Context context;
    /** Recorder factory for platform creation. */
    private final RecorderFactory recorderFactory;
    /** Media player factory for duration lookup. */
    private final MediaPlayerFactory mediaPlayerFactory;
    /** Uri converter for response payloads. */
    private final UriConverter uriConverter;
    /** Base64 encoder for file payloads. */
    private final Base64Encoder base64Encoder;

    public DefaultRecorderPlatform(Context context) {
        this(context, new DefaultRecorderFactory(), new DefaultMediaPlayerFactory(), new DefaultUriConverter(), new DefaultBase64Encoder());
    }

    DefaultRecorderPlatform(
        Context context,
        RecorderFactory recorderFactory,
        MediaPlayerFactory mediaPlayerFactory,
        UriConverter uriConverter,
        Base64Encoder base64Encoder
    ) {
        this.context = context;
        this.recorderFactory = recorderFactory;
        this.mediaPlayerFactory = mediaPlayerFactory;
        this.uriConverter = uriConverter;
        this.base64Encoder = base64Encoder;
    }

    /** Returns whether the device can create a MediaRecorder instance. */
    @Override
    public boolean canDeviceVoiceRecord() {
        return CustomMediaRecorder.canPhoneCreateMediaRecorder(context);
    }

    /** Returns true when the system audio mode indicates another recorder. */
    @Override
    public boolean isMicrophoneOccupied() {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager == null) return true;
        return audioManager.getMode() != AudioManager.MODE_NORMAL;
    }

    /** Creates the recorder adapter for the provided options. */
    @Override
    public RecorderAdapter createRecorder(RecordOptions options) throws Exception {
        return recorderFactory.create(context, options);
    }

    /** Reads the recorded file as base64, returning null on failure. */
    @Override
    public String readFileAsBase64(File recordedFile) {
        try (BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(recordedFile))) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            return base64Encoder.encode(outputStream.toByteArray());
        } catch (IOException exp) {
            return null;
        }
    }

    /** Returns the file duration in milliseconds, or -1 on failure. */
    @Override
    public int getDurationMs(File recordedFile) {
        MediaPlayer mediaPlayer = null;
        try {
            mediaPlayer = mediaPlayerFactory.create();
            mediaPlayer.setDataSource(recordedFile.getAbsolutePath());
            mediaPlayer.prepare();
            return mediaPlayer.getDuration();
        } catch (Exception ignore) {
            return -1;
        } finally {
            if (mediaPlayer != null) {
                mediaPlayer.release();
            }
        }
    }

    /** Returns a file:// URI for the given recording. */
    @Override
    public String toUri(File recordedFile) {
        return uriConverter.toUri(recordedFile);
    }
}
