package cloudy.autume.addition.dungeon.requirements;

import java.util.ArrayList;
import java.util.List;

import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementCriterion.Comparison.AT_LEAST;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementCriterion.Comparison.AT_MOST;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementCriterion.Toggle.DISALLOW_DUPLICATE_CLASS;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementCriterion.Toggle.REQUIRE_PRESENT;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DecimalValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.LongValue;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceEvidence;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceState.CONFIRMED_ABSENT;
import static cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PresenceState.PRESENT;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.FAIL;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.PASS;
import static cloudy.autume.addition.dungeon.requirements.RequirementStatus.UNKNOWN;

/** Pure, side-effect-free evaluator for one frozen per-floor policy and evidence snapshot. */
public final class DungeonRequirementEvaluator {
    private DungeonRequirementEvaluator() { }

    public static DungeonRequirementEvaluation evaluate(
            DungeonRequirementPolicy policy,
            DungeonRequirementEvidence evidence) {
        if (policy == null) throw new IllegalArgumentException("Policy is required");
        if (evidence == null) throw new IllegalArgumentException("Evidence is required");
        if (policy.floor() != evidence.floor()) {
            throw new IllegalArgumentException("Policy and evidence floors do not match");
        }

        List<RequirementFinding> findings = new ArrayList<>(DungeonRequirement.values().length);
        for (DungeonRequirement requirement : DungeonRequirement.values()) {
            if (!policy.enabled(requirement)) continue;
            findings.add(evaluate(requirement, policy, evidence));
        }
        return new DungeonRequirementEvaluation(findings);
    }

    private static RequirementFinding evaluate(
            DungeonRequirement requirement,
            DungeonRequirementPolicy policy,
            DungeonRequirementEvidence evidence) {
        return switch (requirement) {
            case MINIMUM_FLOOR_COMPLETIONS -> minimumLong(requirement,
                    policy.minimumFloorCompletions().value(), evidence.floorCompletions());
            case DISALLOW_DUPLICATE_CLASS -> duplicateClass(evidence.duplicateClass());
            case MAXIMUM_FASTEST_COMPLETION -> maximumLong(requirement,
                    policy.maximumFastestCompletionMs().value(), evidence.fastestCompletionMs());
            case MINIMUM_AVERAGE_SECRETS -> minimumDecimal(requirement,
                    policy.minimumAverageSecrets().value(), evidence.averageSecrets());
            case MINIMUM_MAGICAL_POWER -> minimumLong(requirement,
                    policy.minimumMagicalPower().value(), evidence.magicalPower());
            case REQUIRE_WITHER_BLADE -> presence(requirement, evidence.witherBlade());
            case REQUIRE_TERMINATOR -> presence(requirement, evidence.terminator());
            case REQUIRE_GOLDEN_DRAGON -> presence(requirement, evidence.goldenDragon());
            case REQUIRE_ENDER_DRAGON -> presence(requirement, evidence.enderDragon());
        };
    }

    private static RequirementFinding minimumLong(
            DungeonRequirement requirement, long required, LongValue evidence) {
        RequirementStatus status = !evidence.known() ? UNKNOWN
                : evidence.value() >= required ? PASS : FAIL;
        return new RequirementFinding(requirement, status,
                new DungeonRequirementCriterion.LongThreshold(required, AT_LEAST), evidence);
    }

    private static RequirementFinding maximumLong(
            DungeonRequirement requirement, long required, LongValue evidence) {
        RequirementStatus status = !evidence.known() ? UNKNOWN
                : evidence.value() <= required ? PASS : FAIL;
        return new RequirementFinding(requirement, status,
                new DungeonRequirementCriterion.LongThreshold(required, AT_MOST), evidence);
    }

    private static RequirementFinding minimumDecimal(
            DungeonRequirement requirement, double required, DecimalValue evidence) {
        RequirementStatus status = !evidence.known() ? UNKNOWN
                : Double.compare(evidence.value(), required) >= 0 ? PASS : FAIL;
        return new RequirementFinding(requirement, status,
                new DungeonRequirementCriterion.DecimalThreshold(required, AT_LEAST), evidence);
    }

    private static RequirementFinding presence(
            DungeonRequirement requirement, PresenceEvidence evidence) {
        RequirementStatus status = evidence.state() == PRESENT ? PASS
                : evidence.state() == CONFIRMED_ABSENT ? FAIL : UNKNOWN;
        return new RequirementFinding(requirement, status, REQUIRE_PRESENT, evidence);
    }

    private static RequirementFinding duplicateClass(DuplicateClass evidence) {
        RequirementStatus status = !evidence.conflictingPlayers().isEmpty() ? FAIL
                : evidence.authoritativeWithoutDuplicate() ? PASS : UNKNOWN;
        return new RequirementFinding(DungeonRequirement.DISALLOW_DUPLICATE_CLASS,
                status, DISALLOW_DUPLICATE_CLASS, evidence);
    }
}
