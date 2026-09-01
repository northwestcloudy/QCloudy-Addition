package cloudy.autume.addition.profile;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfileJsonParserTest {
    @Test
    void parsesSchemaOneAndIgnoresUnsupportedDungeonPayload() {
        ProfileSnapshot snapshot = ProfileJsonParser.parse(ProfileFixtures.validJson());

        assertEquals(1, snapshot.schemaVersion());
        assertEquals(ProfileFixtures.PLAYER_UUID, snapshot.identity().uuid());
        assertEquals(ProfileFixtures.PROFILE_ID, snapshot.selectedProfileId());
        assertEquals(1, snapshot.profiles().size());
        assertEquals(ProfileSourceStatus.FRESH,
                snapshot.sourceMetadata().get(ProfileDataSource.PROFILES).status());
        assertEquals(ProfileSourceStatus.NOT_REQUESTED,
                snapshot.sourceMetadata().get(ProfileDataSource.PLAYER).status());
        assertTrue(snapshot.section(ProfileSectionId.OVERVIEW).isPresent());
        assertEquals(3, snapshot.sections().size());
        assertFalse(ProfileSectionId.fromWireName("dungeons").isPresent());
        assertFalse(snapshot.partial());
    }

    @Test
    void sectionPayloadIsDefensivelyCopied() {
        ProfileSection section = ProfileJsonParser.parse(ProfileFixtures.validJson())
                .section(ProfileSectionId.OVERVIEW).orElseThrow();
        var copy = section.payload();
        copy.addProperty("mutated", true);
        assertFalse(section.payload().has("mutated"));
    }

    @Test
    void rejectsUnknownSchemaAndInvalidTimeOrdering() {
        ProfileException schema = assertThrows(ProfileException.class,
                () -> ProfileJsonParser.parse(ProfileFixtures.validJson()
                        .replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertEquals(ProfileException.Code.UNSUPPORTED_SCHEMA, schema.code());

        String invalidTimes = ProfileFixtures.validJson().replaceFirst(
                "\"expiresAt\": \\d+", "\"expiresAt\": 1");
        ProfileException invalid = assertThrows(ProfileException.class,
                () -> ProfileJsonParser.parse(invalidTimes));
        assertEquals(ProfileException.Code.INVALID_RESPONSE, invalid.code());
    }

    @Test
    void parsesStableErrorEnvelope() {
        ProfileException error = ProfileJsonParser.parseError("""
                {"schemaVersion":1,"error":{
                  "code":"NO_SKYBLOCK_PROFILES","message":"No SkyBlock profiles."
                }}
                """, 404, Duration.ofSeconds(12));
        assertEquals(ProfileException.Code.NO_SKYBLOCK_PROFILES, error.code());
        assertEquals(404, error.httpStatus());
        assertEquals(Duration.ofSeconds(12), error.retryAfter());
    }

    @Test
    void validatesSupplementIdentifiersAndIndependentSourceMetadata() {
        ProfileSupplement supplement = ProfileJsonParser.parseSupplement(
                ProfileFixtures.supplementJson(ProfileSectionId.GARDEN,
                        ProfileFixtures.NOW.plus(Duration.ofHours(12))),
                ProfileFixtures.PLAYER_UUID, ProfileFixtures.PROFILE_ID,
                ProfileSectionId.GARDEN);

        assertEquals(ProfileSectionId.GARDEN, supplement.section().id());
        assertEquals(ProfileDataSource.GARDEN, supplement.metadata().source());
        assertEquals(ProfileSourceStatus.FRESH, supplement.metadata().status());

        ProfileException mismatch = assertThrows(ProfileException.class,
                () -> ProfileJsonParser.parseSupplement(
                        ProfileFixtures.supplementJson(ProfileSectionId.GARDEN,
                                ProfileFixtures.NOW.plus(Duration.ofHours(12))),
                        "cccccccccccccccccccccccccccccccc",
                        ProfileFixtures.PROFILE_ID, ProfileSectionId.GARDEN));
        assertEquals(ProfileException.Code.INVALID_RESPONSE, mismatch.code());
    }
}
