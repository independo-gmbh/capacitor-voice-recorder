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

    /** Android context used to access system services. */
    private final Context context;

    public DefaultRecorderPlatform(Context context) {
        this.context = context;
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
        return new CustomMediaRecorder(context, options);
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
            return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
        } catch (IOException exp) {
            return null;
        }
    }

    /** Returns the file duration in milliseconds, or -1 on failure. */
    @Override
    public int getDurationMs(File recordedFile) {
        MediaPlayer mediaPlayer = null;
        try {
            mediaPlayer = new MediaPlayer();
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
        return Uri.fromFile(recordedFile).toString();
    }
}
