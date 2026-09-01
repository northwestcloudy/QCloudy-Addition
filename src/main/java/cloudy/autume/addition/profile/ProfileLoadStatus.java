package cloudy.autume.addition.profile;

/** Overall successful load state. Errors complete the request exceptionally. */
public enum ProfileLoadStatus {
    READY,
    PARTIAL,
    STALE
}
