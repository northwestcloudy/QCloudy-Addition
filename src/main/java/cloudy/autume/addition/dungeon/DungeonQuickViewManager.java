package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.QCloudyAdditionClient;
import cloudy.autume.addition.config.ConfigManager;
import cloudy.autume.addition.config.ModConfig;
import cloudy.autume.addition.dungeon.DungeonPartyAuthorityTracker.Readiness;
import cloudy.autume.addition.dungeon.requirements.DungeonClassKey;
import cloudy.autume.addition.dungeon.requirements.DungeonFloorKey;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvaluation;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvaluator;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidence;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirement;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.DuplicateClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementEvidenceValue.PartyMemberClass;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.DecimalRule;
import cloudy.autume.addition.dungeon.requirements.DungeonRequirementPolicy.LongRule;
import cloudy.autume.addition.i18n.ModText;
import cloudy.autume.addition.network.QcaApiClient;
import cloudy.autume.addition.tracker.HypixelSessionTracker;
import cloudy.autume.addition.tracker.LocationTracker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Runtime boundary for Dungeon Party Finder profile checks and admission actions. */
public final class DungeonQuickViewManager {
    private static final long AUTHORITY_TIMEOUT_NANOS = Duration.ofSeconds(3).toNanos();
    private static final long AUTHORITY_QUEUE_TIMEOUT_NANOS = Duration.ofSeconds(10).toNanos();
    private static final long FINAL_AUTHORITY_MAX_AGE_NANOS = Duration.ofSeconds(2).toNanos();
    private static final long ADMISSION_EVIDENCE_MAX_LOCAL_AGE_NANOS =
            Duration.ofSeconds(10).toNanos();
    private static final long ADMISSION_EVIDENCE_MAX_SOURCE_AGE_MILLIS =
            Duration.ofHours(72).toMillis();
    private static final long ADMISSION_EVIDENCE_MAX_FUTURE_SKEW_MILLIS =
            Duration.ofMinutes(5).toMillis();
    private static final Pattern LOCAL_KICK = Pattern.compile(
            "^(?:(?:party|p)\\s+(?:kick|remove)|kick)\\s+"
                    + "([A-Za-z0-9_]{3,16})(?:\\s.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final DungeonQuickViewService SERVICE = new DungeonQuickViewService(
            QcaApiClient.createDefault(userAgent()), Clock.systemUTC());
    private static final Map<String, Long> RECENT_JOINS = new HashMap<>();
    private static final DungeonQuickViewFailureGate FAILURE_GATE =
            new DungeonQuickViewFailureGate(Duration.ofSeconds(30));
    private static final Map<Long, AdmissionAttempt> ADMISSIONS = new LinkedHashMap<>();
    private static final Map<String, Long> LATEST_ADMISSION = new HashMap<>();
    private static final Map<String, Long> MEMBERSHIP_EPOCHS = new HashMap<>();
    private static final Set<ActionKey> SENT_ACTIONS = new HashSet<>();
    private static final DungeonQueueAdmissionContext QUEUE_CONTEXT =
            new DungeonQueueAdmissionContext();

    private static long session;
    private static long nextAttemptId;
    private static long nextMembershipEpoch;
    private static long admissionPolicyRevision;

    private DungeonQuickViewManager() { }

    public static void init() {
        DungeonPartyAuthorityTracker.init();
    }

    public static void updateScoreboard(List<String> lines) {
        QUEUE_CONTEXT.update(lines);
    }

    public static void updateContext(Minecraft client, List<String> scoreboardLines) {
        updateScoreboard(scoreboardLines);
        DungeonPartyFinderFloorTracker.update(client);
        cancelInvalidAdmissions();
    }

    /** Captures Group Builder selections before its confirmation click closes the menu. */
    public static void onContainerSlotClick(AbstractContainerScreen<?> screen, Slot clickedSlot,
                                            int buttonNum, ContainerInput input) {
        Minecraft client = Minecraft.getInstance();
        if (screen == null || clickedSlot == null || buttonNum != 0
                || input != ContainerInput.PICKUP
                || !HypixelSessionTracker.canUseDungeonQuickView()
                || !ConfigManager.get().dungeons.playerQuickView) {
            return;
        }
        DungeonPartyFinderFloorTracker.observeGroupBuilderConfirm(
                screen.getTitle().getString(),
                DungeonPartyFinderFloorTracker.menuEntries(client, screen),
                clickedSlot.index, System.nanoTime());
    }

    /** Called every client tick so a missing party-info response fails closed. */
    public static void tick(Minecraft client) {
        if (client == null || ADMISSIONS.isEmpty()) return;
        long now = System.nanoTime();
        for (AdmissionAttempt attempt : List.copyOf(ADMISSIONS.values())) {
            tryComplete(client, attempt, now);
        }
    }

    public static void onMessage(Minecraft client, Component message) {
        if (client == null || message == null) return;
        String raw = message.getString();
        boolean partyFinderQueued = DungeonJoinParser.partyFinderQueued(raw);
        boolean partyLifecycleReset = DungeonPartyFinderFloorTracker.observeSystemMessage(raw);
        observeDeparture(raw);
        if (partyLifecycleReset) resetPartyLifecycle(false);
        cancelInvalidAdmissions();

        if (!ConfigManager.get().dungeons.playerQuickView || client.player == null) return;
        if (partyFinderQueued) {
            DungeonFloor floor = DungeonPartyFinderFloorTracker.currentFloor();
            Component status = floor == null
                    ? ModText.component("dungeon.party_finder_floor_missing")
                    : ModText.component("dungeon.party_finder_floor_detected", floor.id());
            client.player.sendSystemMessage(Component.literal("[QCA] ")
                    .withStyle(ChatFormatting.AQUA).append(status));
        }
        DungeonJoinParser.event(raw).ifPresent(event -> {
            if (event.playerName().equalsIgnoreCase(client.getUser().getName())) return;
            long now = System.nanoTime();
            long membershipEpoch = claimMembershipEpoch(event.playerName(), now);
            if (membershipEpoch < 0L) return;
            updateScoreboard(LocationTracker.liveScoreboardLines(client));
            // Mark the exact Party Finder target before reading any currently
            // open listing so it can never be mistaken for a trusted/manual
            // member during the same client task.
            DungeonPartyFinderFloorTracker.observeJoin(event);
            DungeonPartyFinderFloorTracker.update(client);
            List<PartyMemberClass> existingClasses =
                    DungeonPartyFinderFloorTracker.existingClasses(event.playerName());
            request(client, event, existingClasses, now, membershipEpoch);
        });
    }

    /** Cancels a pending automatic action when the user manually removes that player. */
    public static void onOutgoingCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        if (normalized.startsWith("/")) normalized = normalized.substring(1).trim();
        Matcher kick = LOCAL_KICK.matcher(normalized);
        if (kick.matches()) cancelPlayer(kick.group(1));
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.equals("party leave") || lower.equals("p leave")
                || lower.equals("party disband") || lower.equals("p disband")) {
            resetPartyLifecycle(true);
        }
    }

    private static void request(Minecraft client, DungeonJoinParser.DungeonJoinEvent event,
                                List<PartyMemberClass> existingClasses,
                                long now, long membershipEpoch) {
        String playerKey = playerKey(event.playerName());

        long requestSession = session;
        DungeonPartyFinderFloorTracker.ListingContext listing =
                DungeonPartyFinderFloorTracker.currentListing();
        DungeonFloor advertisedFloor = listing == null
                ? DungeonPartyFinderFloorTracker.currentFloor() : listing.floor();
        DungeonFloor requestFloor = advertisedFloor == null
                ? QUEUE_CONTEXT.currentFloor() : advertisedFloor;
        String floor = requestFloor == null ? "" : requestFloor.id();

        DungeonRequirementPolicy policy = listing != null
                && listing.floor().id().equals(floor)
                && floorConsistentWithScoreboard(listing) ? currentPolicy(floor) : null;
        AdmissionAttempt admission = null;
        if (policy != null && policy.hasEnabledRules()) {
            long requiredResponse = DungeonPartyAuthorityTracker.requestRefresh();
            long authoritySession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
            admission = new AdmissionAttempt(++nextAttemptId, requestSession, event, floor,
                    listing, policy, existingClasses, membershipEpoch,
                    authoritySession, requiredResponse,
                    deadlineAfter(now, AUTHORITY_QUEUE_TIMEOUT_NANOS),
                    QUEUE_CONTEXT.generation(), admissionPolicyRevision);
            replaceAdmission(playerKey, admission);
        } else {
            // No enabled policy means this membership passed vacuously. Keep
            // its exact join-line class for future DUPE comparisons.
            DungeonPartyFinderFloorTracker.acceptPartyFinderMember(event);
        }

        DungeonQuickViewSnapshot cached = admission == null
                ? SERVICE.cached(event.playerName(), floor) : null;
        if (cached != null) {
            acceptSnapshot(client, requestSession, admission, cached);
            return;
        }
        if (!FAILURE_GATE.allowRequest(now)) {
            if (admission != null) finish(admission);
            client.player.sendSystemMessage(DungeonQuickViewMessage.unavailable(
                    event.playerName(), "Dungeon profile service is temporarily unavailable."));
            return;
        }
        AdmissionAttempt requestedAdmission = admission;
        CompletableFuture<DungeonQuickViewSnapshot> load = admission == null
                ? SERVICE.load(event.playerName(), floor)
                : SERVICE.loadForAdmission(event.playerName(), floor);
        load.whenComplete((snapshot, failure) -> client.execute(() -> {
            if (requestSession != session || client.player == null
                    || !ConfigManager.get().dungeons.playerQuickView) return;
            if (failure != null) {
                if (requestedAdmission != null) finish(requestedAdmission);
                DungeonQuickViewException problem = failure(failure);
                boolean notify = FAILURE_GATE.recordFailure(
                        problem.isServiceFailure(), System.nanoTime());
                if (notify) {
                    QCloudyAdditionClient.LOGGER.warn(
                            "Could not load Dungeon Quick View for {}", event.playerName(), problem);
                }
                client.player.sendSystemMessage(
                        DungeonQuickViewMessage.unavailable(event.playerName(), problem.getMessage()));
                return;
            }
            FAILURE_GATE.recordSuccess();
            acceptSnapshot(client, requestSession, requestedAdmission, snapshot);
        }));
    }

    private static void acceptSnapshot(Minecraft client, long requestSession,
                                       AdmissionAttempt admission,
                                       DungeonQuickViewSnapshot snapshot) {
        if (requestSession != session || client.player == null
                || !ConfigManager.get().dungeons.playerQuickView) return;
        if (admission == null) {
            client.player.sendSystemMessage(DungeonQuickViewMessage.build(snapshot, client.font));
            return;
        }
        if (!isCurrent(admission)) {
            // Cancelling automatic authority (listing changed, target left, or
            // the user acted manually) must not discard the independent Profile
            // result. A newer attempt for the same name owns presentation and
            // suppresses only this older duplicate callback.
            Long replacement = LATEST_ADMISSION.get(playerKey(admission.event.playerName()));
            if (replacement == null || replacement == admission.id) {
                admission.snapshot = snapshot;
                showProfileIfAvailable(admission);
            }
            return;
        }
        long receivedAtNanos = System.nanoTime();
        admission.snapshot = snapshot;
        admission.snapshotReceivedAtNanos = receivedAtNanos;
        admission.snapshotReceivedAtEpochMillis = System.currentTimeMillis();
        tryComplete(client, admission, receivedAtNanos);
    }

    static void onPartyAuthorityChanged() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || ADMISSIONS.isEmpty()) return;
        // Refresh any currently open own-listing classes in the same client
        // task as PartyInfo. The frozen active names remain the membership
        // boundary; PartyInfo and live Tab UUIDs independently reconcile them.
        DungeonPartyFinderFloorTracker.update(client);
        cancelInvalidAdmissions();
        long now = System.nanoTime();
        for (AdmissionAttempt attempt : List.copyOf(ADMISSIONS.values())) {
            tryComplete(client, attempt, now);
        }
    }

    private static void tryComplete(Minecraft client, AdmissionAttempt attempt, long now) {
        if (!isCurrent(attempt) || client.player == null) return;
        if (attempt.managerSession != session || !ConfigManager.get().dungeons.playerQuickView
                || !HypixelSessionTracker.canUseDungeonQuickView()) {
            finish(attempt);
            return;
        }
        if (attempt.admissionPolicyRevision != admissionPolicyRevision
                || !QUEUE_CONTEXT.unchanged(attempt.queueContextGeneration)
                || !sameListing(attempt.listing)
                || !floorConsistentWithScoreboard(attempt.listing)
                || !attempt.policy.equals(currentPolicy(attempt.floor))) {
            finish(attempt);
            if (attempt.snapshot != null) {
                client.player.sendSystemMessage(
                        DungeonQuickViewMessage.build(attempt.snapshot, client.font));
            }
            return;
        }
        if (attempt.snapshot == null) return;
        String evidenceBlocker = attempt.snapshot.requirementsEvidenceBlocker();
        if (!evidenceBlocker.isEmpty()) {
            showEvidenceUnknown(client, attempt, evidenceBlocker);
            return;
        }
        if (!admissionEvidenceFresh(attempt.snapshot,
                attempt.snapshotReceivedAtNanos, now,
                attempt.snapshotReceivedAtEpochMillis)) {
            showEvidenceUnknown(client, attempt, "SOURCE_STALE");
            return;
        }

        long requiredAuthorityResponse = attempt.awaitingFinalAuthority()
                ? attempt.finalAuthorityResponse : attempt.requiredAuthorityResponse;
        long authorityQueueDeadline = attempt.awaitingFinalAuthority()
                ? attempt.finalAuthorityQueueDeadlineNanos : attempt.authorityQueueDeadlineNanos;
        if (attempt.snapshot.playerUuid() == null
                || requiredAuthorityResponse == Long.MAX_VALUE) {
            showAuthorityUnknown(client, attempt, null,
                    "PARTY_AUTHORITY_UNAVAILABLE", now);
            return;
        }
        DungeonPartyAuthorityTracker.Snapshot party =
                DungeonPartyAuthorityTracker.snapshotFor(
                        attempt.authoritySession, requiredAuthorityResponse);
        long authorityDeadline = authorityResponseDeadline(
                attempt.authoritySession, requiredAuthorityResponse);
        if (authorityDeadline < 0L) {
            if (party == null && now <= authorityQueueDeadline) return;
            showAuthorityUnknown(client, attempt, party,
                    "PARTY_AUTHORITY_UNAVAILABLE", now);
            return;
        }
        if (party == null && now <= authorityDeadline) return;
        if (!authorityResponseUsable(party, now, authorityDeadline)) {
            showAuthorityUnknown(client, attempt, party,
                    "PARTY_AUTHORITY_UNAVAILABLE", now);
            return;
        }
        Readiness readiness = party.readiness(attempt.authoritySession,
                requiredAuthorityResponse, client.player.getUUID(),
                attempt.snapshot.playerUuid());
        if (readiness != Readiness.READY) {
            showAuthorityUnknown(client, attempt, party,
                    authorityReason(readiness), now);
            return;
        }

        DungeonRequirementEvidence evidence = evidenceFor(client, attempt, party, true, now);
        DungeonRequirementEvaluation evaluation =
                DungeonRequirementEvaluator.evaluate(
                        attempt.policy, evidence, attempt.event.dungeonClass());

        if (evaluation.failures().isEmpty()) {
            if (shouldAwaitClassRoster(attempt, evaluation, now, authorityDeadline)) return;
            if (evaluation.unknowns().isEmpty()) {
                DungeonPartyFinderFloorTracker.acceptPartyFinderMember(attempt.event);
            }
            finish(attempt);
            client.player.sendSystemMessage(evaluation.unknowns().isEmpty()
                    ? DungeonQuickViewMessage.build(attempt.snapshot, client.font)
                    : DungeonQuickViewMessage.buildWithUnknowns(
                    attempt.snapshot, evaluation, client.font));
            return;
        }

        // A confirmed failure starts a second, independent PartyInfo refresh.
        // Only its response may authorize the command, so a slow profile request
        // can never reuse party membership captured many seconds earlier.
        if (!attempt.awaitingFinalAuthority()) {
            if (!refreshQueueContext(client, attempt)) {
                finish(attempt);
                client.player.sendSystemMessage(
                        DungeonQuickViewMessage.build(attempt.snapshot, client.font));
                return;
            }
            attempt.finalAuthorityResponse = DungeonPartyAuthorityTracker.requestRefresh();
            attempt.finalAuthorityQueueDeadlineNanos =
                    deadlineAfter(now, AUTHORITY_QUEUE_TIMEOUT_NANOS);
            if (attempt.finalAuthorityResponse == Long.MAX_VALUE) {
                showAuthorityUnknown(client, attempt, null,
                        "PARTY_AUTHORITY_UNAVAILABLE", now);
            }
            return;
        }

        // Final guard is intentionally immediately adjacent to the visible
        // failure report and the one server command it authorizes.
        if (!party.freshAt(now, FINAL_AUTHORITY_MAX_AGE_NANOS)
                || !finalGuard(client, attempt, party, now, authorityDeadline)) {
            finish(attempt);
            if (client.player != null) {
                client.player.sendSystemMessage(DungeonQuickViewMessage.failures(
                        attempt.snapshot.playerName(), attempt.floor, evaluation,
                        "PARTY_AUTHORITY_UNAVAILABLE"));
            }
            return;
        }
        var connection = client.getConnection();
        if (connection == null) {
            finish(attempt);
            if (client.player != null) {
                client.player.sendSystemMessage(DungeonQuickViewMessage.failures(
                        attempt.snapshot.playerName(), attempt.floor, evaluation,
                        "PARTY_AUTHORITY_UNAVAILABLE"));
            }
            return;
        }
        ActionKey action = new ActionKey(attempt.managerSession, attempt.listing.generation(),
                attempt.membershipEpoch, attempt.floor, attempt.snapshot.playerUuid());
        if (!SENT_ACTIONS.add(action)) {
            finish(attempt);
            return;
        }
        finish(attempt);
        client.player.sendSystemMessage(DungeonQuickViewMessage.failures(
                attempt.snapshot.playerName(), attempt.floor, evaluation));
        connection.sendCommand("party kick " + attempt.snapshot.playerName());
    }

    private static void showAuthorityUnknown(
            Minecraft client, AdmissionAttempt attempt,
            DungeonPartyAuthorityTracker.Snapshot party,
            String reason, long nowNanos) {
        DungeonRequirementEvidence evidence = evidenceFor(
                client, attempt, party, false, nowNanos, reason);
        DungeonRequirementEvaluation unavailable = DungeonRequirementEvaluator.evaluate(
                attempt.policy, evidence, attempt.event.dungeonClass());
        finish(attempt);
        if (client.player != null) {
            Component message;
            if (!unavailable.failures().isEmpty()) {
                message = DungeonQuickViewMessage.failures(
                        attempt.snapshot.playerName(), attempt.floor, unavailable, reason);
            } else if (!unavailable.unknowns().isEmpty()) {
                message = DungeonQuickViewMessage.buildWithUnknowns(
                        attempt.snapshot, unavailable, client.font);
            } else {
                message = DungeonQuickViewMessage.build(attempt.snapshot, client.font);
            }
            client.player.sendSystemMessage(message);
        }
    }

    private static void showEvidenceUnknown(
            Minecraft client, AdmissionAttempt attempt, String reason) {
        String unavailableReason = reason == null || reason.isBlank()
                ? "UNSUPPORTED_EVIDENCE" : reason;
        DungeonRequirementEvaluation unavailable = DungeonRequirementEvaluator.evaluate(
                attempt.policy, DungeonRequirementEvidence.unavailable(
                        attempt.policy.floor(), unavailableReason),
                attempt.event.dungeonClass());
        finish(attempt);
        if (client.player != null) {
            client.player.sendSystemMessage(DungeonQuickViewMessage.buildWithUnknowns(
                    attempt.snapshot, unavailable, client.font));
        }
    }

    private static String authorityReason(Readiness readiness) {
        if (readiness == null) return "PARTY_AUTHORITY_UNAVAILABLE";
        return switch (readiness) {
            case NOT_IN_PARTY -> "PARTY_NOT_CONFIRMED";
            case NOT_LEADER -> "LOCAL_PLAYER_NOT_PARTY_LEADER";
            case TARGET_ABSENT -> "TARGET_NOT_IN_PARTY";
            case PENDING, READY -> "PARTY_AUTHORITY_UNAVAILABLE";
        };
    }

    /** Pure clock-domain gate used by the runtime and deterministic tests. */
    static boolean admissionEvidenceFresh(
            DungeonQuickViewSnapshot snapshot,
            long receivedAtNanos,
            long nowNanos,
            long receivedAtEpochMillis) {
        if (snapshot == null || !snapshot.requirementsEvidenceTrusted()
                || receivedAtNanos <= 0L || nowNanos < receivedAtNanos
                || receivedAtEpochMillis <= 0L) return false;
        long localAge = nowNanos - receivedAtNanos;
        if (localAge > ADMISSION_EVIDENCE_MAX_LOCAL_AGE_NANOS) return false;

        // requirementsEvidence.fetchedAt is Unix epoch milliseconds and is the
        // oldest of all backend sources (including the 72-hour identity cache).
        // Compare it only with the wall-clock value captured when this network
        // response was received; local decision aging above stays monotonic.
        long fetchedAt = snapshot.evidenceFetchedAt();
        if (fetchedAt > receivedAtEpochMillis) {
            return fetchedAt - receivedAtEpochMillis
                    <= ADMISSION_EVIDENCE_MAX_FUTURE_SKEW_MILLIS;
        }
        return receivedAtEpochMillis - fetchedAt
                <= ADMISSION_EVIDENCE_MAX_SOURCE_AGE_MILLIS;
    }

    /** Exact-ticket PartyInfo must arrive and be processed inside its hard deadline. */
    static boolean authorityResponseUsable(
            DungeonPartyAuthorityTracker.Snapshot snapshot,
            long nowNanos,
            long deadlineNanos) {
        return snapshot != null && snapshot.receivedAtNanos() > 0L
                && nowNanos >= snapshot.receivedAtNanos()
                && snapshot.receivedAtNanos() <= deadlineNanos
                && nowNanos <= deadlineNanos;
    }

    /** Response time starts at physical dispatch, not while a request waits in FIFO. */
    static long authorityResponseDeadline(long authoritySession, long ticket) {
        long dispatchedAt = DungeonPartyAuthorityTracker.dispatchedAtNanosFor(
                authoritySession, ticket);
        return dispatchedAt <= 0L ? -1L : deadlineAfter(dispatchedAt, AUTHORITY_TIMEOUT_NANOS);
    }

    static long deadlineAfter(long startNanos, long durationNanos) {
        if (startNanos <= 0L || durationNanos < 0L) return -1L;
        return startNanos > Long.MAX_VALUE - durationNanos
                ? Long.MAX_VALUE : startNanos + durationNanos;
    }

    private static DungeonRequirementEvidence evidenceFor(
            Minecraft client, AdmissionAttempt attempt,
            DungeonPartyAuthorityTracker.Snapshot party, boolean authorityReady,
            long nowNanos) {
        return evidenceFor(client, attempt, party, authorityReady, nowNanos,
                "PARTY_AUTHORITY_UNAVAILABLE");
    }

    private static DungeonRequirementEvidence evidenceFor(
            Minecraft client, AdmissionAttempt attempt,
            DungeonPartyAuthorityTracker.Snapshot party, boolean authorityReady,
            long nowNanos, String authorityUnavailableReason) {
        DungeonRequirementEvidence base = attempt.snapshot.requirementsEvidence();
        if (base == null || base.floor() != attempt.policy.floor()) {
            base = DungeonRequirementEvidence.unavailable(
                    attempt.policy.floor(), "UNSUPPORTED_EVIDENCE");
        }
        String evidenceBlocker = attempt.snapshot.requirementsEvidenceBlocker();
        if (!evidenceBlocker.isEmpty()) {
            return DungeonRequirementEvidence.unavailable(
                    attempt.policy.floor(), evidenceBlocker);
        }

        DungeonClassKey newcomerClass = attempt.event.dungeonClass();
        List<PartyMemberClass> classRoster =
                DungeonPartyFinderFloorTracker.currentClassesFor(attempt.existingClasses);
        DuplicateClass duplicate;
        var connection = client.getConnection();
        if (!authorityReady || party == null || connection == null) {
            duplicate = DuplicateClass.unknown(newcomerClass, classRoster,
                    authorityUnavailableReason);
        } else {
            Set<UUID> partyExisting = new HashSet<>(party.members().keySet());
            partyExisting.remove(attempt.snapshot.playerUuid());
            LinkedHashMap<String, UUID> resolvedIdentities = new LinkedHashMap<>();
            for (PartyMemberClass member : classRoster) {
                var playerInfo = connection.getPlayerInfoIgnoreCase(member.playerName());
                UUID uuid = playerInfo == null || playerInfo.getProfile() == null
                        ? null : playerInfo.getProfile().id();
                if (uuid != null) resolvedIdentities.put(playerKey(member.playerName()), uuid);
            }
            duplicate = reconcileDuplicateClass(
                    newcomerClass, classRoster, resolvedIdentities, partyExisting);
        }
        return new DungeonRequirementEvidence(base.floor(), base.floorCompletions(), duplicate,
                base.fastestCompletionMs(), base.averageSecrets(), base.magicalPower(),
                base.witherBlade(), base.terminator(), base.goldenDragon(), base.enderDragon());
    }

    static DuplicateClass reconcileDuplicateClass(
            DungeonClassKey newcomerClass,
            List<PartyMemberClass> classRoster,
            Map<String, UUID> resolvedIdentities,
            Set<UUID> partyExisting) {
        List<PartyMemberClass> roster = classRoster == null ? List.of() : List.copyOf(classRoster);
        Set<UUID> partyMembers = partyExisting == null ? Set.of() : Set.copyOf(partyExisting);
        Map<String, UUID> identities = resolvedIdentities == null ? Map.of() : resolvedIdentities;
        if (partyMembers.size() != roster.size()) {
            return DuplicateClass.unknown(newcomerClass, roster,
                    "PARTY_ROSTER_COUNT_MISMATCH|" + partyMembers.size()
                            + "|" + roster.size());
        }

        LinkedHashMap<UUID, PartyMemberClass> classesByUuid = new LinkedHashMap<>();
        List<String> unmapped = new ArrayList<>();
        for (PartyMemberClass member : roster) {
            UUID uuid = identities.get(playerKey(member.playerName()));
            if (uuid == null || classesByUuid.putIfAbsent(uuid, member) != null) {
                unmapped.add(member.playerName());
            }
        }
        if (!unmapped.isEmpty()) {
            return DuplicateClass.unknown(newcomerClass, roster,
                    "PARTY_CLASS_IDENTITIES_UNAVAILABLE|" + joinedNames(unmapped));
        }

        List<String> missing = partyMembers.stream()
                .filter(uuid -> !classesByUuid.containsKey(uuid))
                .map(DungeonQuickViewManager::shortUuid)
                .toList();
        List<String> unexpected = classesByUuid.entrySet().stream()
                .filter(entry -> !partyMembers.contains(entry.getKey()))
                .map(entry -> entry.getValue().playerName())
                .toList();
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            return DuplicateClass.unknown(newcomerClass, roster,
                    "PARTY_ROSTER_IDENTITY_MISMATCH|" + joinedNames(missing)
                            + "|" + joinedNames(unexpected));
        }

        List<String> missingClasses = roster.stream()
                .filter(member -> member.dungeonClass() == null)
                .map(PartyMemberClass::playerName)
                .toList();
        if (newcomerClass == null) {
            return DuplicateClass.unknown(null, roster, "NEWCOMER_CLASS_MISSING");
        }
        if (!missingClasses.isEmpty()) {
            return DuplicateClass.unknown(newcomerClass, roster,
                    "PARTY_CLASSES_MISSING|" + joinedNames(missingClasses));
        }
        return DuplicateClass.known(newcomerClass, roster);
    }

    private static String joinedNames(List<String> names) {
        return names == null ? "" : names.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) return "unknown-uuid";
        String value = uuid.toString();
        return "UUID-" + value.substring(0, Math.min(8, value.length()));
    }

    private static boolean shouldAwaitClassRoster(
            AdmissionAttempt attempt, DungeonRequirementEvaluation evaluation,
            long nowNanos, long authorityDeadlineNanos) {
        return attempt.policy.duplicateClassDisallowed()
                && nowNanos < authorityDeadlineNanos
                && evaluation.unknowns().stream().anyMatch(finding ->
                finding.requirement() == DungeonRequirement.DISALLOW_DUPLICATE_CLASS);
    }

    private static boolean finalGuard(Minecraft client, AdmissionAttempt attempt,
                                      DungeonPartyAuthorityTracker.Snapshot party,
                                      long nowNanos, long authorityDeadlineNanos) {
        // This reads vanilla's current scoreboard immediately next to the
        // command boundary. The ordinary periodic context cache may be up to one
        // second old and may not authorize an action by itself.
        if (!refreshQueueContext(client, attempt)) return false;
        if (!isCurrent(attempt) || client.player == null || client.getConnection() == null
                || attempt.managerSession != session || !HypixelSessionTracker.canUseDungeonQuickView()
                || attempt.admissionPolicyRevision != admissionPolicyRevision
                || !QUEUE_CONTEXT.unchanged(attempt.queueContextGeneration)
                || !sameListing(attempt.listing)
                || !floorConsistentWithScoreboard(attempt.listing)
                || !admissionEvidenceFresh(attempt.snapshot,
                attempt.snapshotReceivedAtNanos, nowNanos,
                attempt.snapshotReceivedAtEpochMillis)
                || !attempt.snapshot.requirementsEvidenceTrusted()
                || !attempt.event.playerName().equalsIgnoreCase(attempt.snapshot.playerName())
                || !attempt.event.playerName().equalsIgnoreCase(attempt.snapshot.queryName())
                || !attempt.policy.equals(currentPolicy(attempt.floor))
                || !attempt.awaitingFinalAuthority()
                || !authorityResponseUsable(
                party, nowNanos, authorityDeadlineNanos)
                || !party.freshAt(nowNanos, FINAL_AUTHORITY_MAX_AGE_NANOS)) return false;
        var targetInfo = client.getConnection().getPlayerInfoIgnoreCase(
                attempt.event.playerName());
        if (targetInfo == null || targetInfo.getProfile() == null
                || !attempt.snapshot.playerUuid().equals(targetInfo.getProfile().id())) return false;
        return party.readiness(attempt.authoritySession, attempt.finalAuthorityResponse,
                client.player.getUUID(), attempt.snapshot.playerUuid()) == Readiness.READY;
    }

    private static boolean refreshQueueContext(Minecraft client, AdmissionAttempt attempt) {
        if (client == null || attempt == null) return false;
        updateScoreboard(LocationTracker.liveScoreboardLines(client));
        return QUEUE_CONTEXT.unchanged(attempt.queueContextGeneration)
                && floorConsistentWithScoreboard(attempt.listing);
    }

    static DungeonRequirementPolicy policyFor(ModConfig config, String floor) {
        DungeonFloorKey key = DungeonFloorKey.parse(floor).orElse(null);
        if (config == null || key == null || config.dungeons == null
                || !config.dungeons.playerQuickView
                || config.dungeons.partyFinderAutoKick == null
                || !config.dungeons.partyFinderAutoKick.enabled
                || config.dungeons.partyFinderAutoKick.rulesVersion
                != ModConfig.PartyFinderAutoKick.SUPPORTED_RULES_VERSION) return null;
        ModConfig.DungeonFloorRequirements raw =
                config.dungeons.partyFinderAutoKick.rulesFor(floor);
        if (raw == null) return null;
        return new DungeonRequirementPolicy(key,
                longRule(raw.minFloorCompletions), raw.disallowDuplicateClass,
                longRule(raw.maxFastestCompletionMs), decimalRule(raw.minAverageSecrets),
                longRule(raw.minMagicalPower), raw.requireWitherBlade,
                raw.requireTerminator, raw.requireGoldenDragon, raw.requireEnderDragon);
    }

    private static DungeonRequirementPolicy currentPolicy(String floor) {
        return policyFor(ConfigManager.get(), floor);
    }

    private static LongRule longRule(ModConfig.NumericLongRule rule) {
        if (rule == null) return LongRule.disabled(0);
        long value = Math.max(0L, rule.value);
        return rule.enabled ? LongRule.enabled(value) : LongRule.disabled(value);
    }

    private static DecimalRule decimalRule(ModConfig.NumericDoubleRule rule) {
        if (rule == null) return DecimalRule.disabled(0);
        double value = Double.isFinite(rule.value) && rule.value >= 0 ? rule.value : 0;
        return rule.enabled ? DecimalRule.enabled(value) : DecimalRule.disabled(value);
    }

    public static void onWorldChange() {
        clearRuntime();
        DungeonPartyAuthorityTracker.onWorldChange();
    }

    public static void reset() {
        clearRuntime();
        DungeonPartyAuthorityTracker.reset();
    }

    /**
     * Invalidates every in-flight admission whenever an admission-relevant
     * switch or threshold is edited. Recreating the old value later cannot
     * revive a decision that began under an earlier user configuration.
     */
    public static void onAdmissionPolicyChanged() {
        admissionPolicyRevision++;
        cancelAllAdmissions(true);
    }

    static long admissionPolicyRevisionForTesting() {
        return admissionPolicyRevision;
    }

    private static void clearRuntime() {
        session++;
        QUEUE_CONTEXT.reset();
        DungeonPartyFinderFloorTracker.reset();
        RECENT_JOINS.clear();
        FAILURE_GATE.reset();
        SERVICE.reset();
        cancelAllAdmissions(false);
        MEMBERSHIP_EPOCHS.clear();
        SENT_ACTIONS.clear();
    }

    private static void replaceAdmission(String playerKey, AdmissionAttempt attempt) {
        Long previous = LATEST_ADMISSION.put(playerKey, attempt.id);
        if (previous != null) {
            AdmissionAttempt old = ADMISSIONS.remove(previous);
            if (old != null) {
                cancelAuthorityRequests(old);
                old.finished = true;
            }
        }
        ADMISSIONS.put(attempt.id, attempt);
    }

    private static boolean isCurrent(AdmissionAttempt attempt) {
        return attempt != null && !attempt.finished && ADMISSIONS.get(attempt.id) == attempt
                && LATEST_ADMISSION.getOrDefault(playerKey(attempt.event.playerName()), -1L)
                == attempt.id;
    }

    private static void finish(AdmissionAttempt attempt) {
        if (attempt == null || attempt.finished) return;
        cancelAuthorityRequests(attempt);
        attempt.finished = true;
        ADMISSIONS.remove(attempt.id);
        LATEST_ADMISSION.remove(playerKey(attempt.event.playerName()), attempt.id);
        DungeonPartyFinderFloorTracker.rejectPartyFinderMember(attempt.event.playerName());
    }

    private static void cancelPlayer(String player) {
        DungeonPartyFinderFloorTracker.forgetMember(player);
        String key = playerKey(player);
        Long id = LATEST_ADMISSION.remove(key);
        if (id == null) return;
        AdmissionAttempt attempt = ADMISSIONS.remove(id);
        if (attempt != null) {
            showProfileIfAvailable(attempt);
            cancelAuthorityRequests(attempt);
            attempt.finished = true;
        }
    }

    private static void cancelAllAdmissions() {
        cancelAllAdmissions(true);
    }

    private static void cancelAllAdmissions(boolean showProfiles) {
        for (AdmissionAttempt attempt : ADMISSIONS.values()) {
            if (showProfiles) showProfileIfAvailable(attempt);
            cancelAuthorityRequests(attempt);
            attempt.finished = true;
            DungeonPartyFinderFloorTracker.rejectPartyFinderMember(attempt.event.playerName());
        }
        ADMISSIONS.clear();
        LATEST_ADMISSION.clear();
    }

    private static void cancelAuthorityRequests(AdmissionAttempt attempt) {
        if (attempt == null) return;
        DungeonPartyAuthorityTracker.cancelRefresh(
                attempt.authoritySession, attempt.requiredAuthorityResponse);
        DungeonPartyAuthorityTracker.cancelRefresh(
                attempt.authoritySession, attempt.finalAuthorityResponse);
    }

    private static void cancelInvalidAdmissions() {
        DungeonPartyFinderFloorTracker.ListingContext current =
                DungeonPartyFinderFloorTracker.currentListing();
        if (current == null) {
            cancelAllAdmissions();
            MEMBERSHIP_EPOCHS.clear();
            return;
        }
        for (AdmissionAttempt attempt : List.copyOf(ADMISSIONS.values())) {
            if (!current.equals(attempt.listing)
                    || !QUEUE_CONTEXT.unchanged(attempt.queueContextGeneration)
                    || !floorConsistentWithScoreboard(attempt.listing)) {
                showProfileIfAvailable(attempt);
                finish(attempt);
            }
        }
    }

    private static void resetPartyLifecycle(boolean resetListingTracker) {
        if (resetListingTracker) DungeonPartyFinderFloorTracker.reset();
        cancelAllAdmissions(true);
        RECENT_JOINS.clear();
        MEMBERSHIP_EPOCHS.clear();
        SENT_ACTIONS.clear();
    }

    private static void showProfileIfAvailable(AdmissionAttempt attempt) {
        if (attempt == null || attempt.profileDisplayed || attempt.snapshot == null
                || attempt.managerSession != session
                || !ConfigManager.get().dungeons.playerQuickView) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;
        attempt.profileDisplayed = true;
        client.player.sendSystemMessage(DungeonQuickViewMessage.build(
                attempt.snapshot, client.font));
    }

    static boolean observeDeparture(String raw) {
        String player = DungeonJoinParser.departure(raw).orElse(null);
        if (player == null) return false;
        String key = playerKey(player);
        cancelPlayer(player);
        // The next exact Party Finder join is a new membership period even if
        // it arrives inside the ordinary two-second duplicate-message window.
        RECENT_JOINS.remove(key);
        MEMBERSHIP_EPOCHS.remove(key);
        return true;
    }

    /** Claims one stable epoch for a party membership, or -1 for a duplicate join line. */
    static long claimMembershipEpoch(String player, long nowNanos) {
        String key = playerKey(player);
        Long last = RECENT_JOINS.put(key, nowNanos);
        if (last != null && nowNanos - last < 2_000_000_000L) return -1L;
        RECENT_JOINS.entrySet().removeIf(
                entry -> nowNanos - entry.getValue() > 30_000_000_000L);
        return MEMBERSHIP_EPOCHS.computeIfAbsent(key, ignored -> ++nextMembershipEpoch);
    }

    private static boolean sameListing(DungeonPartyFinderFloorTracker.ListingContext expected) {
        return expected != null && expected.equals(DungeonPartyFinderFloorTracker.currentListing());
    }

    private static boolean floorConsistentWithScoreboard(
            DungeonPartyFinderFloorTracker.ListingContext listing) {
        return listing != null && QUEUE_CONTEXT.allows(listing.floor());
    }

    private static String playerKey(String player) {
        return player == null ? "" : player.toLowerCase(Locale.ROOT);
    }

    private static DungeonQuickViewException failure(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException
                && cause.getCause() != null) cause = cause.getCause();
        return cause instanceof DungeonQuickViewException exception ? exception
                : new DungeonQuickViewException("Dungeon profile request failed.", cause);
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance()
                .getModContainer(QCloudyAdditionClient.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        return ("QCloudy_Addition/" + version).replaceAll("[^A-Za-z0-9._+/-]", "_");
    }

    private static final class AdmissionAttempt {
        private final long id;
        private final long managerSession;
        private final DungeonJoinParser.DungeonJoinEvent event;
        private final String floor;
        private final DungeonPartyFinderFloorTracker.ListingContext listing;
        private final DungeonRequirementPolicy policy;
        private final List<PartyMemberClass> existingClasses;
        private final long membershipEpoch;
        private final long authoritySession;
        private final long requiredAuthorityResponse;
        private final long authorityQueueDeadlineNanos;
        private final long queueContextGeneration;
        private final long admissionPolicyRevision;
        private long finalAuthorityResponse = -1L;
        private long finalAuthorityQueueDeadlineNanos;
        private DungeonQuickViewSnapshot snapshot;
        private long snapshotReceivedAtNanos;
        private long snapshotReceivedAtEpochMillis;
        private boolean profileDisplayed;
        private boolean finished;

        private AdmissionAttempt(long id, long managerSession,
                                 DungeonJoinParser.DungeonJoinEvent event, String floor,
                                 DungeonPartyFinderFloorTracker.ListingContext listing,
                                 DungeonRequirementPolicy policy,
                                 List<PartyMemberClass> existingClasses,
                                 long membershipEpoch,
                                 long authoritySession, long requiredAuthorityResponse,
                                 long authorityQueueDeadlineNanos,
                                 long queueContextGeneration,
                                 long admissionPolicyRevision) {
            this.id = id;
            this.managerSession = managerSession;
            this.event = event;
            this.floor = floor;
            this.listing = listing;
            this.policy = policy;
            this.existingClasses = existingClasses == null ? List.of() : List.copyOf(existingClasses);
            this.membershipEpoch = membershipEpoch;
            this.authoritySession = authoritySession;
            this.requiredAuthorityResponse = requiredAuthorityResponse;
            this.authorityQueueDeadlineNanos = authorityQueueDeadlineNanos;
            this.queueContextGeneration = queueContextGeneration;
            this.admissionPolicyRevision = admissionPolicyRevision;
        }

        private boolean awaitingFinalAuthority() {
            return finalAuthorityResponse > 0L;
        }
    }

    private record ActionKey(long session, long listingGeneration, long membershipEpoch,
                             String floor, UUID playerUuid) { }
}
