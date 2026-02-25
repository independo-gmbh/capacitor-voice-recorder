package app.independo.capacitorvoicerecorder.core;

import com.getcapacitor.JSObject;

/** Recording payload returned to the bridge layer. */
public class RecordData {

    /** File URI when returning recordings by reference. */
    private String uri;
    /** Base64 payload for inline recording data. */
    private String recordDataBase64;
    /** MIME type of the audio payload. */
    private String mimeType;
    /** File extension / format without a leading dot (for example: aac, m4a, mp3). */
    private String fileExtension;
    /** Recording duration in milliseconds. */
    private int msDuration;

    public RecordData() {}

    public RecordData(String recordDataBase64, int msDuration, String mimeType, String uri) {
        this(recordDataBase64, msDuration, mimeType, inferFileExtension(mimeType, uri), uri);
    }

    public RecordData(String recordDataBase64, int msDuration, String mimeType, String fileExtension, String uri) {
        this.recordDataBase64 = recordDataBase64;
        this.msDuration = msDuration;
        this.mimeType = mimeType;
        this.fileExtension = fileExtension;
        this.uri = uri;
    }

    /** Returns the base64 payload, if present. */
    public String getRecordDataBase64() {
        return recordDataBase64;
    }

    public void setRecordDataBase64(String recordDataBase64) {
        this.recordDataBase64 = recordDataBase64;
    }

    /** Returns the recording duration in milliseconds. */
    public int getMsDuration() {
        return msDuration;
    }

    public void setMsDuration(int msDuration) {
        this.msDuration = msDuration;
    }

    /** Returns the MIME type of the recording. */
    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    /** Returns the file extension / format of the recording. */
    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /** Returns the file URI, if present. */
    public String getUri() {
        return uri;
    }

    /** Serializes the record data into the legacy JS payload shape. */
    public JSObject toJSObject() {
        JSObject toReturn = new JSObject();
        toReturn.put("recordDataBase64", recordDataBase64);
        toReturn.put("msDuration", msDuration);
        toReturn.put("mimeType", mimeType);
        toReturn.put("fileExtension", fileExtension);
        toReturn.put("uri", uri);
        return toReturn;
    }

    private static String inferFileExtension(String mimeType, String uri) {
        if (uri != null) {
            int dotIndex = uri.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < uri.length() - 1) {
                return uri.substring(dotIndex + 1).toLowerCase();
            }
        }

        if ("audio/mp4".equals(mimeType)) return "m4a";
        if ("audio/mpeg".equals(mimeType)) return "mp3";
        if ("audio/wav".equals(mimeType)) return "wav";
        if ("audio/webm".equals(mimeType) || "audio/webm;codecs=opus".equals(mimeType)) return "webm";
        if ("audio/ogg;codecs=opus".equals(mimeType) || "audio/ogg;codecs=vorbis".equals(mimeType)) return "ogg";
        if ("audio/aac".equals(mimeType)) return "aac";

        return "";
    }
}
