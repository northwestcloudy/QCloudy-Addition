package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.compat.MinecraftClientCompat;
import cloudy.autume.addition.dungeon.requirements.DungeonClassKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks only the local player's own advertised Dungeon Party Finder floor. */
final class DungeonPartyFinderFloorTracker {
    private static final long GROUP_BUILDER_CONFIRM_MAX_AGE_NANOS =
            java.time.Duration.ofSeconds(15).toNanos();
    private static final Pattern FLOOR = Pattern.compile(
            "(?i)^Floor:?\\s*(?:Floor\\s*)?(Entrance|VII|VI|IV|V|III|II|I|[1-7])$");
    private static final Pattern CURRENTLY_SELECTED = Pattern.compile(
            "(?i)^Currently Selected:\\s*(.+)$");
    private static final Pattern MEMBER_CLASS = Pattern.compile(
            "(?i)^(?:\\[[^]]+]\\s*)?([A-Za-z0-9_]{3,16}):\\s*"
                    + "(Archer|Berserk|Healer|Mage|Tank)\\s*\\([^)]*\\d+[^)]*\\)$");
    private static final Pattern SELECTED_CLASS = Pattern.compile(
            "(?i)^Currently Selected:\\s*(Archer|Berserk|Healer|Mage|Tank)$");
    private static final Pattern YOU_JOINED_PARTY = Pattern.compile(
            "^You have joined .+['’]s? party!$");
    private static final Pattern PARTY_DISBANDED_BY_PLAYER = Pattern.compile(
            "^.+ has disbanded the party!$");
    private static DungeonFloor currentFloor;
    private static long generation;
    private static final LinkedHashMap<String, PartyMemberClass> memberClasses = new LinkedHashMap<>();
    private static final LinkedHashSet<String> activeMembers = new LinkedHashSet<>();
    private static final LinkedHashSet<String> pendingPartyFinderMembers = new LinkedHashSet<>();
    private static boolean trustedBaselineCaptured;
    private static boolean awaitingQueuedListing;
    private static GuiRosterSnapshot authoritativeGuiRoster;
    private static DungeonFloor confirmedGroupBuilderFloor;
    private static long confirmedGroupBuilderAtNanos;

    private DungeonPartyFinderFloorTracker() { }

    static void update(Minecraft client) {
        if (client == null || client.player == null
                || !(MinecraftClientCompat.screen(client) instanceof AbstractContainerScreen<?> screen)) return;
        observeOwnListingMenu(screen.getTitle().getString(), menuEntries(client, screen),
                client.getUser().getName(), System.nanoTime());
    }

    static List<MenuEntry> menuEntries(Minecraft client, AbstractContainerScreen<?> screen) {
        if (client == null || client.player == null || screen == null) return List.of();
        Item.TooltipContext context = client.level == null
                ? Item.TooltipContext.EMPTY : Item.TooltipContext.of(client.level);
        List<MenuEntry> entries = new ArrayList<>();
        for (var slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
            List<String> tooltip = new ArrayList<>();
            try {
                slot.getItem().getTooltipLines(context, client.player, TooltipFlag.NORMAL)
                        .forEach(line -> tooltip.add(line.getString()));
            } catch (RuntimeException ignored) {
                continue;
            }
            entries.add(new MenuEntry(slot.index, slot.getItem().getHoverName().getString(), tooltip,
                    slot.getItem().is(Items.PLAYER_HEAD), slot.getItem().is(Items.BOOKSHELF),
                    slot.getItem().is(Items.EMERALD_BLOCK)));
        }
        return List.copyOf(entries);
    }

    static DungeonFloor currentFloor() {
        return currentFloor;
    }

    static ListingContext currentListing() {
        return currentFloor == null ? null : new ListingContext(currentFloor, generation);
    }

    /**
     * Returns only roster classes observed directly from the local player's own
     * Party Finder listing. Chat-derived class cache entries are never copied
     * into this snapshot. The requested newcomer is excluded case-insensitively.
     */
    static Optional<GuiRosterSnapshot> authoritativeGuiRoster(String newcomer) {
        GuiRosterSnapshot observed = authoritativeGuiRoster().orElse(null);
        if (observed == null) return Optional.empty();
        String excluded = key(newcomer);
        List<PartyMemberClass> existingMembers = observed.members().stream()
                .filter(member -> !key(member.playerName()).equals(excluded))
                .toList();
        return Optional.of(new GuiRosterSnapshot(observed.floor(),
                observed.listingGeneration(), observed.observedAtNanos(),
                observed.complete(), existingMembers));
    }

    /** Full current GUI roster, including the newly admitted target. */
    static Optional<GuiRosterSnapshot> authoritativeGuiRoster() {
        GuiRosterSnapshot observed = authoritativeGuiRoster;
        if (observed == null || currentFloor == null
                || observed.listingGeneration() != generation
                || !observed.floor().equals(currentFloor)) {
            return Optional.empty();
        }
        return Optional.of(observed);
    }

    /**
     * Package-private observation seam for deterministic tests. Production
     * calls this only after reading the currently open container and supplies
     * {@link System#nanoTime()}.
     */
    static boolean observeOwnListingMenu(String title, List<MenuEntry> entries,
                                         String localPlayer, long observedAtNanos) {
        Optional<DungeonFloor> observedFloor = floorFromPartyFinder(title, entries, localPlayer);
        if (observedFloor.isEmpty()) return false;

        DungeonFloor floor = observedFloor.orElseThrow();
        if (awaitingQueuedListing) {
            if (currentFloor != null && !currentFloor.equals(floor)) {
                // The retained pre-queue floor was disproved. Treat the GUI
                // observation as another listing boundary and do not carry a
                // pending newcomer across the mismatch.
                generation++;
                clearRosterState();
            } else {
                // A Party Finder admission can arrive after the queue line but
                // before this confirming GUI read. Keep that member pending.
                clearTrustedRosterPreservingPending();
            }
            awaitingQueuedListing = false;
        } else if (currentFloor == null) {
            generation++;
            // A real Party Finder admission message can arrive before the
            // first readable own-listing snapshot. Preserve that pending
            // newcomer while freezing the trusted pre-queue baseline; otherwise
            // the first GUI read could silently promote the newcomer to trusted.
            clearTrustedRosterPreservingPending();
        } else if (!currentFloor.equals(floor)) {
            generation++;
            clearRosterState();
        }
        currentFloor = floor;

        ParsedGuiRoster parsed = parseGuiRoster(entries, localPlayer, floor);
        Optional<DungeonClassKey> selected = selectedClass(entries);
        PartyMemberClass localRosterClass = parsed.membersByName().get(key(localPlayer));
        boolean selectedClassConsistent = selected.isEmpty()
                || localRosterClass != null
                && localRosterClass.dungeonClass() == selected.orElseThrow();
        boolean parsedAuthoritative = parsed.complete() && selectedClassConsistent;
        if (parsedAuthoritative) {
            if (trustedBaselineCaptured) {
                for (String active : List.copyOf(activeMembers)) {
                    if (pendingPartyFinderMembers.contains(active)
                            || parsed.membersByName().containsKey(active)) continue;
                    PartyMemberClass previous = memberClasses.get(active);
                    if (previous != null) {
                        memberClasses.put(active,
                                new PartyMemberClass(previous.playerName(), null));
                    }
                }
            }
            for (Map.Entry<String, PartyMemberClass> entry : parsed.membersByName().entrySet()) {
                memberClasses.put(entry.getKey(), entry.getValue());
                // The first complete own-listing read freezes every member
                // already present as trusted. Later names without a matching
                // Party Finder admission are ordinary/manual trusted joins.
                if (!pendingPartyFinderMembers.contains(entry.getKey())) {
                    activeMembers.add(entry.getKey());
                }
            }
            trustedBaselineCaptured = true;
        } else if (trustedBaselineCaptured && selected.isPresent()
                && localRosterClass != null && activeMembers.contains(key(localPlayer))) {
            memberClasses.put(key(localPlayer), new PartyMemberClass(localPlayer, null));
        }
        if (parsedAuthoritative) selected.ifPresent(value -> {
            putClass(localPlayer, value);
            if (trustedBaselineCaptured) activeMembers.add(key(localPlayer));
        });

        authoritativeGuiRoster = new GuiRosterSnapshot(floor, generation,
                observedAtNanos, parsed.complete() && selectedClassConsistent,
                List.copyOf(parsed.membersByName().values()));
        return true;
    }

    /** Captures the members that existed before this admission. */
    static List<PartyMemberClass> existingClasses(String newcomer) {
        String excluded = key(newcomer);
        return activeMembers.stream()
                .filter(member -> !member.equals(excluded))
                .map(memberClasses::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** Current class records for exactly the frozen pre-join member names. */
    static List<PartyMemberClass> currentClassesFor(List<PartyMemberClass> frozenMembers) {
        if (frozenMembers == null || frozenMembers.isEmpty()) return List.of();
        List<PartyMemberClass> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (PartyMemberClass frozen : frozenMembers) {
            if (frozen == null) continue;
            String member = key(frozen.playerName());
            if (!activeMembers.contains(member) || !seen.add(member)) continue;
            PartyMemberClass current = memberClasses.get(member);
            result.add(current == null
                    ? new PartyMemberClass(frozen.playerName(), null) : current);
        }
        return List.copyOf(result);
    }

    static void observeJoin(DungeonJoinParser.DungeonJoinEvent event) {
        if (event == null) return;
        String member = key(event.playerName());
        // An exact Party Finder line starts a new admission membership, even
        // if a missed departure left an older trusted entry behind.
        activeMembers.remove(member);
        pendingPartyFinderMembers.add(member);
        if (event.dungeonClass() != null) putClass(event.playerName(), event.dungeonClass());
        // A roster mutation makes the last GUI observation stale. The chat
        // line itself must never become authoritative GUI proof.
        authoritativeGuiRoster = null;
    }

    static boolean observeSystemMessage(String raw) {
        return observeSystemMessage(raw, System.nanoTime());
    }

    static boolean observeSystemMessage(String raw, long observedAtNanos) {
        String original = clean(raw);
        String text = original.toLowerCase(Locale.ROOT);
        if (DungeonJoinParser.partyFinderQueued(original)) {
            promoteFreshGroupBuilderFloor(observedAtNanos);
            beginQueuedListing();
            return false;
        }
        if (text.equals("you left the party.")
                || text.contains("the party was disbanded")
                || text.contains("you have been kicked from the party")
                || text.contains("you removed your group from the party finder")
                || text.contains("you are not currently in a party")
                || text.equals("you are not in a party.")
                || YOU_JOINED_PARTY.matcher(original).matches()
                || PARTY_DISBANDED_BY_PLAYER.matcher(original).matches()) {
            clearListing();
            return true;
        }
        DungeonJoinParser.ordinaryPartyJoin(original)
                .ifPresent(DungeonPartyFinderFloorTracker::observeTrustedJoin);
        DungeonJoinParser.departure(original).ifPresent(DungeonPartyFinderFloorTracker::removeMember);
        return false;
    }

    /**
     * Captures the selected Group Builder floor at the exact Confirm Group
     * click boundary. The candidate remains inert until Hypixel sends the
     * exact successful Party Finder queue message.
     */
    static boolean observeGroupBuilderConfirm(String title, List<MenuEntry> entries,
                                              int clickedSlot, long observedAtNanos) {
        clearGroupBuilderCandidate();
        Optional<DungeonFloor> floor = floorFromGroupBuilder(title, entries, clickedSlot);
        if (floor.isEmpty()) return false;
        confirmedGroupBuilderFloor = floor.orElseThrow();
        confirmedGroupBuilderAtNanos = observedAtNanos;
        return true;
    }

    static void acceptPartyFinderMember(DungeonJoinParser.DungeonJoinEvent event) {
        if (event == null) return;
        String member = key(event.playerName());
        pendingPartyFinderMembers.remove(member);
        activeMembers.add(member);
        memberClasses.put(member, new PartyMemberClass(event.playerName(), event.dungeonClass()));
    }

    static void rejectPartyFinderMember(String player) {
        String member = key(player);
        pendingPartyFinderMembers.remove(member);
        if (!activeMembers.contains(member)) memberClasses.remove(member);
    }

    static void forgetMember(String player) {
        removeMember(player);
    }

    static boolean trustedBaselineCaptured() {
        return trustedBaselineCaptured;
    }

    static void reset() {
        clearListing();
    }

    static Optional<DungeonFloor> floorFromPartyFinder(
            String title, List<MenuEntry> entries, String localPlayer) {
        String cleanTitle = clean(title).toLowerCase(Locale.ROOT);
        if (!cleanTitle.contains("party finder") || entries == null || entries.isEmpty()
                || !FriendName.valid(localPlayer)) return Optional.empty();

        int maximumSlot = entries.stream().mapToInt(MenuEntry::slot).max().orElse(-1);
        int bottomStart = Math.max(0, maximumSlot - 8);
        boolean hasDelist = entries.stream().anyMatch(entry -> entry.slot() >= bottomStart
                && isDelistControl(entry));
        if (!hasDelist) return Optional.empty();

        for (MenuEntry entry : entries) {
            if (entry.playerHead() && entry.slot() >= bottomStart
                    && belongsToLocalPlayer(entry, localPlayer)) {
                Optional<DungeonFloor> floor = floorFromOwnPartyEntry(entry);
                if (floor.isPresent()) return floor;
            }
        }
        for (MenuEntry entry : entries) {
            if (!entry.playerHead() || !belongsToLocalPlayer(entry, localPlayer)) continue;
            Optional<DungeonFloor> floor = floorFromOwnPartyEntry(entry);
            if (floor.isPresent()) return floor;
        }
        return Optional.empty();
    }

    static Optional<DungeonFloor> floorFromGroupBuilder(
            String title, List<MenuEntry> entries, int clickedSlot) {
        if (!clean(title).equalsIgnoreCase("Group Builder")
                || entries == null || entries.isEmpty() || clickedSlot < 0) {
            return Optional.empty();
        }

        MenuEntry confirm = entries.stream()
                .filter(entry -> entry.slot() == clickedSlot)
                .findFirst().orElse(null);
        if (confirm == null || !confirm.emeraldBlock()
                || !clean(confirm.name()).equalsIgnoreCase("Confirm Group")
                || confirm.tooltip().stream().map(DungeonPartyFinderFloorTracker::clean)
                .noneMatch(line -> line.equalsIgnoreCase("Click to confirm!"))) {
            return Optional.empty();
        }

        String dungeon = selectedValue(entries, "Select Dungeon Type").orElse("");
        String floor = selectedValue(entries, "Select Floor").orElse("");
        String cleanDungeon = clean(dungeon).toLowerCase(Locale.ROOT);
        if (!cleanDungeon.contains("catacombs")) return Optional.empty();

        Matcher match = FLOOR.matcher("Floor: " + floor);
        if (!match.matches()) return Optional.empty();
        String floorValue = match.group(1);
        if (floorValue.equalsIgnoreCase("Entrance")) {
            return Optional.of(new DungeonFloor("E"));
        }
        int number = roman(floorValue.toUpperCase(Locale.ROOT));
        if (number < 1) return Optional.empty();
        boolean master = cleanDungeon.contains("master mode");
        return Optional.of(new DungeonFloor((master ? "M" : "F") + number));
    }

    private static Optional<String> selectedValue(List<MenuEntry> entries, String entryName) {
        for (MenuEntry entry : entries) {
            if (!clean(entry.name()).equalsIgnoreCase(entryName)) continue;
            for (String raw : entry.tooltip()) {
                Matcher selected = CURRENTLY_SELECTED.matcher(clean(raw));
                if (selected.matches()) return Optional.of(selected.group(1).trim());
            }
        }
        return Optional.empty();
    }

    private static boolean belongsToLocalPlayer(MenuEntry entry, String localPlayer) {
        String local = localPlayer.toLowerCase(Locale.ROOT);
        String name = clean(entry.name()).toLowerCase(Locale.ROOT);
        if (name.equals(local + "'s party")) {
            return true;
        }
        for (String raw : entry.tooltip()) {
            String line = clean(raw).toLowerCase(Locale.ROOT);
            if (line.equals("you are in this party") || line.equals("you are in this party!")) return true;
        }
        return false;
    }

    private static boolean isDelistControl(MenuEntry entry) {
        if (hasDelistSemantics(entry.name())) return true;
        return entry.tooltip().stream().anyMatch(DungeonPartyFinderFloorTracker::hasDelistSemantics);
    }

    private static boolean hasDelistSemantics(String raw) {
        String text = clean(raw).toLowerCase(Locale.ROOT);
        return text.contains("delist")
                || text.contains("remove group")
                || text.contains("remove party");
    }

    private static Optional<DungeonFloor> floorFromOwnPartyEntry(MenuEntry entry) {
        boolean catacombs = false;
        boolean master = false;
        String floorValue = null;
        for (String raw : entry.tooltip()) {
            String line = clean(raw);
            String lower = line.toLowerCase(Locale.ROOT);
            if (lower.startsWith("dungeon:") && lower.contains("catacombs")) {
                catacombs = true;
                master = lower.contains("master mode") || lower.contains("mm ");
            }
            Matcher floor = FLOOR.matcher(line);
            if (floor.matches()) floorValue = floor.group(1);
        }
        if (!catacombs || floorValue == null) return Optional.empty();
        if (floorValue.equalsIgnoreCase("Entrance")) return Optional.of(new DungeonFloor("E"));
        int number = roman(floorValue.toUpperCase(Locale.ROOT));
        return number < 1 ? Optional.empty()
                : Optional.of(new DungeonFloor((master ? "M" : "F") + number));
    }

    static Map<String, PartyMemberClass> classesFromOwnParty(
            List<MenuEntry> entries, String localPlayer) {
        return Map.copyOf(parseGuiRoster(entries, localPlayer, null).membersByName());
    }

    private static ParsedGuiRoster parseGuiRoster(
            List<MenuEntry> entries, String localPlayer, DungeonFloor expectedFloor) {
        if (entries == null || !FriendName.valid(localPlayer)) return ParsedGuiRoster.empty();
        for (MenuEntry entry : entries) {
            if (!entry.playerHead()) continue;
            Optional<DungeonFloor> entryFloor = floorFromOwnPartyEntry(entry);
            if (!belongsToLocalPlayer(entry, localPlayer) || entryFloor.isEmpty()
                    || expectedFloor != null && !expectedFloor.equals(entryFloor.orElseThrow())) continue;
            LinkedHashMap<String, PartyMemberClass> result = new LinkedHashMap<>();
            for (String raw : entry.tooltip()) {
                Matcher matcher = MEMBER_CLASS.matcher(clean(raw));
                if (!matcher.matches()) continue;
                DungeonClassKey.parse(matcher.group(2)).ifPresent(dungeonClass -> {
                    PartyMemberClass member = new PartyMemberClass(matcher.group(1), dungeonClass);
                    result.put(key(member.playerName()), member);
                });
            }
            // The leader is necessarily part of an own listing. Requiring its
            // row avoids treating an empty/partial lore parse as a complete
            // roster. A later consumer can additionally reconcile names with
            // the fresh official PartyInfo membership snapshot.
            return new ParsedGuiRoster(result, result.containsKey(key(localPlayer)));
        }
        return ParsedGuiRoster.empty();
    }

    private static Optional<DungeonClassKey> selectedClass(List<MenuEntry> entries) {
        for (MenuEntry entry : entries) {
            if (entry.slot() != 45) continue;
            for (String raw : entry.tooltip()) {
                Matcher matcher = SELECTED_CLASS.matcher(clean(raw));
                if (matcher.matches()) return DungeonClassKey.parse(matcher.group(1));
            }
        }
        return Optional.empty();
    }

    private static boolean putClass(String player, DungeonClassKey dungeonClass) {
        if (!FriendName.valid(player) || dungeonClass == null) return false;
        String key = key(player);
        PartyMemberClass next = new PartyMemberClass(player, dungeonClass);
        PartyMemberClass previous = memberClasses.put(key, next);
        return previous == null || previous.dungeonClass() != next.dungeonClass()
                || !previous.playerName().equalsIgnoreCase(next.playerName());
    }

    private static void removeMember(String player) {
        String member = key(player);
        memberClasses.remove(member);
        activeMembers.remove(member);
        pendingPartyFinderMembers.remove(member);
        authoritativeGuiRoster = null;
    }

    private static void observeTrustedJoin(String player) {
        if (!FriendName.valid(player)) return;
        String member = key(player);
        if (pendingPartyFinderMembers.contains(member)) return;
        activeMembers.add(member);
        memberClasses.putIfAbsent(member, new PartyMemberClass(player, null));
        authoritativeGuiRoster = null;
    }

    private static void beginQueuedListing() {
        generation++;
        // The queue confirmation starts a new roster/admission generation, but
        // it does not disprove a floor already read from the local player's own
        // ownership-proven listing. The GUI normally closes at this point, so
        // clearing that floor would make the subsequent request omit ?floor.
        clearRosterState();
        awaitingQueuedListing = true;
    }

    private static void promoteFreshGroupBuilderFloor(long observedAtNanos) {
        DungeonFloor candidate = confirmedGroupBuilderFloor;
        long capturedAt = confirmedGroupBuilderAtNanos;
        clearGroupBuilderCandidate();
        if (candidate == null || capturedAt <= 0L || observedAtNanos < capturedAt
                || observedAtNanos - capturedAt > GROUP_BUILDER_CONFIRM_MAX_AGE_NANOS) {
            return;
        }
        currentFloor = candidate;
    }

    private static void clearListing() {
        if (currentFloor != null || !memberClasses.isEmpty() || authoritativeGuiRoster != null) {
            generation++;
        }
        currentFloor = null;
        clearRosterState();
        awaitingQueuedListing = false;
        clearGroupBuilderCandidate();
    }

    private static void clearGroupBuilderCandidate() {
        confirmedGroupBuilderFloor = null;
        confirmedGroupBuilderAtNanos = 0L;
    }

    private static void clearRosterState() {
        memberClasses.clear();
        activeMembers.clear();
        pendingPartyFinderMembers.clear();
        trustedBaselineCaptured = false;
        authoritativeGuiRoster = null;
    }

    private static void clearTrustedRosterPreservingPending() {
        LinkedHashMap<String, PartyMemberClass> pendingClasses = new LinkedHashMap<>();
        for (String pending : pendingPartyFinderMembers) {
            PartyMemberClass value = memberClasses.get(pending);
            if (value != null) pendingClasses.put(pending, value);
        }
        memberClasses.clear();
        memberClasses.putAll(pendingClasses);
        activeMembers.clear();
        trustedBaselineCaptured = false;
        authoritativeGuiRoster = null;
    }

    private static String key(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int roman(String value) {
        return switch (value) {
            case "I", "1" -> 1;
            case "II", "2" -> 2;
            case "III", "3" -> 3;
            case "IV", "4" -> 4;
            case "V", "5" -> 5;
            case "VI", "6" -> 6;
            case "VII", "7" -> 7;
            default -> -1;
        };
    }

    private static String clean(String raw) {
        String stripped = net.minecraft.ChatFormatting.stripFormatting(raw == null ? "" : raw);
        return stripped == null ? "" : stripped.trim();
    }

    record MenuEntry(int slot, String name, List<String> tooltip,
                     boolean playerHead, boolean bookshelf, boolean emeraldBlock) {
        MenuEntry(int slot, String name, List<String> tooltip,
                  boolean playerHead, boolean bookshelf) {
            this(slot, name, tooltip, playerHead, bookshelf, false);
        }

        MenuEntry {
            tooltip = tooltip == null ? List.of() : List.copyOf(tooltip);
        }
    }

    record ListingContext(DungeonFloor floor, long generation) { }

    record GuiRosterSnapshot(DungeonFloor floor, long listingGeneration,
                             long observedAtNanos, boolean complete,
                             List<PartyMemberClass> members) {
        GuiRosterSnapshot {
            members = members == null ? List.of() : List.copyOf(members);
        }

        /** Strictly post-admission, bounded-age, complete evidence for one listing. */
        boolean usableFor(ListingContext expectedListing, long admissionStartedAtNanos,
                          long nowNanos, long maximumAgeNanos) {
            return complete
                    && expectedListing != null
                    && floor.equals(expectedListing.floor())
                    && listingGeneration == expectedListing.generation()
                    && observedAtNanos > admissionStartedAtNanos
                    && maximumAgeNanos >= 0L
                    && nowNanos >= observedAtNanos
                    && nowNanos - observedAtNanos <= maximumAgeNanos;
        }
    }

    private record ParsedGuiRoster(LinkedHashMap<String, PartyMemberClass> membersByName,
                                   boolean complete) {
        private ParsedGuiRoster {
            membersByName = membersByName == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(membersByName);
        }

        private static ParsedGuiRoster empty() {
            return new ParsedGuiRoster(new LinkedHashMap<>(), false);
        }
    }

    private static final class FriendName {
        private static boolean valid(String name) {
            return name != null && name.matches("[A-Za-z0-9_]{1,16}");
        }
    }
}
