package cloudy.autume.addition.profile;

import com.google.gson.JsonObject;

import java.util.Objects;

/** Immutable section envelope; payload is defensively copied on input and output. */
public final class ProfileSection {
    private final ProfileSectionId id;
    private final ProfileSectionStatus status;
    private final String message;
    private final JsonObject payload;

    public ProfileSection(ProfileSectionId id,
                          ProfileSectionStatus status,
                          String message,
                          JsonObject payload) {
        this.id = Objects.requireNonNull(id, "id");
        this.status = Objects.requireNonNull(status, "status");
        this.message = message == null ? "" : message;
        this.payload = payload == null ? new JsonObject() : payload.deepCopy();
        if (this.message.length() > 512) {
            throw new IllegalArgumentException("Section message is too long");
        }
    }

    public ProfileSectionId id() {
        return id;
    }

    public ProfileSectionStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public JsonObject payload() {
        return payload.deepCopy();
    }

    public boolean available() {
        return status == ProfileSectionStatus.AVAILABLE || status == ProfileSectionStatus.STALE;
    }
}
