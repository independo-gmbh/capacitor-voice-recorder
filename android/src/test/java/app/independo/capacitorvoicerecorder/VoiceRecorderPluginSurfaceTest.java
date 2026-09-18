package app.independo.capacitorvoicerecorder;

import static org.junit.Assert.assertEquals;

import com.getcapacitor.PluginMethod;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * What the JS side can actually call.
 *
 * Capacitor finds plugin methods by reflecting over `@PluginMethod`, so a method
 * that exists on the service but was never exposed here compiles perfectly and
 * fails only at runtime, on a device, with "not implemented on android". That is
 * exactly how `simulateRecordingFailure` shipped broken to an Android tablet
 * while working on iOS, where the equivalent list is written out by hand and so
 * was not forgotten.
 *
 * Kept as a literal list rather than a count: it has to break when a name
 * changes, not only when one goes missing, and the diff is then the API change.
 */
public class VoiceRecorderPluginSurfaceTest {

    private static final Set<String> EXPECTED_METHODS = new TreeSet<>(
        Arrays.asList(
            "canDeviceVoiceRecord",
            "requestAudioRecordingPermission",
            "hasAudioRecordingPermission",
            "startRecording",
            "stopRecording",
            "pauseRecording",
            "resumeRecording",
            "getCurrentStatus",
            "simulateRecordingFailure"
        )
    );

    @Test
    public void everyMethodTheJsSideCallsIsExposedToTheBridge() {
        // Declared, not inherited: `Plugin` contributes `addListener`,
        // `checkPermissions` and friends, which are Capacitor's surface rather
        // than this plugin's and would only make this list noise.
        Set<String> exposed = Arrays.stream(VoiceRecorder.class.getDeclaredMethods())
            .filter(method -> method.getAnnotation(PluginMethod.class) != null)
            .map(Method::getName)
            .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(EXPECTED_METHODS, exposed);
    }
}
