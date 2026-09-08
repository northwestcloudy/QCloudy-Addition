package cloudy.autume.addition.dungeon.requirements;

/** One enabled rule's typed result. */
public record RequirementFinding(
        DungeonRequirement requirement,
        RequirementStatus status,
        DungeonRequirementCriterion criterion,
        DungeonRequirementEvidenceValue evidence) {

    public RequirementFinding {
        if (requirement == null) throw new IllegalArgumentException("Requirement is required");
        if (status == null) throw new IllegalArgumentException("Status is required");
        if (criterion == null) throw new IllegalArgumentException("Criterion is required");
        if (evidence == null) throw new IllegalArgumentException("Evidence is required");
    }

    public String unavailableReason() {
        return status == RequirementStatus.UNKNOWN ? evidence.unavailableReason() : "";
    }
}
