package cloudy.autume.addition.profile.ui;

import cloudy.autume.addition.profile.ProfileDescriptor;
import cloudy.autume.addition.profile.ProfileIdentity;
import cloudy.autume.addition.profile.ProfileSection;
import cloudy.autume.addition.profile.ProfileSectionId;
import cloudy.autume.addition.profile.ProfileSectionStatus;
import cloudy.autume.addition.profile.ProfileSnapshot;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProfilePresentationMapperTest {
    private static final String PLAYER_UUID = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PROFILE_ID = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void mapsOverviewAndSkillsToCardsAndProgressInsteadOfRawRows() {
        ProfileSnapshot snapshot = snapshot(Map.of(
                ProfileSectionId.OVERVIEW, payload("""
                        {
                          "player":{"newPackageRank":"MVP_PLUS"},
                          "profile":{"banking":{"balance":1090000000}},
                          "currencies":{"coin_purse":538560},
                          "leveling":{"experience":47239},
                          "playerData":{"fairy_souls_collected":286}
                        }
                        """),
                ProfileSectionId.SKILLS, payload("""
                        {"playerData":{"experience":{
                          "SKILL_COMBAT":3500000,
                          "SKILL_FARMING":1200000,
                          "ignored_history":999999999
                        }}}
                        """)));

        ProfilePresentationMapper.SectionView overview =
                ProfilePresentationMapper.section(snapshot, ProfileSectionId.OVERVIEW, false);
        ProfilePresentationMapper.SectionView skills =
                ProfilePresentationMapper.section(snapshot, ProfileSectionId.SKILLS, false);

        assertTrue(overview.blocks().stream()
                .anyMatch(ProfilePresentationMapper.StatGridBlock.class::isInstance));
        assertTrue(overview.blocks().stream()
                .anyMatch(ProfilePresentationMapper.ProgressBlock.class::isInstance));
        ProfilePresentationMapper.ProgressBlock skillProgress = assertInstanceOf(
                ProfilePresentationMapper.ProgressBlock.class, skills.blocks().getFirst());
        assertEquals(List.of("Combat", "Farming"),
                skillProgress.entries().stream().map(ProfilePresentationMapper.ProgressEntry::label).toList());
        assertFalse(skills.blocks().stream()
                .anyMatch(ProfilePresentationMapper.ListBlock.class::isInstance));
    }

    @Test
    void preservesInventorySlotsAndMarketIdentityForIconHovering() {
        ProfileSnapshot snapshot = snapshot(Map.of(ProfileSectionId.INVENTORY, payload("""
                {"inv_contents":{"decodeStatus":"decoded","items":[{
                  "slot":2,
                  "count":4,
                  "itemId":"ENCHANTED_DIAMOND",
                  "variantKey":"0123456789abcdef0123456789abcdef",
                  "displayName":"Enchanted Diamond",
                  "rarity":"UNCOMMON"
                }]}}
                """)));

        ProfilePresentationMapper.SectionView view =
                ProfilePresentationMapper.section(snapshot, ProfileSectionId.INVENTORY, false);
        ProfilePresentationMapper.ItemGridBlock grid = assertInstanceOf(
                ProfilePresentationMapper.ItemGridBlock.class, view.blocks().getFirst());
        ProfilePresentationMapper.ItemView item = grid.items().getFirst();

        assertEquals("Inventory", grid.title());
        assertEquals(9, grid.columns());
        assertEquals(36, grid.minimumSlots());
        assertEquals(2, item.slot());
        assertEquals(4, item.count());
        assertEquals("ENCHANTED_DIAMOND", item.itemId());
        assertEquals("0123456789abcdef0123456789abcdef", item.variantKey());
        assertEquals("Enchanted Diamond", item.displayName());
        assertEquals("UNCOMMON", item.rarity());
    }

    @Test
    void nestedProjectionLimitKeepsUsableGridAndAddsSectionNotice() {
        ProfileSnapshot snapshot = snapshot(Map.of(ProfileSectionId.INVENTORY, payload("""
                {"inv_contents":{"decodeStatus":"decoded","items":[
                  {"slot":0,"count":1,"itemId":"HYPERION","displayName":"Hyperion"},
                  {"status":"truncated","omitted":40}
                ]}}
                """)));

        ProfilePresentationMapper.SectionView view =
                ProfilePresentationMapper.section(snapshot, ProfileSectionId.INVENTORY, true);

        assertTrue(view.blocks().stream()
                .anyMatch(ProfilePresentationMapper.ItemGridBlock.class::isInstance));
        assertTrue(view.blocks().stream()
                .filter(ProfilePresentationMapper.NoticeBlock.class::isInstance)
                .map(ProfilePresentationMapper.NoticeBlock.class::cast)
                .anyMatch(notice -> notice.message().contains("省略")));
        assertFalse(view.blocks().getFirst() instanceof ProfilePresentationMapper.EmptyBlock);
    }

    @Test
    void unknownPayloadNeverFallsBackToRawJsonTree() {
        ProfileSnapshot snapshot = snapshot(Map.of(ProfileSectionId.MISC, payload("""
                {"unknownServerField":{"secret":"must not be rendered"}}
                """)));

        ProfilePresentationMapper.SectionView view =
                ProfilePresentationMapper.section(snapshot, ProfileSectionId.MISC, false);

        assertEquals(1, view.blocks().size());
        assertInstanceOf(ProfilePresentationMapper.EmptyBlock.class, view.blocks().getFirst());
    }

    private static ProfileSection payload(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return new ProfileSection(ProfileSectionId.OVERVIEW,
                ProfileSectionStatus.AVAILABLE, "", object);
    }

    private static ProfileSnapshot snapshot(Map<ProfileSectionId, ProfileSection> rawSections) {
        LinkedHashMap<ProfileSectionId, ProfileSection> sections = new LinkedHashMap<>();
        rawSections.forEach((id, section) -> sections.put(id,
                new ProfileSection(id, section.status(), section.message(), section.payload())));
        return new ProfileSnapshot(1,
                new ProfileIdentity("NorthWestCloudy", PLAYER_UUID, "NorthWestCloudy", ""),
                List.of(new ProfileDescriptor(PROFILE_ID, "Blueberry", true, "normal", 1)),
                PROFILE_ID, Map.of(), sections, false);
    }
}
