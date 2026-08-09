package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrPhysicalRoundStateTest {
    @Test
    void initialRoundOwnsEveryPhysicalTileAndStartsWithDealerDiscard() {
        McrRoundState state = McrRoundState.start(20260808L, Wind.EAST);

        assertEquals(0, state.revision());
        assertEquals(Wind.EAST, state.currentSeat());
        assertEquals(McrRoundPhase.AWAITING_DISCARD, state.phase());
        assertEquals(14, state.hand(Wind.EAST).size());
        assertEquals(13, state.hand(Wind.SOUTH).size());
        assertTrue(state.melds(Wind.WEST).isEmpty());
        assertTrue(state.river(Wind.NORTH).isEmpty());
        assertFalse(state.reactionWindow().isPresent());
        assertFalse(state.outcome().isPresent());
        assertTrue(state.lastDraw().isPresent());
        assertTrue(state.lastDrawSource().isPresent());

        int placed = state.wall().remaining();
        for (Wind seat : Wind.values()) {
            placed += state.hand(seat).size();
            placed += state.flowers(seat).size();
        }
        assertEquals(144, placed);
        assertThrows(UnsupportedOperationException.class,
                () -> state.hand(Wind.EAST).add(McrTileInstance.fromId(0)));
    }

    @Test
    void physicalMeldsPreserveClaimSourceAndConvertToScoringMelds() {
        McrTileInstance m4 = McrTileInstance.of(Tile.M4, 0);
        McrTileInstance m5 = McrTileInstance.of(Tile.M5, 0);
        McrTileInstance m6 = McrTileInstance.of(Tile.M6, 0);
        McrPhysicalMeld chow = McrPhysicalMeld.chow(List.of(m6, m4, m5), m5, Wind.EAST);
        assertEquals(List.of(m4, m5, m6), chow.tiles());
        assertEquals(Meld.chow(Tile.M5), chow.scoringMeld());

        McrTileInstance p1 = McrTileInstance.of(Tile.P7, 0);
        McrTileInstance p2 = McrTileInstance.of(Tile.P7, 1);
        McrTileInstance p3 = McrTileInstance.of(Tile.P7, 2);
        McrTileInstance p4 = McrTileInstance.of(Tile.P7, 3);
        McrPhysicalMeld pung = McrPhysicalMeld.pung(List.of(p3, p1, p2), p1, Wind.NORTH);
        McrPhysicalMeld added = pung.addFourth(p4);
        assertEquals(McrMeldOrigin.ADDED_KONG, added.origin());
        assertEquals(p1, added.claimedDiscard());
        assertEquals(Wind.NORTH, added.sourceSeat());
        assertEquals(p4, added.addedTile());
        assertEquals(p4, added.tiles().getLast());
        assertEquals(Meld.openKong(Tile.P7), added.scoringMeld());

        McrPhysicalMeld concealed = McrPhysicalMeld.concealedKong(List.of(p1, p2, p3, p4));
        assertEquals(Meld.concealedKong(Tile.P7), concealed.scoringMeld());
    }

    @Test
    void malformedPhysicalMeldsAndDiscardTransitionsFailClosed() {
        McrTileInstance m4 = McrTileInstance.of(Tile.M4, 0);
        McrTileInstance m5 = McrTileInstance.of(Tile.M5, 0);
        McrTileInstance s6 = McrTileInstance.of(Tile.S6, 0);
        assertThrows(IllegalArgumentException.class,
                () -> McrPhysicalMeld.chow(List.of(m4, m5, s6), m5, Wind.EAST));
        assertThrows(IllegalArgumentException.class,
                () -> McrPhysicalMeld.pung(List.of(m5, m5, m5), m5, Wind.EAST));
        assertThrows(IllegalArgumentException.class,
                () -> McrPhysicalMeld.concealedKong(List.of(m4, m5, s6)));

        McrPhysicalDiscard pending = McrPhysicalDiscard.pending(Wind.EAST, m4);
        assertEquals(McrDiscardStatus.UNCLAIMED, pending.unclaimed().status());
        assertEquals(Wind.SOUTH, pending.claimedForMeld(Wind.SOUTH).claimant());
        assertThrows(IllegalArgumentException.class,
                () -> new McrPhysicalDiscard(Wind.EAST, m4,
                        McrDiscardStatus.MELD_CLAIMED, Wind.EAST));
        assertThrows(IllegalStateException.class, () -> pending.unclaimed().unclaimed());
    }

    @Test
    void roundOutcomeRejectsCallerForgedPayment() {
        McrRulesEngine engine = new StandardMcrRulesEngine();
        WinEvaluation evaluation = engine.evaluate(new WinInput(
                TileCounts.parse("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8",
                        "B1", "B1", "B1", "T2", "T2"),
                List.of(), Tile.M9,
                WinContext.standard(Wind.EAST, Wind.EAST, WinMethod.DISCARD)));
        Payment valid = McrPayments.settle(evaluation, Wind.EAST, Wind.SOUTH);
        assertEquals(valid, new McrRoundOutcome.Win(
                Wind.EAST, Wind.SOUTH, evaluation, valid).payment());

        Payment forged = new Payment(java.util.Map.of(
                Wind.EAST, 3, Wind.SOUTH, -1, Wind.WEST, -1, Wind.NORTH, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new McrRoundOutcome.Win(Wind.EAST, Wind.SOUTH, evaluation, forged));
    }
}
