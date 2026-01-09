package app.independo.capacitorvoicerecorder.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.getcapacitor.JSObject;
import org.junit.Test;

public class RecordDataTest {

    @Test
    public void toJSObjectIncludesProvidedValues() {
        RecordData recordData = new RecordData("BASE64", 1200, "audio/aac", "file:///tmp/recording.aac");

        JSObject result = recordData.toJSObject();

        assertEquals("BASE64", result.optString("recordDataBase64"));
        assertEquals(1200, result.optInt("msDuration"));
        assertEquals("audio/aac", result.optString("mimeType"));
        assertEquals("file:///tmp/recording.aac", result.optString("uri"));
    }

    @Test
    public void toJSObjectPreservesNullValues() {
        RecordData recordData = new RecordData(null, 100, "audio/aac", null);

        JSObject result = recordData.toJSObject();

        assertFalse(result.has("recordDataBase64"));
        assertFalse(result.has("uri"));
    }
}
