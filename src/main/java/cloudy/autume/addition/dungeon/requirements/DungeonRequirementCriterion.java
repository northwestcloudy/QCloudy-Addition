package cloudy.autume.addition.dungeon.requirements;

/** Typed criterion retained on a finding so rendering never has to re-evaluate it. */
public sealed interface DungeonRequirementCriterion permits
        DungeonRequirementCriterion.LongThreshold,
        DungeonRequirementCriterion.DecimalThreshold,
        DungeonRequirementCriterion.Toggle {

    enum Comparison {
        AT_LEAST,
        AT_MOST
    }

    record LongThreshold(long value, Comparison comparison)
            implements DungeonRequirementCriterion {
        public LongThreshold {
            if (value < 0) throw new IllegalArgumentException("Threshold must not be negative");
            if (comparison == null) throw new IllegalArgumentException("Comparison is required");
        }
    }

    record DecimalThreshold(double value, Comparison comparison)
            implements DungeonRequirementCriterion {
        public DecimalThreshold {
            if (!Double.isFinite(value) || value < 0) {
                throw new IllegalArgumentException("Threshold must be finite and not negative");
            }
            if (comparison == null) throw new IllegalArgumentException("Comparison is required");
        }
    }

    enum Toggle implements DungeonRequirementCriterion {
        DISALLOW_DUPLICATE_CLASS,
        REQUIRE_PRESENT
    }
}
