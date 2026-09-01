package cloudy.autume.addition.profile;

import com.google.gson.JsonObject;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class ProfileFixtures {
    static final String PLAYER_UUID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    static final String PROFILE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private ProfileFixtures() {
    }

    static String validJson() {
        long now = NOW.toEpochMilli();
        return """
                {
                  "schemaVersion": 1,
                  "partial": false,
                  "identity": {
                    "query": "NorthwestCloudy",
                    "uuid": "%s",
                    "name": "NorthwestCloudy",
                    "skinTextureUrl": ""
                  },
                  "selectedProfileId": "%s",
                  "profiles": [{
                    "profileId": "%s",
                    "cuteName": "Apple",
                    "selected": true,
                    "gameMode": "normal",
                    "memberCount": 1
                  }],
                  "sources": {
                    "identity": {
                      "status": "fresh",
                      "fetchedAt": %d,
                      "expiresAt": %d,
                      "staleUntil": %d,
                      "nextRefreshAt": %d,
                      "sourceVersion": "mojang-v1"
                    },
                    "player": {"status": "not_requested"},
                    "profiles": {
                      "status": "fresh",
                      "fetchedAt": %d,
                      "expiresAt": %d,
                      "staleUntil": %d,
                      "nextRefreshAt": %d,
                      "sourceVersion": "hypixel-v2"
                    },
                    "museum": {"status": "not_requested"},
                    "garden": {"status": "not_requested"},
                    "market": {"status": "not_requested"}
                  },
                  "sections": {
                    "overview": {
                      "status": "available",
                      "message": "",
                      "payload": {"skyBlockLevel": 123}
                    },
                    "museum": {"status": "not_loaded", "payload": {}},
                    "garden": {"status": "not_loaded", "payload": {}},
                    "dungeons": {"status": "available", "payload": {"mustBeIgnored": true}}
                  }
                }
                """.formatted(PLAYER_UUID, PROFILE_ID, PROFILE_ID,
                now, now + Duration.ofHours(24).toMillis(), now + Duration.ofHours(72).toMillis(),
                now + Duration.ofHours(24).toMillis(),
                now, now + Duration.ofHours(1).toMillis(), now + Duration.ofHours(24).toMillis(),
                now + Duration.ofHours(1).toMillis());
    }

    static ProfileSnapshot snapshot(Instant profileExpiresAt) {
        return snapshot(profileExpiresAt, false);
    }

    static ProfileSnapshot snapshotWithDirectIdentity(Instant profileExpiresAt) {
        return snapshot(profileExpiresAt, true);
    }

    private static ProfileSnapshot snapshot(Instant profileExpiresAt, boolean directIdentity) {
        EnumMap<ProfileDataSource, SourceMetadata> metadata =
                new EnumMap<>(ProfileDataSource.class);
        metadata.put(ProfileDataSource.IDENTITY, new SourceMetadata(ProfileDataSource.IDENTITY,
                ProfileSourceStatus.FRESH, NOW,
                directIdentity ? NOW : NOW.plus(Duration.ofHours(24)),
                directIdentity ? NOW : NOW.plus(Duration.ofHours(72)),
                directIdentity ? NOW : NOW.plus(Duration.ofHours(24)),
                directIdentity ? "direct" : "identity-v1"));
        metadata.put(ProfileDataSource.PROFILES, new SourceMetadata(ProfileDataSource.PROFILES,
                ProfileSourceStatus.FRESH, NOW, profileExpiresAt,
                NOW.plus(Duration.ofHours(24)), profileExpiresAt, "profiles-v1"));
        return new ProfileSnapshot(1,
                new ProfileIdentity("NorthwestCloudy", PLAYER_UUID, "NorthwestCloudy", ""),
                List.of(new ProfileDescriptor(PROFILE_ID, "Apple", true, "normal", 1)),
                PROFILE_ID, metadata,
                Map.of(ProfileSectionId.OVERVIEW, new ProfileSection(ProfileSectionId.OVERVIEW,
                        ProfileSectionStatus.AVAILABLE, "", new JsonObject())), false);
    }

    static String supplementJson(ProfileSectionId sectionId, Instant expiresAt) {
        String name = sectionId.wireName();
        long now = NOW.toEpochMilli();
        return """
                {
                  "schemaVersion": 1,
                  "identity": {"uuid": "%s"},
                  "profileId": "%s",
                  "sections": {
                    "%s": {
                      "status": "available",
                      "message": null,
                      "payload": {"loaded": true}
                    }
                  },
                  "sources": {
                    "%s": {
                      "status": "fresh",
                      "fetchedAt": %d,
                      "expiresAt": %d,
                      "staleUntil": %d,
                      "nextRefreshAt": %d,
                      "sourceVersion": "supplement-v1"
                    }
                  }
                }
                """.formatted(PLAYER_UUID, PROFILE_ID, name, name, now,
                expiresAt.toEpochMilli(), expiresAt.plus(Duration.ofHours(1)).toEpochMilli(),
                expiresAt.toEpochMilli());
    }

    static String shardJson(ShardBazaarSide side, Instant expiresAt) {
        long now = NOW.toEpochMilli();
        return """
                {
                  "schemaVersion": 1,
                  "source": "hypixel-bazaar",
                  "metadata": {
                    "status": "fresh",
                    "fetchedAt": %d,
                    "expiresAt": %d,
                    "staleUntil": %d,
                    "nextRefreshAt": %d,
                    "sourceVersion": "12345"
                  },
                  "side": "%s",
                  "prices": {"SHARD_TEST": 125.5, "SHARD_SECOND": 42}
                }
                """.formatted(now, expiresAt.toEpochMilli(),
                expiresAt.plus(Duration.ofMinutes(9)).toEpochMilli(),
                expiresAt.toEpochMilli(), side.wireName());
    }
}
