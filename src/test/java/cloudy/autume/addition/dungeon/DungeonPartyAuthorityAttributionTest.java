package cloudy.autume.addition.dungeon;

import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket.PartyRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonPartyAuthorityAttributionTest {
    private static final UUID LOCAL =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID FIRST_TARGET =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SECOND_TARGET =
            UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Object FIRST_CONNECTION = new Object();

    @BeforeEach
    void setUp() {
        DungeonPartyAuthorityTracker.reset();
        DungeonPartyAuthorityTracker.useSenderForTesting(ticket -> true);
        DungeonPartyAuthorityTracker.useConnectionForTesting(FIRST_CONNECTION);
    }

    @AfterEach
    void tearDown() {
        DungeonPartyAuthorityTracker.reset();
        DungeonPartyAuthorityTracker.restoreSenderAfterTesting();
        DungeonPartyAuthorityTracker.restoreClockAfterTesting();
    }

    @Test
    void foreignResponseBeforeQcaResponseCannotCompleteTheQcaTicket() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        DungeonPartyAuthorityTracker.recordForeignSendForTesting();
        long ticket = DungeonPartyAuthorityTracker.requestRefresh();

        assertEquals(2, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 100L);

        assertNull(DungeonPartyAuthorityTracker.snapshotFor(session, ticket));
        assertEquals(ticket, DungeonPartyAuthorityTracker.activeTicketForTesting());
        assertFalse(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());

        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 200L);
        DungeonPartyAuthorityTracker.Snapshot exact =
                DungeonPartyAuthorityTracker.snapshotFor(session, ticket);
        assertEquals(ticket, exact.responseSerial());
        assertEquals(200L, exact.receivedAtNanos());
        assertEquals(DungeonPartyAuthorityTracker.Readiness.READY,
                exact.readiness(session, ticket, LOCAL, FIRST_TARGET));
    }

    @Test
    void foreignErrorConsumesOnlyTheForeignFifoPosition() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        DungeonPartyAuthorityTracker.recordForeignSendForTesting();
        long ticket = DungeonPartyAuthorityTracker.requestRefresh();

        DungeonPartyAuthorityTracker.acceptErrorForTesting(100L);
        assertFalse(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(ticket, DungeonPartyAuthorityTracker.activeTicketForTesting());
        assertNull(DungeonPartyAuthorityTracker.snapshotFor(session, ticket));

        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 200L);
        assertEquals(200L, DungeonPartyAuthorityTracker
                .snapshotFor(session, ticket).receivedAtNanos());
    }

    @Test
    void exactTicketSnapshotsRemainDistinctWhenALaterRequestCompletes() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long first = DungeonPartyAuthorityTracker.requestRefresh();
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 100L);
        long second = DungeonPartyAuthorityTracker.requestRefresh();
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(SECOND_TARGET), 200L);

        DungeonPartyAuthorityTracker.Snapshot firstSnapshot =
                DungeonPartyAuthorityTracker.snapshotFor(session, first);
        DungeonPartyAuthorityTracker.Snapshot secondSnapshot =
                DungeonPartyAuthorityTracker.snapshotFor(session, second);

        assertTrue(firstSnapshot.members().containsKey(FIRST_TARGET));
        assertFalse(firstSnapshot.members().containsKey(SECOND_TARGET));
        assertTrue(secondSnapshot.members().containsKey(SECOND_TARGET));
        assertFalse(secondSnapshot.members().containsKey(FIRST_TARGET));
        assertEquals(second, DungeonPartyAuthorityTracker.snapshot().responseSerial());
    }

    @Test
    void queuedTicketsStartTheirDeadlineOnlyWhenActuallyDispatched() {
        AtomicLong clock = new AtomicLong(1_000L);
        DungeonPartyAuthorityTracker.useClockForTesting(clock::get);
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();

        long first = DungeonPartyAuthorityTracker.requestRefresh();
        long second = DungeonPartyAuthorityTracker.requestRefresh();

        assertEquals(1_000L,
                DungeonPartyAuthorityTracker.dispatchedAtNanosFor(session, first));
        assertEquals(-1L,
                DungeonPartyAuthorityTracker.dispatchedAtNanosFor(session, second));

        clock.set(7_000L);
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 6_999L);

        assertEquals(7_000L,
                DungeonPartyAuthorityTracker.dispatchedAtNanosFor(session, second));
        assertEquals(7_000L + java.time.Duration.ofSeconds(3).toNanos(),
                DungeonQuickViewManager.authorityResponseDeadline(session, second));
    }

    @Test
    void cancelledQueuedTicketDoesNotBlockTheNextAdmission() {
        List<Long> sent = new ArrayList<>();
        DungeonPartyAuthorityTracker.useSenderForTesting(ticket -> {
            sent.add(ticket);
            return true;
        });
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long first = DungeonPartyAuthorityTracker.requestRefresh();
        long cancelled = DungeonPartyAuthorityTracker.requestRefresh();
        long third = DungeonPartyAuthorityTracker.requestRefresh();

        DungeonPartyAuthorityTracker.cancelRefresh(session, cancelled);
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 100L);

        assertEquals(List.of(first, third), sent);
        assertEquals(third, DungeonPartyAuthorityTracker.activeTicketForTesting());
        assertEquals(List.of(), DungeonPartyAuthorityTracker.pendingTicketsForTesting());
    }

    @Test
    void ingressTimestampDoesNotMoveForwardWhileClientExecutionIsDelayed() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long ticket = DungeonPartyAuthorityTracker.requestRefresh();
        List<Runnable> clientTasks = new ArrayList<>();

        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 125L,
                action -> {
                    clientTasks.add(action);
                    return true;
                });

        assertNull(DungeonPartyAuthorityTracker.snapshotFor(session, ticket));
        assertEquals(1, clientTasks.size());
        clientTasks.getFirst().run();

        DungeonPartyAuthorityTracker.Snapshot snapshot =
                DungeonPartyAuthorityTracker.snapshotFor(session, ticket);
        assertEquals(125L, snapshot.receivedAtNanos());
        assertTrue(snapshot.freshAt(175L, 50L));
        assertFalse(snapshot.freshAt(176L, 50L));
    }

    @Test
    void worldResetCannotReuseAnOldMarkerForTheSameTicketNumber() {
        long oldSession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long oldTicket = DungeonPartyAuthorityTracker.requestRefresh();
        assertEquals(1L, oldTicket);

        DungeonPartyAuthorityTracker.onWorldChange();
        long newSession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long newTicket = DungeonPartyAuthorityTracker.requestRefresh();
        assertEquals(1L, newTicket);
        assertTrue(newSession > oldSession);

        // The first response consumes only the retained old-session marker.
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 100L);
        assertNull(DungeonPartyAuthorityTracker.snapshotFor(newSession, newTicket));
        assertEquals(newTicket, DungeonPartyAuthorityTracker.activeTicketForTesting());

        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 200L);
        assertEquals(200L, DungeonPartyAuthorityTracker
                .snapshotFor(newSession, newTicket).receivedAtNanos());
    }

    @Test
    void newPhysicalConnectionDropsTheOldConnectionsLedger() {
        long oldSession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        DungeonPartyAuthorityTracker.requestRefresh();

        DungeonPartyAuthorityTracker.useConnectionForTesting(new Object());
        long newSession = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long newTicket = DungeonPartyAuthorityTracker.requestRefresh();
        assertTrue(newSession > oldSession);
        assertEquals(1, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());

        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 300L);
        assertEquals(300L, DungeonPartyAuthorityTracker
                .snapshotFor(newSession, newTicket).receivedAtNanos());
    }

    @Test
    void matchedErrorAndSendFailureBothFailClosed() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long ticket = DungeonPartyAuthorityTracker.requestRefresh();
        DungeonPartyAuthorityTracker.acceptErrorForTesting(100L);

        assertNull(DungeonPartyAuthorityTracker.snapshotFor(session, ticket));
        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(Long.MAX_VALUE, DungeonPartyAuthorityTracker.requestRefresh());

        DungeonPartyAuthorityTracker.reset();
        DungeonPartyAuthorityTracker.useConnectionForTesting(FIRST_CONNECTION);
        DungeonPartyAuthorityTracker.useSenderForTesting(ignored -> false);
        assertEquals(Long.MAX_VALUE, DungeonPartyAuthorityTracker.requestRefresh());
        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(0, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());
    }

    @Test
    void poisonedConnectionStaysBlockedAcrossWorldChangesAndKeepsForeignMarkers() {
        DungeonPartyAuthorityTracker.requestRefresh();
        DungeonPartyAuthorityTracker.recordForeignSendForTesting();
        DungeonPartyAuthorityTracker.acceptErrorForTesting(100L);

        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(1, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());
        DungeonPartyAuthorityTracker.onWorldChange();

        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(Long.MAX_VALUE, DungeonPartyAuthorityTracker.requestRefresh());
        assertEquals(1, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());

        // The delayed foreign response consumes only its retained position.
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 200L);
        assertEquals(0, DungeonPartyAuthorityTracker.outboundLedgerSizeForTesting());
        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
    }

    @Test
    void unmatchedResponseFailsTheWholeAuthoritySessionClosed() {
        DungeonPartyAuthorityTracker.acceptForTesting(true, party(FIRST_TARGET), 100L);

        assertTrue(DungeonPartyAuthorityTracker.dispatchBlockedForTesting());
        assertEquals(Long.MAX_VALUE, DungeonPartyAuthorityTracker.requestRefresh());
    }

    @Test
    void exactSnapshotHistoryIsBoundedAndEvictionNeverSubstitutesLatest() {
        long session = DungeonPartyAuthorityTracker.snapshot().sessionEpoch();
        long first = -1L;
        long latest = -1L;
        for (int index = 0; index < 33; index++) {
            long ticket = DungeonPartyAuthorityTracker.requestRefresh();
            if (index == 0) first = ticket;
            latest = ticket;
            DungeonPartyAuthorityTracker.acceptForTesting(
                    true, party(FIRST_TARGET), 100L + index);
        }

        assertNull(DungeonPartyAuthorityTracker.snapshotFor(session, first));
        assertEquals(latest, DungeonPartyAuthorityTracker
                .snapshotFor(session, latest).responseSerial());
    }

    private static Map<UUID, PartyRole> party(UUID target) {
        return Map.of(LOCAL, PartyRole.LEADER, target, PartyRole.MEMBER);
    }
}
