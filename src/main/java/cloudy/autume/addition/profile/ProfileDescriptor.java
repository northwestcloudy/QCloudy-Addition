package cloudy.autume.addition.profile;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** One selectable SkyBlock profile from the all-profiles response. */
public record ProfileDescriptor(String profileId,
                                String cuteName,
                                boolean selected,
                                String gameMode,
                                int memberCount) {
    private static final Pattern PROFILE_ID = Pattern.compile("[0-9a-f]{32}");

    public ProfileDescriptor {
        profileId = Objects.requireNonNull(profileId, "profileId")
                .replace("-", "").toLowerCase(Locale.ROOT);
        cuteName = Objects.requireNonNull(cuteName, "cuteName").trim();
        gameMode = gameMode == null ? "" : gameMode.trim();
        if (!PROFILE_ID.matcher(profileId).matches()) {
            throw new IllegalArgumentException("Invalid profile ID");
        }
        if (cuteName.isEmpty() || cuteName.length() > 64) {
            throw new IllegalArgumentException("Invalid profile cute name");
        }
        if (gameMode.length() > 64 || memberCount < 1 || memberCount > 100) {
            throw new IllegalArgumentException("Invalid profile descriptor");
        }
    }
}
