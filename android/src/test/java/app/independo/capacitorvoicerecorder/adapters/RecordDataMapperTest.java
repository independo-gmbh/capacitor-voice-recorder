package app.independo.capacitorvoicerecorder.adapters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import app.independo.capacitorvoicerecorder.core.RecordData;
import com.getcapacitor.JSObject;
import org.junit.Test;

public class RecordDataMapperTest {

    @Test
    public void toLegacyJSObjectCopiesAllFields() {
        RecordData recordData = new RecordData("BASE64", 1200, "audio/aac", "file:///tmp/recording.aac");

        JSObject result = RecordDataMapper.toLegacyJSObject(recordData);

        assertEquals("BASE64", result.optString("recordDataBase64"));
        assertEquals(1200, result.optInt("msDuration"));
        assertEquals("audio/aac", result.optString("mimeType"));
        assertEquals("file:///tmp/recording.aac", result.optString("uri"));
    }

    @Test
    public void toNormalizedJSObjectPrefersUriOverBase64() {
        RecordData recordData = new RecordData("BASE64", 1200, "audio/aac", "file:///tmp/recording.aac");

        JSObject result = RecordDataMapper.toNormalizedJSObject(recordData);

        assertTrue(result.has("uri"));
        assertEquals("file:///tmp/recording.aac", result.optString("uri"));
        assertFalse(result.has("recordDataBase64"));
    }

    @Test
    public void toNormalizedJSObjectUsesBase64WhenUriMissing() {
        RecordData recordData = new RecordData("BASE64", 1200, "audio/aac", null);

        JSObject result = RecordDataMapper.toNormalizedJSObject(recordData);

        assertTrue(result.has("recordDataBase64"));
        assertEquals("BASE64", result.optString("recordDataBase64"));
        assertFalse(result.has("uri"));
    }

    @Test
    public void toNormalizedJSObjectOmitsEmptyBase64() {
        RecordData recordData = new RecordData("", 1200, "audio/aac", null);

        JSObject result = RecordDataMapper.toNormalizedJSObject(recordData);

        assertFalse(result.has("recordDataBase64"));
        assertFalse(result.has("uri"));
    }
}
