package cloudy.autume.addition.profile;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Schema-v1 read model returned by the QCA profile service. */
public final class ProfileSnapshot {
    public static final int SCHEMA_VERSION = 1;

    private final ProfileIdentity identity;
    private final List<ProfileDescriptor> profiles;
    private final String selectedProfileId;
    private final Map<ProfileDataSource, SourceMetadata> sourceMetadata;
    private final Map<ProfileSectionId, ProfileSection> sections;
    private final boolean partial;

    public ProfileSnapshot(int schemaVersion,
                           ProfileIdentity identity,
                           List<ProfileDescriptor> profiles,
                           String selectedProfileId,
                           Map<ProfileDataSource, SourceMetadata> sourceMetadata,
                           Map<ProfileSectionId, ProfileSection> sections,
                           boolean partial) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported profile schema: " + schemaVersion);
        }
        this.identity = Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(profiles, "profiles");
        if (profiles.isEmpty() || profiles.size() > 64) {
            throw new IllegalArgumentException("Profile list must contain between 1 and 64 entries");
        }
        this.profiles = Collections.unmodifiableList(new ArrayList<>(profiles));
        this.selectedProfileId = canonicalProfileId(selectedProfileId);
        if (this.profiles.stream().noneMatch(profile -> profile.profileId().equals(this.selectedProfileId))) {
            throw new IllegalArgumentException("Selected profile is absent from profile list");
        }

        EnumMap<ProfileDataSource, SourceMetadata> metadata =
                new EnumMap<>(ProfileDataSource.class);
        if (sourceMetadata != null) metadata.putAll(sourceMetadata);
        for (ProfileDataSource source : ProfileDataSource.values()) {
            SourceMetadata value = metadata.get(source);
            if (value == null) metadata.put(source, SourceMetadata.notRequested(source));
            else if (value.source() != source) {
                throw new IllegalArgumentException("Mismatched source metadata key");
            }
        }
        this.sourceMetadata = Collections.unmodifiableMap(metadata);

        LinkedHashMap<ProfileSectionId, ProfileSection> copiedSections = new LinkedHashMap<>();
        if (sections != null) {
            for (Map.Entry<ProfileSectionId, ProfileSection> entry : sections.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null
                        || entry.getValue().id() != entry.getKey()) {
                    throw new IllegalArgumentException("Invalid profile section entry");
                }
                copiedSections.put(entry.getKey(), entry.getValue());
            }
        }
        this.sections = Collections.unmodifiableMap(copiedSections);
        this.partial = partial || copiedSections.values().stream().anyMatch(section ->
                section.status() == ProfileSectionStatus.PRIVATE
                        || section.status() == ProfileSectionStatus.NOT_FOUND
                        || section.status() == ProfileSectionStatus.ERROR);
    }

    public int schemaVersion() {
        return SCHEMA_VERSION;
    }

    public ProfileIdentity identity() {
        return identity;
    }

    public List<ProfileDescriptor> profiles() {
        return profiles;
    }

    public String selectedProfileId() {
        return selectedProfileId;
    }

    public Map<ProfileDataSource, SourceMetadata> sourceMetadata() {
        return sourceMetadata;
    }

    public Map<ProfileSectionId, ProfileSection> sections() {
        return sections;
    }

    public Optional<ProfileSection> section(ProfileSectionId id) {
        return Optional.ofNullable(sections.get(id));
    }

    public boolean partial() {
        return partial;
    }

    public boolean stale() {
        return sourceMetadata.values().stream().anyMatch(SourceMetadata::stale)
                || sections.values().stream()
                .anyMatch(section -> section.status() == ProfileSectionStatus.STALE);
    }

    ProfileSnapshot withSupplement(ProfileSupplement supplement) {
        Objects.requireNonNull(supplement, "supplement");
        if (!identity.uuid().equals(supplement.playerUuid())
                || !selectedProfileId.equals(supplement.profileId())) {
            throw new ProfileException(ProfileException.Code.INVALID_RESPONSE,
                    "The supplementary profile response did not match the selected profile.");
        }
        EnumMap<ProfileDataSource, SourceMetadata> updatedMetadata =
                new EnumMap<>(ProfileDataSource.class);
        updatedMetadata.putAll(sourceMetadata);
        updatedMetadata.put(supplement.metadata().source(), supplement.metadata());
        LinkedHashMap<ProfileSectionId, ProfileSection> updatedSections =
                new LinkedHashMap<>(sections);
        updatedSections.put(supplement.section().id(), supplement.section());
        return new ProfileSnapshot(SCHEMA_VERSION, identity, profiles, selectedProfileId,
                updatedMetadata, updatedSections, partial);
    }

    /** Earliest boundary that may cap a ten-minute in-process cache entry. */
    Instant sessionCacheBoundary(Instant now, Instant maximum) {
        Instant result = maximum;
        for (SourceMetadata metadata : sourceMetadata.values()) {
            Optional<Instant> boundary = metadata.localCacheBoundary(now);
            if (metadata.available() && boundary.isEmpty()) {
                if (metadata.source() == ProfileDataSource.IDENTITY
                        && metadata.instantaneousFreshBoundary()) continue;
                return now;
            }
            if (metadata.status() == ProfileSourceStatus.ERROR) return now;
            if (boundary.isPresent() && boundary.get().isBefore(result)) result = boundary.get();
        }
        return result;
    }

    private static String canonicalProfileId(String value) {
        if (value == null) throw new IllegalArgumentException("Missing selected profile ID");
        String canonical = value.replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if (!canonical.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Invalid selected profile ID");
        }
        return canonical;
    }
}
