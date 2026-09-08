package cloudy.autume.addition.dungeon.requirements;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot of the nine settings for one floor. */
public record DungeonRequirementPolicy(
        DungeonFloorKey floor,
        LongRule minimumFloorCompletions,
        boolean duplicateClassDisallowed,
        LongRule maximumFastestCompletionMs,
        DecimalRule minimumAverageSecrets,
        LongRule minimumMagicalPower,
        boolean witherBladeRequired,
        boolean terminatorRequired,
        boolean goldenDragonRequired,
        boolean enderDragonRequired) {

    public DungeonRequirementPolicy {
        if (floor == null) throw new IllegalArgumentException("Floor is required");
        minimumFloorCompletions = safe(minimumFloorCompletions);
        maximumFastestCompletionMs = safe(maximumFastestCompletionMs);
        minimumAverageSecrets = safe(minimumAverageSecrets);
        minimumMagicalPower = safe(minimumMagicalPower);
    }

    public static DungeonRequirementPolicy allDisabled(DungeonFloorKey floor) {
        return new DungeonRequirementPolicy(floor,
                LongRule.disabled(0), false,
                LongRule.disabled(0), DecimalRule.disabled(0),
                LongRule.disabled(0), false, false, false, false);
    }

    public boolean hasEnabledRules() {
        return !enabledRequirements().isEmpty();
    }

    /** Returns enabled keys in the canonical nine-rule order. */
    public List<DungeonRequirement> enabledRequirements() {
        List<DungeonRequirement> enabled = new ArrayList<>(DungeonRequirement.values().length);
        for (DungeonRequirement requirement : DungeonRequirement.values()) {
            if (enabled(requirement)) enabled.add(requirement);
        }
        return Collections.unmodifiableList(enabled);
    }

    public boolean enabled(DungeonRequirement requirement) {
        if (requirement == null) return false;
        return switch (requirement) {
            case MINIMUM_FLOOR_COMPLETIONS -> minimumFloorCompletions.enabled();
            case DISALLOW_DUPLICATE_CLASS -> duplicateClassDisallowed;
            case MAXIMUM_FASTEST_COMPLETION -> maximumFastestCompletionMs.enabled();
            case MINIMUM_AVERAGE_SECRETS -> minimumAverageSecrets.enabled();
            case MINIMUM_MAGICAL_POWER -> minimumMagicalPower.enabled();
            case REQUIRE_WITHER_BLADE -> witherBladeRequired;
            case REQUIRE_TERMINATOR -> terminatorRequired;
            case REQUIRE_GOLDEN_DRAGON -> goldenDragonRequired;
            case REQUIRE_ENDER_DRAGON -> enderDragonRequired;
        };
    }

    private static LongRule safe(LongRule value) {
        return value == null ? LongRule.disabled(0) : value;
    }

    private static DecimalRule safe(DecimalRule value) {
        return value == null ? DecimalRule.disabled(0) : value;
    }

    public record LongRule(boolean enabled, long value) {
        public LongRule {
            if (value < 0) throw new IllegalArgumentException("Rule value must not be negative");
        }

        public static LongRule enabled(long value) {
            return new LongRule(true, value);
        }

        public static LongRule disabled(long retainedValue) {
            return new LongRule(false, retainedValue);
        }
    }

    public record DecimalRule(boolean enabled, double value) {
        public DecimalRule {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Rule value must be finite and not negative");
            }
        }

        public static DecimalRule enabled(double value) {
            return new DecimalRule(true, value);
        }

        public static DecimalRule disabled(double retainedValue) {
            return new DecimalRule(false, retainedValue);
        }
    }
}
