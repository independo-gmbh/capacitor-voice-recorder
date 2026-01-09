package app.independo.capacitorvoicerecorder.adapters;

import app.independo.capacitorvoicerecorder.core.RecordData;
import com.getcapacitor.JSObject;

/** Maps internal record data into legacy or normalized JS payloads. */
public final class RecordDataMapper {

    private RecordDataMapper() {}

    /** Converts record data to the legacy payload shape. */
    public static JSObject toLegacyJSObject(RecordData recordData) {
        return recordData.toJSObject();
    }

    /** Converts record data to the normalized payload shape. */
    public static JSObject toNormalizedJSObject(RecordData recordData) {
        JSObject normalized = new JSObject();
        normalized.put("msDuration", recordData.getMsDuration());
        normalized.put("mimeType", recordData.getMimeType());

        String uri = recordData.getUri();
        String recordDataBase64 = recordData.getRecordDataBase64();
        if (uri != null && !uri.isEmpty()) {
            normalized.put("uri", uri);
        } else if (recordDataBase64 != null && !recordDataBase64.isEmpty()) {
            normalized.put("recordDataBase64", recordDataBase64);
        }

        return normalized;
    }
}
