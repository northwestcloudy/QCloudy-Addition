package cloudy.autume.addition.party;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure command planner shared by Party Chat {@code !...} commands and local
 * {@code //...} commands. Returned payloads never include a leading slash.
 */
public final class PartyCommandEngine {
    public static final long WARP_COOLDOWN_NANOS = 5_000_000_000L;
    public static final long ALL_INVITE_COOLDOWN_NANOS = 2_000_000_000L;
    private static final long PARTY_MESSAGE_DEDUPE_NANOS = 500_000_000L;
    private static final List<String> FLOOR_NAMES = List.of(
            "", "ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN");

    private static final LinkedHashMap<String, Feature> ALIASES = aliases();

    private final PartyRosterTracker roster;
    private final EnumMap<Feature, Long> lastDispatchNanos = new EnumMap<>(Feature.class);
    private final Map<String, Long> recentPartyMessages = new LinkedHashMap<>();

    public PartyCommandEngine() {
        this(new PartyRosterTracker());
    }

    public PartyCommandEngine(PartyRosterTracker roster) {
        this.roster = roster == null ? new PartyRosterTracker() : roster;
    }

    /**
     * Handles one server chat line. Non-party text is offered to the roster
     * lifecycle observer and otherwise ignored.
     */
    public Result handlePartyChat(String raw, String localPlayer, boolean masterEnabled,
                                  Predicate<Feature> featureEnabled,
                                  Function<Feature, TriggerScope> scopeFor,
                                  BlockCoordinates coordinates, long nowNanos) {
        var parsed = PartyChatLine.parse(raw);
        if (parsed.isEmpty()) {
            roster.observeSystemMessage(raw);
            return Result.ignored();
        }

        PartyChatLine line = parsed.get();
        roster.observePartyChat(line);
        if (!masterEnabled) return Result.ignored();

        ParsedCommand command = parse(line.message(), "!");
        if (command == null) return Result.ignored();
        if (!enabled(featureEnabled, command.feature())) return Result.ignored();

        TriggerScope scope = scopeFor == null ? null : scopeFor.apply(command.feature());
        if (scope == null || !scope.allows(line.sender(), localPlayer)) return Result.ignored();
        if (duplicatePartyMessage(raw, nowNanos)) return Result.ignored();

        Result result = build(command, line.sender(), localPlayer, coordinates);
        return applyCooldown(result, nowNanos);
    }

    /** Handles exact user input beginning with two slashes. Unknown input is passed through. */
    public Result handleLocalDoubleSlash(String rawInput, String localPlayer, boolean masterEnabled,
                                         Predicate<Feature> featureEnabled,
                                         BlockCoordinates coordinates, long nowNanos) {
        ParsedCommand command = parse(rawInput, "//");
        if (command == null) return Result.ignored();
        if (!masterEnabled || !enabled(featureEnabled, command.feature())) {
            return Result.disabled(command.feature());
        }
        Result result = build(command, localPlayer, localPlayer, coordinates);
        return applyCooldown(result, nowNanos);
    }

    /** Updates the session roster from a non-chat English Hypixel party message. */
    public void observePartySystemMessage(String raw) {
        roster.observeSystemMessage(raw);
    }

    public PartyRosterTracker roster() {
        return roster;
    }

    public void resetSession() {
        roster.resetSession();
        lastDispatchNanos.clear();
        recentPartyMessages.clear();
    }

    /** Returns enabled alias suggestions, including the requested {@code !} or {@code //} prefix. */
    public List<String> suggestCommands(String partial, EntryPoint entryPoint, boolean masterEnabled,
                                        Predicate<Feature> featureEnabled) {
        if (!masterEnabled || entryPoint == null) return List.of();
        String prefix = entryPoint == EntryPoint.PARTY_CHAT ? "!" : "//";
        String input = partial == null ? "" : partial.trim();
        if (!input.startsWith(prefix)) return List.of();
        String fragment = input.substring(prefix.length()).toLowerCase(Locale.ROOT);
        if (fragment.indexOf(' ') >= 0 || fragment.indexOf('\t') >= 0) return List.of();

        List<String> result = new ArrayList<>();
        ALIASES.forEach((alias, feature) -> {
            if (alias.startsWith(fragment) && enabled(featureEnabled, feature)) {
                result.add(prefix + alias);
            }
        });
        return List.copyOf(result);
    }

    public List<String> suggestPlayers(String fragment, String localPlayer) {
        return roster.suggestions(fragment, localPlayer);
    }

    private Result build(ParsedCommand command, String defaultTarget, String localPlayer,
                         BlockCoordinates coordinates) {
        String verb = command.verb();
        List<String> arguments = command.arguments();
        return switch (command.feature()) {
            case WARP -> arguments.isEmpty()
                    ? Result.command(Feature.WARP, "party warp")
                    : Result.error(Feature.WARP, ErrorKind.INVALID_ARGUMENTS);
            case ALL_INVITE -> arguments.isEmpty()
                    ? Result.command(Feature.ALL_INVITE, "party settings allinvite")
                    : Result.error(Feature.ALL_INVITE, ErrorKind.INVALID_ARGUMENTS);
            case TRANSFER -> buildTransfer(verb, arguments, defaultTarget, localPlayer);
            case KICK -> buildTargetCommand(Feature.KICK, "party kick", arguments, localPlayer);
            case COORDINATES -> buildCoordinates(arguments, coordinates);
            case PROMOTE -> buildTargetCommand(Feature.PROMOTE, "party promote", arguments, localPlayer);
            case STREAM -> buildStream(arguments);
            case DUNGEON -> arguments.isEmpty()
                    ? Result.command(Feature.DUNGEON, "joininstance " + dungeonInstance(verb))
                    : Result.error(Feature.DUNGEON, ErrorKind.INVALID_ARGUMENTS);
            case KUUDRA -> arguments.isEmpty()
                    ? Result.command(Feature.KUUDRA, "joininstance " + kuudraInstance(verb))
                    : Result.error(Feature.KUUDRA, ErrorKind.INVALID_ARGUMENTS);
        };
    }

    private Result buildTransfer(String verb, List<String> arguments, String defaultTarget,
                                 String localPlayer) {
        if (verb.equals("ptme") && !arguments.isEmpty() || arguments.size() > 1) {
            return Result.error(Feature.TRANSFER, ErrorKind.INVALID_ARGUMENTS);
        }
        String target = arguments.isEmpty() ? defaultTarget : arguments.getFirst();
        return resolvedTarget(Feature.TRANSFER, "party transfer", target, localPlayer);
    }

    private Result buildTargetCommand(Feature feature, String payloadPrefix, List<String> arguments,
                                      String localPlayer) {
        if (arguments.size() != 1) return Result.error(feature, ErrorKind.INVALID_ARGUMENTS);
        return resolvedTarget(feature, payloadPrefix, arguments.getFirst(), localPlayer);
    }

    private Result resolvedTarget(Feature feature, String payloadPrefix, String target,
                                  String localPlayer) {
        PartyRosterTracker.Resolution resolution = roster.resolve(target, localPlayer);
        if (resolution.resolved()) return Result.command(feature, payloadPrefix + " " + resolution.name());
        if (resolution.kind() == PartyRosterTracker.ResolutionKind.AMBIGUOUS) {
            return Result.error(feature, ErrorKind.AMBIGUOUS_PLAYER, resolution.candidates());
        }
        return Result.error(feature, ErrorKind.INVALID_PLAYER);
    }

    private static Result buildCoordinates(List<String> arguments, BlockCoordinates coordinates) {
        if (!arguments.isEmpty()) return Result.error(Feature.COORDINATES, ErrorKind.INVALID_ARGUMENTS);
        if (coordinates == null) return Result.error(Feature.COORDINATES, ErrorKind.COORDINATES_UNAVAILABLE);
        return Result.command(Feature.COORDINATES,
                "pc x: " + coordinates.x() + ", y: " + coordinates.y() + ", z: " + coordinates.z());
    }

    private static Result buildStream(List<String> arguments) {
        if (arguments.isEmpty()) return Result.command(Feature.STREAM, "stream");
        if (arguments.size() != 1) return Result.error(Feature.STREAM, ErrorKind.INVALID_ARGUMENTS);
        String argument = arguments.getFirst();
        if (argument.matches("[0-9]+")) return Result.command(Feature.STREAM, "stream open " + argument);
        String normalized = argument.toLowerCase(Locale.ROOT);
        if (normalized.equals("c") || normalized.equals("close") || normalized.equals("off")) {
            return Result.command(Feature.STREAM, "stream close");
        }
        return Result.error(Feature.STREAM, ErrorKind.INVALID_ARGUMENTS);
    }

    private Result applyCooldown(Result result, long nowNanos) {
        if (result.status() != Status.COMMAND || result.feature() == null) return result;
        long duration = cooldownFor(result.feature());
        if (duration == 0L) return result;
        Long previous = lastDispatchNanos.get(result.feature());
        if (previous != null) {
            long elapsed = nowNanos - previous;
            if (elapsed >= 0L && elapsed < duration) {
                return Result.cooldown(result.feature(), duration - elapsed);
            }
        }
        lastDispatchNanos.put(result.feature(), nowNanos);
        return result;
    }

    private boolean duplicatePartyMessage(String raw, long nowNanos) {
        String key = PartyText.clean(raw);
        recentPartyMessages.entrySet().removeIf(entry -> {
            long elapsed = nowNanos - entry.getValue();
            return elapsed < 0L || elapsed >= PARTY_MESSAGE_DEDUPE_NANOS;
        });
        Long previous = recentPartyMessages.putIfAbsent(key, nowNanos);
        return previous != null && nowNanos - previous >= 0L
                && nowNanos - previous < PARTY_MESSAGE_DEDUPE_NANOS;
    }

    private static long cooldownFor(Feature feature) {
        return switch (feature) {
            case WARP -> WARP_COOLDOWN_NANOS;
            case ALL_INVITE -> ALL_INVITE_COOLDOWN_NANOS;
            default -> 0L;
        };
    }

    private static ParsedCommand parse(String raw, String requiredPrefix) {
        if (raw == null) return null;
        String input = raw.trim();
        if (!input.startsWith(requiredPrefix)) return null;
        String body = input.substring(requiredPrefix.length()).trim();
        if (body.isEmpty()) return null;
        String[] tokens = body.split("\\s+");
        String verb = tokens[0].toLowerCase(Locale.ROOT);
        Feature feature = ALIASES.get(verb);
        if (feature == null) return null;
        List<String> arguments = new ArrayList<>(Math.max(0, tokens.length - 1));
        for (int index = 1; index < tokens.length; index++) {
            arguments.add(tokens[index]);
        }
        return new ParsedCommand(feature, verb, List.copyOf(arguments));
    }

    private static String dungeonInstance(String verb) {
        if (verb.equals("fe") || verb.equals("f0")) return "CATACOMBS_ENTRANCE";
        if (verb.equals("me") || verb.equals("m0")) return "MASTER_CATACOMBS_ENTRANCE";
        int floor = verb.charAt(1) - '0';
        String prefix = verb.charAt(0) == 'm' ? "MASTER_CATACOMBS_FLOOR_" : "CATACOMBS_FLOOR_";
        return prefix + FLOOR_NAMES.get(floor);
    }

    private static String kuudraInstance(String verb) {
        return switch (verb) {
            case "t1" -> "KUUDRA_NORMAL";
            case "t2" -> "KUUDRA_HOT";
            case "t3" -> "KUUDRA_BURNING";
            case "t4" -> "KUUDRA_FIERY";
            case "t5" -> "KUUDRA_INFERNAL";
            default -> throw new IllegalArgumentException("Unknown Kuudra alias: " + verb);
        };
    }

    private static boolean enabled(Predicate<Feature> featureEnabled, Feature feature) {
        return featureEnabled != null && featureEnabled.test(feature);
    }

    private static LinkedHashMap<String, Feature> aliases() {
        LinkedHashMap<String, Feature> result = new LinkedHashMap<>();
        addAliases(result, Feature.WARP, "warp", "w");
        addAliases(result, Feature.ALL_INVITE, "allinvite", "all", "allinv");
        addAliases(result, Feature.TRANSFER, "pt", "ptme");
        addAliases(result, Feature.KICK, "k");
        addAliases(result, Feature.COORDINATES, "sc", "sendcoords", "c");
        addAliases(result, Feature.PROMOTE, "pp");
        addAliases(result, Feature.STREAM, "stream", "st", "s");
        addAliases(result, Feature.DUNGEON, "fe", "f0", "me", "m0");
        for (int floor = 1; floor <= 7; floor++) {
            addAliases(result, Feature.DUNGEON, "f" + floor, "m" + floor);
        }
        for (int tier = 1; tier <= 5; tier++) addAliases(result, Feature.KUUDRA, "t" + tier);
        return result;
    }

    private static void addAliases(LinkedHashMap<String, Feature> result, Feature feature,
                                   String... aliases) {
        for (String alias : aliases) result.put(alias, feature);
    }

    public enum Feature {
        WARP,
        ALL_INVITE,
        TRANSFER,
        KICK,
        COORDINATES,
        PROMOTE,
        STREAM,
        DUNGEON,
        KUUDRA
    }

    public enum TriggerScope {
        SELF_ONLY,
        OTHERS_ONLY,
        EVERYONE;

        public boolean allows(String sender, String localPlayer) {
            if (this == EVERYONE) return true;
            if (!PartyRosterTracker.validUsername(sender)
                    || !PartyRosterTracker.validUsername(localPlayer)) return false;
            boolean self = sender.equalsIgnoreCase(localPlayer);
            return this == SELF_ONLY ? self : !self;
        }
    }

    public enum EntryPoint {
        PARTY_CHAT,
        LOCAL_DOUBLE_SLASH
    }

    public enum Status {
        IGNORED,
        DISABLED,
        COMMAND,
        ERROR,
        COOLDOWN
    }

    public enum ErrorKind {
        NONE,
        INVALID_ARGUMENTS,
        INVALID_PLAYER,
        AMBIGUOUS_PLAYER,
        COORDINATES_UNAVAILABLE
    }

    public record BlockCoordinates(int x, int y, int z) {
    }

    public record Result(Status status, Feature feature, String payload, ErrorKind error,
                         List<String> candidates, long retryAfterNanos) {
        public Result {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }

        public boolean shouldCancelInput() {
            return status != Status.IGNORED;
        }

        private static Result ignored() {
            return new Result(Status.IGNORED, null, null, ErrorKind.NONE, List.of(), 0L);
        }

        private static Result disabled(Feature feature) {
            return new Result(Status.DISABLED, feature, null, ErrorKind.NONE, List.of(), 0L);
        }

        private static Result command(Feature feature, String payload) {
            return new Result(Status.COMMAND, feature, payload, ErrorKind.NONE, List.of(), 0L);
        }

        private static Result error(Feature feature, ErrorKind error) {
            return error(feature, error, List.of());
        }

        private static Result error(Feature feature, ErrorKind error, List<String> candidates) {
            return new Result(Status.ERROR, feature, null, error, candidates, 0L);
        }

        private static Result cooldown(Feature feature, long retryAfterNanos) {
            return new Result(Status.COOLDOWN, feature, null, ErrorKind.NONE, List.of(), retryAfterNanos);
        }
    }

    private record ParsedCommand(Feature feature, String verb, List<String> arguments) {
    }
}
