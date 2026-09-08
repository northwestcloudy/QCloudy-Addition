package cloudy.autume.addition.dungeon.requirements;

/**
 * Stable evaluation and presentation order for the nine independent rules.
 * Do not reorder existing constants; chat output relies on this order.
 */
public enum DungeonRequirement {
    MINIMUM_FLOOR_COMPLETIONS,
    DISALLOW_DUPLICATE_CLASS,
    MAXIMUM_FASTEST_COMPLETION,
    MINIMUM_AVERAGE_SECRETS,
    MINIMUM_MAGICAL_POWER,
    REQUIRE_WITHER_BLADE,
    REQUIRE_TERMINATOR,
    REQUIRE_GOLDEN_DRAGON,
    REQUIRE_ENDER_DRAGON
}
