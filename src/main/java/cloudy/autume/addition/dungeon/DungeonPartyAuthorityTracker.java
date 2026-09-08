package cloudy.autume.addition.dungeon;

import cloudy.autume.addition.tracker.HypixelSessionTracker;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Authoritative, session-only party membership and role snapshots supplied by
 * the official Hypixel Mod API. Chat parsing is deliberately not allowed to
 * authorize an automatic party command.
 *
 * <p>PartyInfo packets carry no request id. The shared Mod API may also be used
 * by other installed mods, so every successful outbound PartyInfo request is
 * recorded in a per-connection FIFO ledger by the sendPacket mixin. A response
 * can complete a QCA ticket only when the FIFO entry is the exact QCA
 * (authority session, ticket) marker currently in flight.</p>
 */
public final class DungeonPartyAuthorityTracker {
    private static final int MAX_OUTBOUND_MARKERS = 256;
    private static final int MAX_EXACT_SNAPSHOTS = 32;
    private static final Object TEST_CONNECTION = new Object();
    private static final ThreadLocal<OutboundIntent> OUTBOUND_INTENT = new ThreadLocal<>();

    private static boolean initialized;
    private static long sessionEpoch;
    private static long nextRequestTicket;
    private static long responseSerial;
    private static long receivedAtNanos;
    private static boolean inParty;
    private static Map<UUID, PartyRole> members = Map.of();
    private static final ArrayDeque<Long> pendingTickets = new ArrayDeque<>();
    private static final ArrayDeque<OutboundMarker> outboundLedger = new ArrayDeque<>();
    private static final LinkedHashMap<RequestKey, Snapshot> exactSnapshots =
            new LinkedHashMap<>();
    private static long activeTicket;
    private static boolean handlingResponse;
    private static boolean dispatchBlocked;
    private static boolean connectionKnown;
    private static Object connectionIdentity;
    private static boolean testingSender;
    private static Object testingConnection = TEST_CONNECTION;
    private static PartyInfoSender sender = DungeonPartyAuthorityTracker::sendPartyInfoPacket;

    private DungeonPartyAuthorityTracker() { }

    static synchronized void init() {
        if (initialized) return;
        initialized = true;
    }

    /**
     * Queues a fresh snapshot and returns its unique ticket. QCA permits only
     * one PartyInfo request in flight: a response matched to the exact active
     * marker completes it before the next FIFO ticket is sent. A lost response
     * leaves the queue stopped (and therefore safely non-authoritative) until
     * the world/session or physical connection is reset.
     */
    static synchronized long requestRefresh() {
        observeConnectionLocked(testingSender
                ? testingConnection : currentConnectionIdentity());
        if (dispatchBlocked || (!testingSender
                && !HypixelSessionTracker.canSendHypixelCommand())) {
            return Long.MAX_VALUE;
        }
        long ticket = ++nextRequestTicket;
        pendingTickets.addLast(ticket);
        if (activeTicket == 0L && !handlingResponse && !dispatchNext()) {
            return Long.MAX_VALUE;
        }
        return ticket;
    }

    /** Latest matched QCA response, retained for diagnostics and compatibility. */
    static synchronized Snapshot snapshot() {
        return new Snapshot(sessionEpoch, responseSerial, inParty, members, receivedAtNanos);
    }

    /**
     * Returns only the response matched to this exact authority marker. Later
     * QCA responses can never stand in for an earlier ticket.
     */
    static synchronized Snapshot snapshotFor(long expectedSession, long ticket) {
        if (dispatchBlocked || ticket <= 0L || ticket == Long.MAX_VALUE) return null;
        return exactSnapshots.get(new RequestKey(expectedSession, ticket));
    }

    /**
     * Invalidates QCA authority for a level/world transition. The physical
     * connection ledger is deliberately retained: an old response must first
     * consume its old-session marker and may not be mistaken for a reused
     * ticket in the new world.
     */
    static synchronized void onWorldChange() {
        resetAuthorityLocked(false);
    }

    /** A disconnect is a hard boundary; the old connection can no longer reply. */
    static synchronized void reset() {
        resetAuthorityLocked(true);
    }

    /**
     * Called by HypixelModAPISendPacketMixin after a successful shared API
     * send. This method is public solely as a narrow cross-package mixin hook.
     */
    public static synchronized void onSuccessfulPartyInfoSend(
            ServerboundPartyInfoPacket packet) {
        if (packet == null) return;
        observeConnectionLocked(currentConnectionIdentity());
        OutboundIntent intent = OUTBOUND_INTENT.get();
        RequestKey owner = intent != null && intent.packet == packet ? intent.owner : null;
        boolean recorded = appendOutboundMarkerLocked(owner);
        if (intent != null && intent.packet == packet) intent.recorded = recorded;
    }

    /**
     * Called at the shared HypixelModAPI handler-loop entry, before any
     * third-party registered handler can throw and prevent later callbacks.
     */
    public static void onPartyInfoResponseIngress(ClientboundPartyInfoPacket packet,
                                                  long ingressAtNanos) {
        acceptProductionIngress(packet, ingressAtNanos);
    }

    /** Error responses consume one FIFO position but can never produce authority. */
    public static void onPartyInfoErrorIngress(long ingressAtNanos) {
        acceptProductionErrorIngress(ingressAtNanos);
    }

    private static void acceptProductionIngress(ClientboundPartyInfoPacket packet,
                                                long ingressAtNanos) {
        Minecraft client = Minecraft.getInstance();
        Object connection = client == null ? null : client.getConnection();
        TaskScheduler scheduler = action -> {
            if (client == null) return false;
            try {
                client.execute(action);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        };
        acceptIngress(packet, ingressAtNanos, connection, scheduler, true);
    }

    private static void acceptProductionErrorIngress(long ingressAtNanos) {
        // Capture/evaluate the connection after the timestamp so no main-thread
        // queue delay can refresh the apparent age of an inbound response.
        Object connection = currentConnectionIdentity();
        acceptErrorIngress(connection, ingressAtNanos);
    }

    private static void acceptIngress(ClientboundPartyInfoPacket packet,
                                      long ingressAtNanos,
                                      Object connection,
                                      TaskScheduler scheduler,
                                      boolean notifyManager) {
        if (packet == null) return;
        Map<UUID, PartyRole> nextMembers;
        boolean nextInParty;
        RequestKey owner;
        synchronized (DungeonPartyAuthorityTracker.class) {
            owner = consumeResponseMarkerLocked(connection);
            if (owner == null) return;
            if (ingressAtNanos <= 0L) {
                failMatchedResponseLocked(owner);
                return;
            }
            try {
                LinkedHashMap<UUID, PartyRole> next = new LinkedHashMap<>();
                packet.getMemberMap().forEach((uuid, member) -> {
                    if (uuid != null && member != null && member.getRole() != null) {
                        next.put(uuid, member.getRole());
                    }
                });
                nextMembers = Map.copyOf(next);
                nextInParty = packet.isInParty();
            } catch (RuntimeException ignored) {
                failMatchedResponseLocked(owner);
                return;
            }
        }

        RequestKey matchedOwner = owner;
        boolean scheduled;
        try {
            scheduled = scheduler != null && scheduler.schedule(() ->
                    acceptMatched(matchedOwner, nextInParty, nextMembers,
                            ingressAtNanos, notifyManager));
        } catch (RuntimeException ignored) {
            scheduled = false;
        }
        if (!scheduled) {
            synchronized (DungeonPartyAuthorityTracker.class) {
                failMatchedResponseLocked(matchedOwner);
            }
        }
    }

    private static void acceptErrorIngress(Object connection, long ignoredIngressAtNanos) {
        synchronized (DungeonPartyAuthorityTracker.class) {
            RequestKey owner = consumeResponseMarkerLocked(connection);
            if (owner != null) failMatchedResponseLocked(owner);
        }
    }

    private static synchronized void acceptMatched(RequestKey owner,
                                                   boolean nextInParty,
                                                   Map<UUID, PartyRole> nextMembers,
                                                   long ingressAtNanos,
                                                   boolean notifyManager) {
        if (!isActiveLocked(owner) || dispatchBlocked) return;
        Snapshot exact = new Snapshot(owner.sessionEpoch, owner.ticket, nextInParty,
                nextMembers, ingressAtNanos);
        rememberExactSnapshotLocked(owner, exact);
        responseSerial = owner.ticket;
        activeTicket = 0L;
        inParty = nextInParty;
        members = exact.members();
        receivedAtNanos = ingressAtNanos;

        handlingResponse = true;
        try {
            if (notifyManager) DungeonQuickViewManager.onPartyAuthorityChanged();
        } finally {
            handlingResponse = false;
            dispatchNext();
        }
    }

    /** Pops exactly one server FIFO position and returns it only if QCA owns it. */
    private static RequestKey consumeResponseMarkerLocked(Object connection) {
        observeConnectionLocked(connection);
        OutboundMarker marker = outboundLedger.pollFirst();
        if (marker == null) {
            // The response cannot be attributed. Continuing would leave FIFO
            // alignment unknown and could let a later packet be guessed as a
            // QCA answer, so the complete authority session fails closed.
            failAttributionLocked();
            return null;
        }
        RequestKey owner = marker.owner;
        if (owner == null) return null;
        if (owner.sessionEpoch != sessionEpoch) {
            // A retained marker from before a world change consumes this old
            // response without touching a possibly reused current ticket.
            return null;
        }
        if (!isActiveLocked(owner)) {
            failAttributionLocked();
            return null;
        }
        return owner;
    }

    private static boolean appendOutboundMarkerLocked(RequestKey owner) {
        if (outboundLedger.size() >= MAX_OUTBOUND_MARKERS) {
            failAttributionLocked();
            return false;
        }
        outboundLedger.addLast(new OutboundMarker(owner));
        return true;
    }

    private static void rememberExactSnapshotLocked(RequestKey owner, Snapshot snapshot) {
        exactSnapshots.put(owner, snapshot);
        while (exactSnapshots.size() > MAX_EXACT_SNAPSHOTS) {
            var iterator = exactSnapshots.entrySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private static void failMatchedResponseLocked(RequestKey owner) {
        if (owner != null && owner.sessionEpoch == sessionEpoch
                && isActiveLocked(owner)) failAttributionLocked();
    }

    private static void failAttributionLocked() {
        activeTicket = 0L;
        pendingTickets.clear();
        exactSnapshots.clear();
        responseSerial = 0L;
        receivedAtNanos = 0L;
        inParty = false;
        members = Map.of();
        // Do not discard remaining markers on the same physical connection.
        // Their responses can still arrive after a world transition; losing
        // their FIFO positions would let one collide with a new ticket.
        dispatchBlocked = true;
    }

    private static boolean isActiveLocked(RequestKey owner) {
        return owner != null && owner.sessionEpoch == sessionEpoch
                && owner.ticket == activeTicket;
    }

    /** Sends exactly the next queued request; false permanently fails this session closed. */
    private static boolean dispatchNext() {
        if (activeTicket != 0L || pendingTickets.isEmpty() || dispatchBlocked) return true;
        long ticket = pendingTickets.removeFirst();
        activeTicket = ticket;
        RequestKey owner = new RequestKey(sessionEpoch, ticket);
        boolean sent;
        try {
            sent = (testingSender || HypixelSessionTracker.canSendHypixelCommand())
                    && sender.send(ticket);
            if (sent && testingSender) sent = appendOutboundMarkerLocked(owner);
        } catch (RuntimeException ignored) {
            sent = false;
        }
        if (sent && isActiveLocked(owner)) return true;

        if (isActiveLocked(owner)) activeTicket = 0L;
        pendingTickets.clear();
        dispatchBlocked = true;
        return false;
    }

    private static boolean sendPartyInfoPacket(long ticket) {
        ServerboundPartyInfoPacket packet = new ServerboundPartyInfoPacket();
        OutboundIntent intent;
        synchronized (DungeonPartyAuthorityTracker.class) {
            intent = new OutboundIntent(new RequestKey(sessionEpoch, ticket), packet);
        }
        OUTBOUND_INTENT.set(intent);
        try {
            boolean sent = HypixelModAPI.getInstance().sendPacket(packet);
            // If the required mixin did not observe this exact packet, its
            // eventual response cannot safely be attributed to QCA.
            return sent && intent.recorded;
        } finally {
            OUTBOUND_INTENT.remove();
        }
    }

    private static Object currentConnectionIdentity() {
        Minecraft client = Minecraft.getInstance();
        return client == null ? null : client.getConnection();
    }

    private static void observeConnectionLocked(Object nextConnection) {
        if (!connectionKnown) {
            connectionKnown = true;
            connectionIdentity = nextConnection;
            return;
        }
        if (connectionIdentity == nextConnection) return;
        // Responses cannot cross physical connections, so unlike a world
        // transition the old ledger is safe to discard here.
        resetAuthorityLocked(true);
        connectionKnown = true;
        connectionIdentity = nextConnection;
    }

    private static void resetAuthorityLocked(boolean clearConnection) {
        boolean preservePoison = dispatchBlocked && !clearConnection;
        sessionEpoch++;
        nextRequestTicket = 0L;
        responseSerial = 0L;
        receivedAtNanos = 0L;
        inParty = false;
        members = Map.of();
        exactSnapshots.clear();
        pendingTickets.clear();
        activeTicket = 0L;
        handlingResponse = false;
        dispatchBlocked = preservePoison;
        if (clearConnection) {
            outboundLedger.clear();
            connectionKnown = false;
            connectionIdentity = null;
        }
    }

    @FunctionalInterface
    interface PartyInfoSender {
        boolean send(long ticket);
    }

    @FunctionalInterface
    interface TaskScheduler {
        boolean schedule(Runnable action);
    }

    /** Added to decoded PartyInfo objects by the required packet mixin. */
    public interface IngressTimestampCarrier {
        long qca$ingressAtNanos();
    }

    /** Deterministic package-private seam; production always uses the default sender. */
    static synchronized void useSenderForTesting(PartyInfoSender testSender) {
        sender = testSender == null ? DungeonPartyAuthorityTracker::sendPartyInfoPacket : testSender;
        testingSender = testSender != null;
    }

    static synchronized void restoreSenderAfterTesting() {
        sender = DungeonPartyAuthorityTracker::sendPartyInfoPacket;
        testingSender = false;
        testingConnection = TEST_CONNECTION;
    }

    static synchronized void useConnectionForTesting(Object connection) {
        testingConnection = connection;
        observeConnectionLocked(connection);
    }

    static synchronized void recordForeignSendForTesting() {
        observeConnectionLocked(testingConnection);
        appendOutboundMarkerLocked(null);
    }

    static void acceptForTesting(boolean nextInParty,
                                 Map<UUID, PartyRole> nextMembers,
                                 long ingressAtNanos) {
        acceptForTesting(nextInParty, nextMembers, ingressAtNanos, action -> {
            action.run();
            return true;
        });
    }

    static void acceptForTesting(boolean nextInParty,
                                 Map<UUID, PartyRole> nextMembers,
                                 long ingressAtNanos,
                                 TaskScheduler scheduler) {
        RequestKey owner;
        synchronized (DungeonPartyAuthorityTracker.class) {
            owner = consumeResponseMarkerLocked(testingConnection);
        }
        if (owner == null) return;
        Map<UUID, PartyRole> copied = nextMembers == null ? Map.of() : Map.copyOf(nextMembers);
        RequestKey matchedOwner = owner;
        boolean scheduled;
        try {
            scheduled = scheduler != null && scheduler.schedule(() ->
                    acceptMatched(matchedOwner, nextInParty, copied, ingressAtNanos, false));
        } catch (RuntimeException ignored) {
            scheduled = false;
        }
        if (!scheduled) {
            synchronized (DungeonPartyAuthorityTracker.class) {
                failMatchedResponseLocked(matchedOwner);
            }
        }
    }

    static synchronized void acceptErrorForTesting(long ingressAtNanos) {
        acceptErrorIngress(testingConnection, ingressAtNanos);
    }

    static synchronized long activeTicketForTesting() {
        return activeTicket;
    }

    static synchronized List<Long> pendingTicketsForTesting() {
        return List.copyOf(pendingTickets);
    }

    static synchronized int outboundLedgerSizeForTesting() {
        return outboundLedger.size();
    }

    static synchronized boolean dispatchBlockedForTesting() {
        return dispatchBlocked;
    }

    enum Readiness {
        PENDING,
        READY,
        NOT_IN_PARTY,
        NOT_LEADER,
        TARGET_ABSENT
    }

    record Snapshot(long sessionEpoch, long responseSerial, boolean inParty,
                    Map<UUID, PartyRole> members, long receivedAtNanos) {
        Snapshot(long sessionEpoch, long responseSerial, boolean inParty,
                 Map<UUID, PartyRole> members) {
            this(sessionEpoch, responseSerial, inParty, members, System.nanoTime());
        }

        Snapshot {
            members = members == null ? Map.of() : Map.copyOf(members);
        }

        boolean freshAt(long nowNanos, long maximumAgeNanos) {
            return receivedAtNanos > 0L && maximumAgeNanos >= 0L
                    && nowNanos >= receivedAtNanos
                    && nowNanos - receivedAtNanos <= maximumAgeNanos;
        }

        Readiness readiness(long expectedSession, long requiredResponse,
                            UUID localPlayer, UUID target) {
            if (sessionEpoch != expectedSession || responseSerial != requiredResponse) {
                return Readiness.PENDING;
            }
            if (!inParty) return Readiness.NOT_IN_PARTY;
            if (localPlayer == null || members.get(localPlayer) != PartyRole.LEADER) {
                return Readiness.NOT_LEADER;
            }
            if (target == null || !members.containsKey(target)) return Readiness.TARGET_ABSENT;
            return Readiness.READY;
        }
    }

    private record RequestKey(long sessionEpoch, long ticket) { }

    private record OutboundMarker(RequestKey owner) { }

    private static final class OutboundIntent {
        private final RequestKey owner;
        private final ServerboundPartyInfoPacket packet;
        private boolean recorded;

        private OutboundIntent(RequestKey owner, ServerboundPartyInfoPacket packet) {
            this.owner = owner;
            this.packet = packet;
        }
    }
}
