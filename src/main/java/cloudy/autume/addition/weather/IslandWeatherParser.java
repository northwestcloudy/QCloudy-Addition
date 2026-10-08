package cloudy.autume.addition.weather;

import cloudy.autume.addition.tracker.IslandArea;
import net.minecraft.ChatFormatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative parser for explicit weather text already received by the
 * client. The documented 20-minute cadence is never used as evidence.
 */
public final class IslandWeatherParser {
    private static final Pattern LABEL_ONLY = Pattern.compile(
            "(?i)^(?:current\\s+|island\\s+)?weather\\s*[:：]\\s*$");
    private static final Pattern LABELED = Pattern.compile(
            "(?i)^(?:(?:mild|extreme)\\s+)?(?:current\\s+|island\\s+)?"
                    + "weather(?:\\s+active)?\\s*[:：-]\\s*(.+)$");
    private static final Pattern IS_NOW = Pattern.compile(
            "(?i)^(?:the\\s+)?weather\\s+(?:is|has\\s+changed\\s+to|changed\\s+to)"
                    + "\\s+(.+?)[.!]?$" );
    private static final Pattern BECAME_ACTIVE = Pattern.compile(
            "(?i)^(.+?)(?:\\s+weather)?\\s+(?:has\\s+begun|is\\s+now\\s+active)[.!]?$" );
    private static final Pattern CLEAR = Pattern.compile(
            "(?i)^(?:(?:current|island)\\s+)?weather\\s*[:：-]\\s*"
                    + "(?:none|clear|inactive|no active weather)[.!]?$|"
                    + "^(?:the\\s+)?weather\\s+(?:has\\s+)?ended[.!]?$|"
                    + "^weather\\s+is\\s+no\\s+longer\\s+active[.!]?$" );
    private static final Pattern TIMER_MARKER = Pattern.compile(
            "(?i)\\b(?:ends?\\s+in|remaining|time\\s+left)\\s*[:：-]?\\s*"
                    + "((?:[0-9]+\\s*(?:d(?:ays?)?|h(?:ours?|rs?)?|m(?:in(?:ute)?s?)?|s(?:ec(?:ond)?s?)?)\\s*)+"
                    + "|[0-9]{1,3}:[0-9]{2}(?::[0-9]{2})?)\\b" );
    private static final Pattern DURATION_PART = Pattern.compile(
            "(?i)([0-9]+)\\s*(d(?:ays?)?|h(?:ours?|rs?)?|m(?:in(?:ute)?s?)?|s(?:ec(?:ond)?s?)?)\\b" );
    private static final Pattern CLOCK = Pattern.compile(
            "^([0-9]{1,3}):([0-9]{2})(?::([0-9]{2}))?$" );
    private static final Pattern SEVERITY = Pattern.compile(
            "(?i)^(?:mild|extreme)\\s+|\\s*\\((?:mild|extreme)\\)\\s*$" );

    private IslandWeatherParser() {
    }

    public static ParseResult parse(IslandArea island,
                                    WeatherSnapshot.EvidenceSource source,
                                    Iterable<String> receivedTexts) {
        if (island == null || !island.isWeatherIsland() || source == null
                || receivedTexts == null) return ParseResult.unrecognized();

        List<String> lines = flatten(receivedTexts);
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (CLEAR.matcher(line).matches()) return ParseResult.clear();

            if (LABEL_ONLY.matcher(line).matches()) {
                if (index + 1 >= lines.size()) continue;
                String candidate = lines.get(index + 1);
                long remaining = explicitDuration(candidate);
                if (remaining < 0L && index + 2 < lines.size()) {
                    remaining = explicitDuration(lines.get(index + 2));
                }
                Optional<WeatherSnapshot> snapshot = active(
                        island, source, candidate, remaining, line + " " + candidate);
                if (snapshot.isPresent()) return ParseResult.active(snapshot.orElseThrow());
                continue;
            }

            String candidate = candidate(LABELED, line);
            if (candidate == null) candidate = candidate(IS_NOW, line);
            if (candidate == null) candidate = candidate(BECAME_ACTIVE, line);
            if (candidate == null) continue;

            Optional<WeatherSnapshot> snapshot = active(
                    island, source, candidate, explicitDuration(line), line);
            if (snapshot.isPresent()) return ParseResult.active(snapshot.orElseThrow());
        }
        return ParseResult.unrecognized();
    }

    private static Optional<WeatherSnapshot> active(IslandArea island,
                                                     WeatherSnapshot.EvidenceSource source,
                                                     String rawCandidate,
                                                     long remainingSeconds,
                                                     String receivedText) {
        String weatherName = weatherName(rawCandidate);
        IslandWeatherCatalog catalog = IslandWeatherCatalog.instance();
        Optional<IslandWeatherCatalog.Weather> weather = catalog.find(island, weatherName);
        if (weather.isEmpty()) {
            // In particular, "Weather: Breeze" in a Mineshaft or an extreme
            // weather from another island must not leak across island scope.
            return Optional.empty();
        }
        return Optional.of(new WeatherSnapshot(island, weather.orElseThrow(),
                remainingSeconds, source, receivedText));
    }

    private static String candidate(Pattern pattern, String line) {
        Matcher matcher = pattern.matcher(line);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String weatherName(String raw) {
        String value = plain(raw);
        Matcher timer = TIMER_MARKER.matcher(value);
        if (timer.find()) value = value.substring(0, timer.start()).trim();
        value = SEVERITY.matcher(value).replaceAll("").trim();
        value = value.replaceFirst("(?i)\\s+weather$", "").trim();
        return value.replaceFirst("[.!;,\\-–—]+$", "").trim();
    }

    private static long explicitDuration(String line) {
        Matcher marker = TIMER_MARKER.matcher(line);
        if (!marker.find()) return -1L;
        String value = marker.group(1).trim();
        Matcher clock = CLOCK.matcher(value);
        if (clock.matches()) {
            long first = Long.parseLong(clock.group(1));
            long second = Long.parseLong(clock.group(2));
            String third = clock.group(3);
            if (second >= 60L || third != null && Long.parseLong(third) >= 60L) return -1L;
            return third == null ? first * 60L + second
                    : first * 3_600L + second * 60L + Long.parseLong(third);
        }

        long seconds = 0L;
        boolean found = false;
        Matcher part = DURATION_PART.matcher(value);
        while (part.find()) {
            found = true;
            long amount;
            try {
                amount = Long.parseLong(part.group(1));
            } catch (NumberFormatException ignored) {
                return -1L;
            }
            String unit = part.group(2).toLowerCase(Locale.ROOT);
            long multiplier = unit.startsWith("d") ? 86_400L
                    : unit.startsWith("h") ? 3_600L
                    : unit.startsWith("m") ? 60L : 1L;
            if (amount > Long.MAX_VALUE / multiplier
                    || seconds > Long.MAX_VALUE - amount * multiplier) return -1L;
            seconds += amount * multiplier;
        }
        return found ? seconds : -1L;
    }

    private static List<String> flatten(Iterable<String> receivedTexts) {
        List<String> result = new ArrayList<>();
        for (String raw : receivedTexts) {
            if (raw == null) continue;
            for (String rawLine : raw.split("\\R")) {
                String line = plain(rawLine);
                if (!line.isBlank()) result.add(line);
            }
        }
        return List.copyOf(result);
    }

    private static String plain(String value) {
        String stripped = ChatFormatting.stripFormatting(value == null ? "" : value);
        return stripped == null ? "" : stripped.replace('\u00a0', ' ').trim();
    }

    public record ParseResult(boolean recognized, WeatherSnapshot snapshot) {
        public ParseResult {
            if (!recognized && snapshot != null) {
                throw new IllegalArgumentException("Unrecognized weather cannot contain a snapshot");
            }
        }

        public static ParseResult active(WeatherSnapshot snapshot) {
            return new ParseResult(true, java.util.Objects.requireNonNull(snapshot));
        }

        public static ParseResult clear() {
            return new ParseResult(true, null);
        }

        public static ParseResult unrecognized() {
            return new ParseResult(false, null);
        }

        public boolean active() {
            return snapshot != null;
        }

        public boolean cleared() {
            return recognized && snapshot == null;
        }
    }
}
