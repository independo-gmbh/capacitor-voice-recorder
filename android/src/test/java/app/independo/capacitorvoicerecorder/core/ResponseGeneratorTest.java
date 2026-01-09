package app.independo.capacitorvoicerecorder.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.getcapacitor.JSObject;
import org.junit.Test;

public class ResponseGeneratorTest {

    @Test
    public void fromBooleanWrapsTrueAndFalse() {
        JSObject trueResponse = ResponseGenerator.fromBoolean(true);
        JSObject falseResponse = ResponseGenerator.fromBoolean(false);

        assertTrue(trueResponse.optBoolean("value"));
        assertFalse(falseResponse.optBoolean("value"));
    }

    @Test
    public void dataResponseWrapsPayload() {
        JSObject response = ResponseGenerator.dataResponse("payload");

        assertEquals("payload", response.optString("value"));
    }

    @Test
    public void statusResponseWrapsStatus() {
        JSObject response = ResponseGenerator.statusResponse(CurrentRecordingStatus.RECORDING);

        assertEquals("RECORDING", response.optString("status"));
    }
}
