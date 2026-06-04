package app.independo.capacitorvoicerecorder;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import app.independo.capacitorvoicerecorder.service.VoiceRecorderService;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.lang.reflect.Field;
import org.junit.Test;

public class VoiceRecorderTest {

    @Test
    public void getCurrentAmplitudeResolvesServiceValue() throws Exception {
        VoiceRecorder plugin = new VoiceRecorder();
        setService(plugin, new FixedAmplitudeService(0.42));
        PluginCall call = mock(PluginCall.class);

        plugin.getCurrentAmplitude(call);

        verify(call).resolve(argThat((JSObject response) -> response.optDouble("value") == 0.42));
    }

    private static void setService(VoiceRecorder plugin, VoiceRecorderService service) throws Exception {
        Field serviceField = VoiceRecorder.class.getDeclaredField("service");
        serviceField.setAccessible(true);
        serviceField.set(plugin, service);
    }

    private static final class FixedAmplitudeService extends VoiceRecorderService {
        private final double amplitude;

        FixedAmplitudeService(double amplitude) {
            super(null, null);
            this.amplitude = amplitude;
        }

        @Override
        public double getCurrentAmplitude() {
            return amplitude;
        }
    }
}
