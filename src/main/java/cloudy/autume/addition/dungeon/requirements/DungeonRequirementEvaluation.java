package cloudy.autume.addition.dungeon.requirements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Immutable combined result with stable full, failure and unknown projections. */
public final class DungeonRequirementEvaluation {
    private final RequirementStatus status;
    private final List<RequirementFinding> findings;
    private final List<RequirementFinding> failures;
    private final List<RequirementFinding> unknowns;

    DungeonRequirementEvaluation(List<RequirementFinding> source) {
        List<RequirementFinding> ordered = new ArrayList<>(source == null ? List.of() : source);
        ordered.sort(Comparator.comparingInt(value -> value.requirement().ordinal()));
        EnumSet<DungeonRequirement> seen = EnumSet.noneOf(DungeonRequirement.class);
        for (RequirementFinding finding : ordered) {
            if (finding == null || !seen.add(finding.requirement())) {
                throw new IllegalArgumentException("Findings must contain unique requirement keys");
            }
        }
        findings = List.copyOf(ordered);
        failures = filter(RequirementStatus.FAIL);
        unknowns = filter(RequirementStatus.UNKNOWN);
        status = !failures.isEmpty() ? RequirementStatus.FAIL
                : !unknowns.isEmpty() ? RequirementStatus.UNKNOWN
                : RequirementStatus.PASS;
    }

    public RequirementStatus status() {
        return status;
    }

    public List<RequirementFinding> findings() {
        return findings;
    }

    public List<RequirementFinding> failures() {
        return failures;
    }

    public List<RequirementFinding> unknowns() {
        return unknowns;
    }

    public boolean passed() {
        return status == RequirementStatus.PASS;
    }

    private List<RequirementFinding> filter(RequirementStatus expected) {
        return findings.stream().filter(value -> value.status() == expected).toList();
    }
}
