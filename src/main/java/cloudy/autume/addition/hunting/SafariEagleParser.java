package cloudy.autume.addition.hunting;

import cloudy.autume.addition.tracker.PetTier;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure parsers for Eagle evidence already received by the client.
 *
 * <p>Callers must pass one bounded Safari Essence Shop item/widget, not an
 * arbitrary screen or nearby-entity dump. In particular, entity evidence is
 * ignored unless the caller has independently bound that entity to the local
 * player.</p>
 */
public final class SafariEagleParser {
    private static final Pattern EAGLE = Pattern.compile("(?i)\\bEagle(?:\\s+Pet)?\\b");
    private static final String TIER_VALUE =
            "(VERY[ _-]?SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|SPECIAL)";
    private static final Pattern CURRENT_TIER = Pattern.compile(
            "(?i)\\b(?:Current\\s+)?(?:Tier|Rarity)\\s*[:#-]\\s*" + TIER_VALUE + "\\b");
    private static final Pattern PREFIXED_TIER = Pattern.compile(
            "(?i)(?:^|\\[)" + TIER_VALUE + "(?:]|\\s+)\\s*Eagle(?:\\s+Pet)?\\b");
    private static final Pattern INLINE_EAGLE_TIER = Pattern.compile(
            "(?i)\\bEagle(?:\\s+Pet)?\\s*(?:[:#-]|\\[)\\s*" + TIER_VALUE + "\\b");
    private static final Pattern LOCKED = Pattern.compile(
            "(?i)\\b(?:LOCKED|NOT\\s+UNLOCKED|NOT\\s+OWNED)\\b|\\b(?:Click|Purchase).{0,20}\\bunlock\\b");
    private static final Pattern UNLOCKED = Pattern.compile(
            "(?i)\\b(?:UNLOCKED|OWNED|MAXED|MAXIMUM\\s+TIER)\\b|\\bClick.{0,20}\\bupgrade\\b");

    private SafariEagleParser() {
    }

    /** Parses one item/lore block from a physically opened Safari Essence Shop. */
    public static SafariEagleState safariEssenceShop(Iterable<String> boundedItemText) {
        List<String> lines = clean(boundedItemText);
        if (lines.stream().noneMatch(line -> EAGLE.matcher(line).find())) return SafariEagleState.EMPTY;
        String joined = String.join(" ", lines);
        SafariEagleState.UnlockStatus unlock = SafariEagleState.UnlockStatus.UNKNOWN;
        SafariEagleState.Presence presence = SafariEagleState.Presence.UNKNOWN;
        if (LOCKED.matcher(joined).find()) {
            unlock = SafariEagleState.UnlockStatus.LOCKED;
            presence = SafariEagleState.Presence.ABSENT;
        } else if (UNLOCKED.matcher(joined).find()) {
            unlock = SafariEagleState.UnlockStatus.UNLOCKED;
        }
        PetTier tier = explicitTier(lines);
        return new SafariEagleState(unlock, presence, tier,
                Set.of(SafariEagleState.Evidence.SAFARI_ESSENCE_SHOP));
    }

    /** Parses the current player's bounded Critter Safari Tab/widget block. */
    public static SafariEagleState safariWidget(Iterable<String> boundedWidgetText) {
        List<String> lines = clean(boundedWidgetText);
        List<String> eagleLines = lines.stream().filter(line -> EAGLE.matcher(line).find()).toList();
        if (eagleLines.isEmpty()) return SafariEagleState.EMPTY;
        return new SafariEagleState(SafariEagleState.UnlockStatus.UNLOCKED,
                SafariEagleState.Presence.PRESENT, explicitTier(eagleLines),
                Set.of(SafariEagleState.Evidence.SAFARI_WIDGET));
    }

    /**
     * Parses an entity label only after the caller has proven ownership. Merely
     * being near an entity is never sufficient evidence.
     */
    public static SafariEagleState ownerBoundEntity(String receivedName, boolean ownerBound) {
        String line = HuntingTextParser.plain(receivedName);
        if (!ownerBound || !EAGLE.matcher(line).find()) return SafariEagleState.EMPTY;
        return new SafariEagleState(SafariEagleState.UnlockStatus.UNLOCKED,
                SafariEagleState.Presence.PRESENT, explicitTier(List.of(line)),
                Set.of(SafariEagleState.Evidence.OWNER_BOUND_ENTITY));
    }

    private static PetTier explicitTier(List<String> lines) {
        for (String line : lines) {
            Matcher current = CURRENT_TIER.matcher(line);
            if (current.find()) return PetTier.fromWire(current.group(1));
            Matcher prefixed = PREFIXED_TIER.matcher(line);
            if (prefixed.find()) return PetTier.fromWire(prefixed.group(1));
            Matcher inline = INLINE_EAGLE_TIER.matcher(line);
            if (inline.find()) return PetTier.fromWire(inline.group(1));
        }
        return PetTier.UNKNOWN;
    }

    private static List<String> clean(Iterable<String> rawLines) {
        List<String> result = new ArrayList<>();
        if (rawLines == null) return result;
        for (String raw : rawLines) {
            String line = HuntingTextParser.plain(raw);
            if (!line.isBlank()) result.add(line);
        }
        return result;
    }
}
