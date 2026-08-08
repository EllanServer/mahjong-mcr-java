package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrLegalActionGeneratorTest {
    private static final long HAND_SALT = 0x6d63722d6163746eL;
    private final McrRoundEngine engine = new McrRoundEngine();
    private final McrLegalActionGenerator actions = new McrLegalActionGenerator(engine);

    @Test
    void currentSeatReceivesEveryExactDiscardAndOnlyAcceptedKongs() {
        McrRoundState state = orderedRound();
        List<McrLegalAction> legal = actions.forPlayer(state, Wind.EAST, HAND_SALT);

        List<McrLegalAction> discards = legal.stream()
                .filter(action -> action.action() instanceof McrRoundAction.Discard)
                .toList();
        assertEquals(state.hand(Wind.EAST).size(), discards.size());
        assertEquals(legal.size(), new HashSet<>(
                legal.stream().map(McrLegalAction::key).toList()).size());
        for (McrLegalAction legalAction : legal) {
            assertTrue(engine.transition(state, legalAction.action()).accepted(), legalAction.key());
        }
        assertTrue(actions.forPlayer(state, Wind.SOUTH, HAND_SALT).isEmpty());
        McrProjectionIds ids = McrProjectionIds.forHand(HAND_SALT);
        for (McrLegalAction legalAction : discards) {
            McrRoundAction.Discard discard = (McrRoundAction.Discard) legalAction.action();
            long projected = Long.parseLong(legalAction.key().substring("discard:".length()));
            assertEquals(ids.project(discard.tile()), projected);
            assertNotEquals(discard.tile().id(), projected);
        }
    }

    @Test
    void legalSelfDrawIsProjectedWithoutOfferingItForAnOrdinaryHand() {
        McrRoundState winning = eastPureStraightInitialWin();
        assertTrue(actions.forPlayer(winning, Wind.EAST, HAND_SALT).stream()
                .anyMatch(action -> action.key().equals("self_draw_win")));
        assertFalse(actions.forPlayer(orderedRound(), Wind.EAST, HAND_SALT).stream()
                .anyMatch(action -> action.key().equals("self_draw_win")));
    }

    @Test
    void reactionProjectionContainsOnlyIssuedExactOptionsAndPass() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 4, 12);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrRoundState reacting = engine.transition(
                before,
                new McrRoundAction.Discard(Wind.EAST, McrTileInstance.fromId(16))).state();

        List<McrLegalAction> south = actions.forPlayer(reacting, Wind.SOUTH, HAND_SALT);
        assertTrue(south.stream().anyMatch(action -> action.key().equals("respond:pass")));
        assertTrue(south.stream().anyMatch(action -> action.key().startsWith("respond:chow:")));
        for (McrLegalAction legalAction : south) {
            assertTrue(engine.transition(reacting, legalAction.action()).accepted(), legalAction.key());
        }
        assertTrue(actions.forPlayer(reacting, Wind.EAST, HAND_SALT).isEmpty());
        assertEquals(List.of("close_reactions"), actions.systemActions(reacting).stream()
                .map(McrLegalAction::key)
                .toList());
    }

    @Test
    void aRecordedResponderCannotSubmitAgainAndEndedRoundsExposeNothing() {
        McrRoundState before = twoSeatReactionWait();
        McrRoundState reacting = engine.transition(
                before,
                new McrRoundAction.Discard(Wind.EAST, McrTileInstance.fromId(32))).state();
        McrLegalAction pass = actions.forPlayer(reacting, Wind.WEST, HAND_SALT).stream()
                .filter(action -> action.key().equals("respond:pass"))
                .findFirst()
                .orElseThrow();
        McrRoundState responded = engine.transition(reacting, pass.action()).state();
        assertEquals(McrRoundPhase.REACTIONS, responded.phase());
        assertTrue(actions.forPlayer(responded, Wind.WEST, HAND_SALT).isEmpty());

        McrRoundState ended = engine.transition(
                eastPureStraightInitialWin(), new McrRoundAction.SelfDrawWin(Wind.EAST)).state();
        assertEquals(McrRoundPhase.ENDED, ended.phase());
        for (Wind seat : Wind.values()) {
            assertTrue(actions.forPlayer(ended, seat, HAND_SALT).isEmpty());
        }
        assertTrue(actions.systemActions(ended).isEmpty());
    }

    @Test
    void rejectedInputStateIdentityRemainsUntouchedByGeneratedCommands() {
        McrRoundState state = orderedRound();
        McrRoundAction stale = new McrRoundAction.Discard(
                Wind.SOUTH, state.hand(Wind.SOUTH).getFirst());
        assertSame(state, engine.transition(state, stale).state());
    }

    private static McrRoundState orderedRound() {
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(McrTileInstance.fullSet())), Wind.EAST);
    }

    private static McrRoundState twoSeatReactionWait() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 0, 32);
        int[] southPositions = {4, 5, 6, 7, 20, 21, 22, 23, 36, 37, 38, 39, 49};
        int[] southTiles = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int index = 0; index < southPositions.length; index++) {
            place(order, southPositions[index], southTiles[index]);
        }
        place(order, 8, 33);
        place(order, 9, 34);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static McrRoundState eastPureStraightInitialWin() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        int[] eastPositions = {0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48};
        int[] concealedBeforeWin = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int index = 0; index < eastPositions.length; index++) {
            place(order, eastPositions[index], concealedBeforeWin[index]);
        }
        place(order, 52, 32);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static void place(ArrayList<McrTileInstance> order, int position, int tileId) {
        int current = -1;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id() == tileId) {
                current = index;
                break;
            }
        }
        McrTileInstance displaced = order.get(position);
        order.set(position, order.get(current));
        order.set(current, displaced);
    }
}
