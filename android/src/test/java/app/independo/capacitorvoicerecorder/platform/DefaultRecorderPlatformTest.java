package app.independo.capacitorvoicerecorder.platform;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import app.independo.capacitorvoicerecorder.adapters.RecorderAdapter;
import app.independo.capacitorvoicerecorder.core.RecordOptions;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DefaultRecorderPlatformTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private DefaultRecorderPlatform createPlatform(
        Context context,
        DefaultRecorderPlatform.RecorderFactory recorderFactory,
        DefaultRecorderPlatform.MediaPlayerFactory mediaPlayerFactory,
        DefaultRecorderPlatform.UriConverter uriConverter,
        DefaultRecorderPlatform.Base64Encoder base64Encoder
    ) {
        return new DefaultRecorderPlatform(context, recorderFactory, mediaPlayerFactory, uriConverter, base64Encoder);
    }

    @Test
    public void isMicrophoneOccupiedReturnsTrueWhenAudioManagerMissing() {
        Context context = mock(Context.class);
        when(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            MediaPlayer::new,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );

        assertTrue(platform.isMicrophoneOccupied());
    }

    @Test
    public void isMicrophoneOccupiedReturnsFalseWhenModeNormal() {
        Context context = mock(Context.class);
        AudioManager audioManager = mock(AudioManager.class);
        when(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager);
        when(audioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            MediaPlayer::new,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );

        assertFalse(platform.isMicrophoneOccupied());
    }

    @Test
    public void createRecorderUsesFactory() throws Exception {
        Context context = mock(Context.class);
        RecorderAdapter recorder = mock(RecorderAdapter.class);
        DefaultRecorderPlatform.RecorderFactory recorderFactory = mock(DefaultRecorderPlatform.RecorderFactory.class);
        when(recorderFactory.create(context, new RecordOptions(null, null, false))).thenReturn(recorder);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            recorderFactory,
            MediaPlayer::new,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );

        RecorderAdapter result = platform.createRecorder(new RecordOptions(null, null, false));

        assertEquals(recorder, result);
        verify(recorderFactory).create(context, new RecordOptions(null, null, false));
    }

    @Test
    public void readFileAsBase64ReturnsEncodedData() throws Exception {
        Context context = mock(Context.class);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            MediaPlayer::new,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );
        File file = tempFolder.newFile("payload.aac");
        Files.write(file.toPath(), "payload".getBytes());

        String result = platform.readFileAsBase64(file);

        String expected = Base64.getEncoder().encodeToString("payload".getBytes());
        assertEquals(expected, result.trim());
    }

    @Test
    public void readFileAsBase64ReturnsNullOnError() {
        Context context = mock(Context.class);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            MediaPlayer::new,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );
        File missingFile = new File("missing-recording.aac");

        assertNull(platform.readFileAsBase64(missingFile));
    }

    @Test
    public void getDurationMsReturnsDurationAndReleases() throws Exception {
        Context context = mock(Context.class);
        MediaPlayer mediaPlayer = mock(MediaPlayer.class);
        when(mediaPlayer.getDuration()).thenReturn(1500);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            () -> mediaPlayer,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );
        File file = tempFolder.newFile("duration.aac");

        int duration = platform.getDurationMs(file);

        assertEquals(1500, duration);
        verify(mediaPlayer).setDataSource(file.getAbsolutePath());
        verify(mediaPlayer).prepare();
        verify(mediaPlayer).release();
    }

    @Test
    public void getDurationMsReturnsMinusOneOnFailure() throws Exception {
        Context context = mock(Context.class);
        MediaPlayer mediaPlayer = mock(MediaPlayer.class);
        doThrow(new RuntimeException("prepare failed")).when(mediaPlayer).prepare();
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            () -> mediaPlayer,
            file -> "file://ignored",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );
        File file = tempFolder.newFile("duration-fail.aac");

        int duration = platform.getDurationMs(file);

        assertEquals(-1, duration);
        verify(mediaPlayer).release();
    }

    @Test
    public void toUriUsesConverter() {
        Context context = mock(Context.class);
        DefaultRecorderPlatform platform = createPlatform(
            context,
            (ctx, options) -> mock(RecorderAdapter.class),
            MediaPlayer::new,
            file -> "file://converted",
            data -> java.util.Base64.getEncoder().encodeToString(data)
        );
        File file = new File("recording.aac");

        assertEquals("file://converted", platform.toUri(file));
    }
}
