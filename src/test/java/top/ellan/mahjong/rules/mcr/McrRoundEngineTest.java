package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrRoundEngineTest {
    private final McrRoundEngine engine = new McrRoundEngine();

    @Test
    void unclaimedDiscardDrawsForTheNextSeatWithoutOpeningAnIdleWindow() {
        McrRoundState before = orderedRound();
        McrTileInstance discard = McrTileInstance.fromId(0);

        McrRoundTransition transition = engine.transition(
                before, new McrRoundAction.Discard(Wind.EAST, discard));

        assertTrue(transition.accepted());
        McrRoundState after = transition.state();
        assertEquals(1, after.revision());
        assertEquals(McrRoundPhase.AWAITING_DISCARD, after.phase());
        assertEquals(Wind.SOUTH, after.currentSeat());
        assertEquals(14, after.hand(Wind.SOUTH).size());
        assertEquals(McrTileInstance.fromId(53), after.lastDraw().orElseThrow());
        assertEquals(McrDrawSource.NORMAL, after.lastDrawSource().orElseThrow());
        assertEquals(90, after.wall().remaining());
        assertEquals(McrDiscardStatus.UNCLAIMED, after.river(Wind.EAST).getLast().status());
    }

    @Test
    void exactChowClaimMovesOnlyTheIssuedTilesAndSkipsTheDraw() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 4, 12);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrTileInstance discard = McrTileInstance.fromId(16);

        McrRoundState reacting = engine.transition(
                before, new McrRoundAction.Discard(Wind.EAST, discard)).state();
        assertEquals(McrRoundPhase.REACTIONS, reacting.phase());
        McrReaction chow = reacting.reactionWindow().orElseThrow()
                .legalOptions(Wind.SOUTH).stream()
                .filter(option -> option.type() == McrReactionType.CHOW)
                .filter(option -> option.concealedTiles().contains(McrTileInstance.fromId(12)))
                .filter(option -> option.concealedTiles().contains(McrTileInstance.fromId(20)))
                .findFirst()
                .orElseThrow();

        McrRoundTransition transition = engine.transition(reacting, new McrRoundAction.React(chow));

        assertTrue(transition.accepted());
        McrRoundState after = transition.state();
        assertEquals(2, after.revision());
        assertEquals(McrRoundPhase.AWAITING_DISCARD, after.phase());
        assertEquals(Wind.SOUTH, after.currentSeat());
        assertEquals(91, after.wall().remaining());
        assertTrue(after.lastDraw().isEmpty());
        assertEquals(1, after.melds(Wind.SOUTH).size());
        assertEquals(McrMeldOrigin.CHOW, after.melds(Wind.SOUTH).getFirst().origin());
        assertEquals(McrDiscardStatus.MELD_CLAIMED, after.river(Wind.EAST).getLast().status());
    }

    @Test
    void directKongDrawsFromBackAndFlowerChainCancelsAfterKongSource() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 0, 4);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrTileInstance discard = McrTileInstance.fromId(4);

        McrRoundState reacting = engine.transition(
                before, new McrRoundAction.Discard(Wind.EAST, discard)).state();
        McrReaction kong = reacting.reactionWindow().orElseThrow()
                .legalOptions(Wind.SOUTH).stream()
                .filter(option -> option.type() == McrReactionType.KONG)
                .findFirst()
                .orElseThrow();
        McrRoundState after = engine.transition(
                reacting, new McrRoundAction.React(kong)).state();

        assertEquals(McrRoundPhase.AWAITING_DISCARD, after.phase());
        assertEquals(Wind.SOUTH, after.currentSeat());
        assertEquals(8, after.flowers(Wind.SOUTH).size());
        assertEquals(McrTileInstance.fromId(135), after.lastDraw().orElseThrow());
        assertEquals(McrDrawSource.FLOWER_REPLACEMENT, after.lastDrawSource().orElseThrow());
        assertEquals(82, after.wall().remaining());
        assertEquals(McrMeldOrigin.DIRECT_KONG, after.melds(Wind.SOUTH).getFirst().origin());
    }

    @Test
    void issuedHuEndsTheHandWithEngineBoundZeroSumPayment() {
        McrRoundState before = southPureStraightWait();
        McrTileInstance winningDiscard = McrTileInstance.fromId(32);

        McrRoundState reacting = engine.transition(
                before, new McrRoundAction.Discard(Wind.EAST, winningDiscard)).state();
        McrReaction hu = reacting.reactionWindow().orElseThrow()
                .legalOptions(Wind.SOUTH).stream()
                .filter(option -> option.type() == McrReactionType.HU)
                .findFirst()
                .orElseThrow();
        McrRoundTransition claimed = engine.transition(reacting, new McrRoundAction.React(hu));
        McrRoundState ended = claimed.state();
        if (ended.phase() == McrRoundPhase.REACTIONS) {
            ended = engine.transition(ended, new McrRoundAction.CloseReactions()).state();
        }

        assertEquals(McrRoundPhase.ENDED, ended.phase());
        McrRoundOutcome.Win outcome = assertInstanceOf(
                McrRoundOutcome.Win.class, ended.outcome().orElseThrow());
        assertEquals(Wind.SOUTH, outcome.winner());
        assertEquals(Wind.EAST, outcome.discarder());
        assertTrue(outcome.evaluation().legalWin());
        assertEquals(0, outcome.payment().deltas().values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(McrDiscardStatus.WIN_CLAIMED, ended.river(Wind.EAST).getLast().status());
    }

    @Test
    void dealerCanWinBeforeTheFirstDiscardUsingTheTrackedFinalDealTile() {
        McrRoundState before = eastPureStraightInitialWin();

        McrRoundTransition transition = engine.transition(
                before, new McrRoundAction.SelfDrawWin(Wind.EAST));

        assertTrue(transition.accepted());
        McrRoundOutcome.Win outcome = assertInstanceOf(
                McrRoundOutcome.Win.class, transition.state().outcome().orElseThrow());
        assertEquals(Wind.EAST, outcome.winner());
        assertEquals(WinMethod.SELF_DRAW, outcome.evaluation().winMethod());
        assertEquals(McrDrawSource.DEAL, transition.state().lastDrawSource().orElseThrow());
        assertEquals(0, outcome.payment().deltas().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void concealedKongUsesExactCopiesAndBackWallFlowerReplacement() {
        McrRoundState before = orderedRound();
        List<McrTileInstance> kongTiles = List.of(
                McrTileInstance.fromId(0),
                McrTileInstance.fromId(1),
                McrTileInstance.fromId(2),
                McrTileInstance.fromId(3));

        McrRoundTransition transition = engine.transition(
                before, new McrRoundAction.ConcealedKong(Wind.EAST, kongTiles));

        assertTrue(transition.accepted());
        McrRoundState after = transition.state();
        assertEquals(McrMeldOrigin.CONCEALED_KONG, after.melds(Wind.EAST).getFirst().origin());
        assertEquals(8, after.flowers(Wind.EAST).size());
        assertEquals(McrTileInstance.fromId(135), after.lastDraw().orElseThrow());
        assertEquals(McrDrawSource.FLOWER_REPLACEMENT, after.lastDrawSource().orElseThrow());
        assertEquals(82, after.wall().remaining());
    }

    @Test
    void selfDrawAndKongRejectWithoutChangingStateWhenNotLegal() {
        McrRoundState state = orderedRound();
        McrRoundTransition falseHu = engine.transition(
                state, new McrRoundAction.SelfDrawWin(Wind.EAST));
        assertSame(state, falseHu.state());
        assertEquals(List.of(McrRoundViolation.WIN_NOT_LEGAL), falseHu.violations());

        McrRoundTransition malformedKong = engine.transition(
                state, new McrRoundAction.ConcealedKong(
                        Wind.EAST, List.of(state.hand(Wind.EAST).getFirst())));
        assertSame(state, malformedKong.state());
        assertEquals(List.of(McrRoundViolation.KONG_NOT_LEGAL), malformedKong.violations());
    }

    @Test
    void rejectedAndStaleActionsRetainTheExactInputSnapshot() {
        McrRoundState state = orderedRound();
        McrRoundTransition wrongSeat = engine.transition(
                state, new McrRoundAction.Discard(Wind.SOUTH, state.hand(Wind.SOUTH).getFirst()));
        assertSame(state, wrongSeat.state());
        assertEquals(List.of(McrRoundViolation.NOT_CURRENT_SEAT), wrongSeat.violations());

        McrRoundTransition missingTile = engine.transition(
                state, new McrRoundAction.Discard(Wind.EAST, state.hand(Wind.SOUTH).getFirst()));
        assertSame(state, missingTile.state());
        assertEquals(List.of(McrRoundViolation.TILE_NOT_OWNED), missingTile.violations());

        McrRoundTransition wrongPhase = engine.transition(state, new McrRoundAction.CloseReactions());
        assertSame(state, wrongPhase.state());
        assertEquals(List.of(McrRoundViolation.WRONG_PHASE), wrongPhase.violations());
    }

    private static McrRoundState orderedRound() {
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(McrTileInstance.fullSet())), Wind.EAST);
    }

    private static McrRoundState southPureStraightWait() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 0, 32);
        int[] southPositions = {4, 5, 6, 7, 20, 21, 22, 23, 36, 37, 38, 39, 49};
        int[] southTiles = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int i = 0; i < southPositions.length; i++) {
            place(order, southPositions[i], southTiles[i]);
        }
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static McrRoundState eastPureStraightInitialWin() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        int[] eastPositions = {0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48};
        int[] concealedBeforeWin = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int i = 0; i < eastPositions.length; i++) {
            place(order, eastPositions[i], concealedBeforeWin[i]);
        }
        place(order, 52, 32);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static void place(ArrayList<McrTileInstance> order, int position, int tileId) {
        int current = -1;
        for (int index = position; index < order.size(); index++) {
            if (order.get(index).id() == tileId) {
                current = index;
                break;
            }
        }
        if (current < 0) {
            for (int index = 0; index < position; index++) {
                if (order.get(index).id() == tileId) {
                    current = index;
                    break;
                }
            }
        }
        McrTileInstance displaced = order.get(position);
        order.set(position, order.get(current));
        order.set(current, displaced);
    }
}
