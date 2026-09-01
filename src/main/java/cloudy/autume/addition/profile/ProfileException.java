package cloudy.autume.addition.profile;

import java.time.Duration;

/** Safe, typed failure from the QCA profile service. */
public final class ProfileException extends RuntimeException {
    public enum Code {
        INVALID_TARGET,
        PLAYER_NOT_FOUND,
        NO_SKYBLOCK_PROFILES,
        PROFILE_NOT_FOUND,
        RATE_LIMITED,
        UPSTREAM_UNAVAILABLE,
        SERVICE_UNAVAILABLE,
        UNAUTHORIZED,
        UNSUPPORTED_SCHEMA,
        INVALID_RESPONSE,
        RESPONSE_TOO_LARGE,
        CANCELLED
    }

    private final Code code;
    private final Duration retryAfter;
    private final int httpStatus;

    public ProfileException(Code code, String message) {
        this(code, message, Duration.ZERO, 0, null);
    }

    public ProfileException(Code code,
                            String message,
                            Duration retryAfter,
                            int httpStatus,
                            Throwable cause) {
        super(safeMessage(message), cause);
        this.code = java.util.Objects.requireNonNull(code, "code");
        this.retryAfter = retryAfter == null || retryAfter.isNegative()
                ? Duration.ZERO : retryAfter;
        this.httpStatus = Math.max(0, httpStatus);
    }

    public Code code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }

    public int httpStatus() {
        return httpStatus;
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "Profile request failed.";
        String normalized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return normalized.length() <= 256 ? normalized : normalized.substring(0, 256);
    }
}
