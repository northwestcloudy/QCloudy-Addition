package cloudy.autume.addition.tracker;

import cloudy.autume.addition.hud.CompactNumbers;

import java.util.Locale;
import java.util.Set;

public final class PetLeveling {
    private static final int[] LEVEL_XP = {
            100,110,120,130,145,160,175,190,210,230,250,275,300,330,360,400,440,490,540,600,
            660,730,800,880,960,1050,1150,1260,1380,1510,1650,1800,1960,2130,2310,2500,2700,
            2920,3160,3420,3700,4000,4350,4750,5200,5700,6300,7000,7800,8700,9700,10800,12000,
            13300,14700,16200,17800,19500,21300,23200,25200,27400,29800,32400,35200,38200,41400,
            44800,48400,52200,56200,60400,64800,69400,74200,79200,84700,90700,97200,104200,111700,
            119700,128200,137200,146700,156700,167700,179700,192700,206700,221700,237700,254700,
            272700,291700,311700,333700,357700,383700,411700,441700,476700,516700,561700,611700,
            666700,726700,791700,861700,936700,1016700,1101700,1191700,1286700,1386700,1496700,
            1616700,1746700,1886700
    };
    private static final Set<String> LEVEL_200 = Set.of("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON");

    private PetLeveling() {
    }

    public static Progress progress(PetTracker.PetSnapshot pet) {
        int level = parseLevel(pet.level());
        int maxLevel = LEVEL_200.contains(normalize(pet.name())) ? 200 : 100;
        if (!pet.tier().hasKnownLevelingCurve()) return Progress.unknown(maxLevel);
        int offset = pet.tier().levelingOffset();
        double maximum = maximumXp(pet);

        double current;
        if (pet.maxLevel() || level >= maxLevel) {
            current = maximum;
        } else if (pet.nextXp().isEmpty() && !pet.currentXp().isEmpty()) {
            // Hypixel's "+... XP" Tab form is cumulative pet XP.
            current = CompactNumbers.parse(pet.currentXp());
        } else {
            current = completedXp(level, offset, maxLevel) + CompactNumbers.parse(pet.currentXp());
        }
        current = Math.clamp(current, 0.0, maximum);
        return new Progress(current, maximum, maximum == 0.0 ? 0.0 : current / maximum * 100.0, maxLevel);
    }

    public static int cosmeticLevel(PetTracker.PetSnapshot pet, double exactTotalExperience) {
        Progress progress = progress(pet);
        int receivedLevel = parseLevel(pet.level());
        if (!progress.known()) return receivedLevel;
        double overflow;
        if (exactTotalExperience > 0.0) {
            if (exactTotalExperience < progress.maximum()) return receivedLevel;
            overflow = exactTotalExperience - progress.maximum();
        } else {
            // An overflow line is only meaningful after Hypixel has marked the
            // pet as max level. Returning maxLevel for an ordinary leveling pet
            // incorrectly turns Ancient Golden Dragons into Lvl 200.
            if (!pet.maxLevel() && pet.overflowXp().isBlank()) return receivedLevel;
            overflow = CompactNumbers.parse(pet.overflowXp());
        }
        if (overflow < 1_886_700.0) return progress.maxLevel();
        return progress.maxLevel() + (int) Math.floor(overflow / 1_886_700.0);
    }

    public static double maximumXp(PetTracker.PetSnapshot pet) {
        if (!pet.tier().hasKnownLevelingCurve()) return 0.0;
        int maxLevel = LEVEL_200.contains(normalize(pet.name())) ? 200 : 100;
        double maximum = standardTotal(pet.tier().levelingOffset());
        if (maxLevel == 200) maximum += dragonTotal();
        return maximum;
    }

    public static boolean canInferMaximumXp(PetTracker.PetSnapshot pet) {
        return pet != null && pet.tier().hasKnownLevelingCurve();
    }

    static double completedXp(int level, int rarityOffset, int maxLevel) {
        int clamped = Math.clamp(level, 1, maxLevel);
        double total = 0.0;
        int standardTransitions = Math.min(clamped - 1, 99);
        for (int index = 0; index < standardTransitions; index++) total += LEVEL_XP[rarityOffset + index];
        if (maxLevel == 200 && clamped > 100) {
            int extraTransitions = clamped - 100;
            for (int index = 0; index < extraTransitions; index++) total += dragonStep(index);
        }
        return total;
    }

    private static double standardTotal(int offset) {
        double total = 0.0;
        for (int index = 0; index < 99; index++) total += LEVEL_XP[offset + index];
        return total;
    }

    private static double dragonTotal() {
        double total = 0.0;
        for (int index = 0; index < 100; index++) total += dragonStep(index);
        return total;
    }

    private static int dragonStep(int index) {
        if (index == 0) return 0;
        if (index == 1) return 5_555;
        return 1_886_700;
    }

    private static int parseLevel(String raw) {
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (RuntimeException ignored) {
            return 1;
        }
    }

    private static String normalize(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }

    public record Progress(double current, double maximum, double percentage, int maxLevel, boolean known) {
        public Progress(double current, double maximum, double percentage, int maxLevel) {
            this(current, maximum, percentage, maxLevel, true);
        }

        static Progress unknown(int maxLevel) {
            return new Progress(0.0, 0.0, 0.0, maxLevel, false);
        }
    }
}
