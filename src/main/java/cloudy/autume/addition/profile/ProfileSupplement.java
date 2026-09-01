package cloudy.autume.addition.profile;

import java.util.Locale;
import java.util.Objects;

/** One independently fetched Museum or Garden section. */
record ProfileSupplement(String playerUuid,
                         String profileId,
                         ProfileSection section,
                         SourceMetadata metadata) {
    ProfileSupplement {
        playerUuid = canonicalId(playerUuid, "player UUID");
        profileId = canonicalId(profileId, "profile ID");
        section = Objects.requireNonNull(section, "section");
        metadata = Objects.requireNonNull(metadata, "metadata");
        ProfileDataSource expectedSource = switch (section.id()) {
            case MUSEUM -> ProfileDataSource.MUSEUM;
            case GARDEN -> ProfileDataSource.GARDEN;
            default -> throw new IllegalArgumentException(
                    "Only Museum and Garden support supplementary loading");
        };
        if (metadata.source() != expectedSource) {
            throw new IllegalArgumentException("Supplement source does not match its section");
        }
    }

    private static String canonicalId(String value, String label) {
        if (value == null) throw new IllegalArgumentException("Missing " + label);
        String canonical = value.replace("-", "").toLowerCase(Locale.ROOT);
        if (!canonical.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid " + label);
        }
        return canonical;
    }
}
