package app.independo.capacitorvoicerecorder.adapters;

/** Functional interface used to check microphone permission. */
@FunctionalInterface
public interface PermissionChecker {
    /** Returns whether the app currently has microphone permission. */
    boolean hasAudioPermission();
}
