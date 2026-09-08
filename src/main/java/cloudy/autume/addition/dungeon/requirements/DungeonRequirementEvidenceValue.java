package cloudy.autume.addition.dungeon.requirements;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/** Typed, immutable inputs for one requirement finding. */
public sealed interface DungeonRequirementEvidenceValue permits
        DungeonRequirementEvidenceValue.LongValue,
        DungeonRequirementEvidenceValue.DecimalValue,
        DungeonRequirementEvidenceValue.PresenceEvidence,
        DungeonRequirementEvidenceValue.DuplicateClass {

    /** Empty for evidence that is conclusive; explanatory text for UNKNOWN. */
    String unavailableReason();

    record LongValue(Long value, String unavailableReason)
            implements DungeonRequirementEvidenceValue {
        public LongValue {
            if (value != null && value < 0) {
                throw new IllegalArgumentException("Evidence value must not be negative");
            }
            unavailableReason = reason(value == null, unavailableReason);
        }

        public static LongValue known(long value) {
            return new LongValue(value, "");
        }

        public static LongValue unknown(String reason) {
            return new LongValue(null, reason);
        }

        public boolean known() {
            return value != null;
        }
    }

    record DecimalValue(Double value, String unavailableReason)
            implements DungeonRequirementEvidenceValue {
        public DecimalValue {
            if (value != null && (!Double.isFinite(value) || value < 0)) {
                throw new IllegalArgumentException("Evidence value must be finite and not negative");
            }
            unavailableReason = reason(value == null, unavailableReason);
        }

        public static DecimalValue known(double value) {
            return new DecimalValue(value, "");
        }

        public static DecimalValue unknown(String reason) {
            return new DecimalValue(null, reason);
        }

        public boolean known() {
            return value != null;
        }
    }

    enum PresenceState {
        PRESENT,
        CONFIRMED_ABSENT,
        UNKNOWN
    }

    record PresenceEvidence(PresenceState state, String unavailableReason)
            implements DungeonRequirementEvidenceValue {
        public PresenceEvidence {
            state = state == null ? PresenceState.UNKNOWN : state;
            unavailableReason = reason(state == PresenceState.UNKNOWN, unavailableReason);
        }

        public static PresenceEvidence present() {
            return new PresenceEvidence(PresenceState.PRESENT, "");
        }

        public static PresenceEvidence confirmedAbsent() {
            return new PresenceEvidence(PresenceState.CONFIRMED_ABSENT, "");
        }

        public static PresenceEvidence unknown(String reason) {
            return new PresenceEvidence(PresenceState.UNKNOWN, reason);
        }
    }

    /** A member present before the newcomer, with null class when it is not known. */
    record PartyMemberClass(String playerName, DungeonClassKey dungeonClass) {
        public PartyMemberClass {
            playerName = safeName(playerName);
        }
    }

    /**
     * Live party-class evidence. A known duplicate is conclusive even when the
     * rest of the roster is incomplete. No duplicate is conclusive only when
     * rosterComplete is true and every member class is known.
     */
    record DuplicateClass(
            DungeonClassKey newcomerClass,
            List<PartyMemberClass> existingMembers,
            boolean rosterComplete,
            String unavailableReason) implements DungeonRequirementEvidenceValue {

        public DuplicateClass {
            existingMembers = existingMembers == null ? List.of() : List.copyOf(existingMembers);
            unavailableReason = reason(!conclusiveWithoutDuplicate(
                    newcomerClass, existingMembers, rosterComplete), unavailableReason);
        }

        public static DuplicateClass known(
                DungeonClassKey newcomerClass, List<PartyMemberClass> existingMembers) {
            if (newcomerClass == null) throw new IllegalArgumentException("Newcomer class is required");
            return new DuplicateClass(newcomerClass, existingMembers, true, "");
        }

        public static DuplicateClass unknown(
                DungeonClassKey newcomerClass,
                List<PartyMemberClass> observedMembers,
                String reason) {
            return new DuplicateClass(newcomerClass, observedMembers, false, reason);
        }

        /** Conflicting names in deterministic order, de-duplicated case-insensitively. */
        public List<String> conflictingPlayers() {
            if (newcomerClass == null) return List.of();
            LinkedHashMap<String, String> unique = new LinkedHashMap<>();
            for (PartyMemberClass member : existingMembers) {
                if (member != null && member.dungeonClass() == newcomerClass) {
                    unique.putIfAbsent(member.playerName().toLowerCase(Locale.ROOT), member.playerName());
                }
            }
            List<String> names = new ArrayList<>(unique.values());
            names.sort(Comparator.comparing((String name) -> name.toLowerCase(Locale.ROOT))
                    .thenComparing(Comparator.naturalOrder()));
            return List.copyOf(names);
        }

        public boolean authoritativeWithoutDuplicate() {
            return conclusiveWithoutDuplicate(newcomerClass, existingMembers, rosterComplete);
        }

        private static boolean conclusiveWithoutDuplicate(
                DungeonClassKey newcomerClass,
                List<PartyMemberClass> members,
                boolean rosterComplete) {
            if (newcomerClass == null || !rosterComplete || members == null) return false;
            for (PartyMemberClass member : members) {
                if (member == null || member.dungeonClass() == null) return false;
            }
            return true;
        }
    }

    private static String reason(boolean needed, String value) {
        if (!needed) return "";
        if (value == null) return "Evidence unavailable";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (normalized.isEmpty()) return "Evidence unavailable";
        return normalized.length() <= 192 ? normalized : normalized.substring(0, 192);
    }

    private static String safeName(String value) {
        if (value == null) return "";
        String normalized = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        return normalized.length() <= 16 ? normalized : normalized.substring(0, 16);
    }
}
