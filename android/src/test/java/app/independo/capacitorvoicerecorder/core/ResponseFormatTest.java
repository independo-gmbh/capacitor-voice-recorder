package app.independo.capacitorvoicerecorder.core;

import static org.junit.Assert.assertEquals;

import com.getcapacitor.PluginConfig;
import java.lang.reflect.Constructor;
import org.json.JSONObject;
import org.junit.Test;

public class ResponseFormatTest {

    @Test
    public void defaultsToLegacyWhenConfigMissing() {
        PluginConfig config = createConfig(null);

        assertEquals(ResponseFormat.LEGACY, ResponseFormat.fromConfig(config));
    }

    @Test
    public void parsesNormalizedCaseInsensitively() {
        assertEquals(ResponseFormat.NORMALIZED, ResponseFormat.fromConfig(createConfig("normalized")));
        assertEquals(ResponseFormat.NORMALIZED, ResponseFormat.fromConfig(createConfig("NORMALIZED")));
        assertEquals(ResponseFormat.NORMALIZED, ResponseFormat.fromConfig(createConfig("Normalized")));
    }

    @Test
    public void defaultsToLegacyForUnknownValues() {
        assertEquals(ResponseFormat.LEGACY, ResponseFormat.fromConfig(createConfig("other")));
    }

    private PluginConfig createConfig(String responseFormat) {
        try {
            JSONObject json = new JSONObject();
            if (responseFormat != null) {
                json.put("responseFormat", responseFormat);
            }
            Constructor<PluginConfig> ctor = PluginConfig.class.getDeclaredConstructor(JSONObject.class);
            ctor.setAccessible(true);
            return ctor.newInstance(json);
        } catch (Exception exp) {
            throw new RuntimeException(exp);
        }
    }
}
