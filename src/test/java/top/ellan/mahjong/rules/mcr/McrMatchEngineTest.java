package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrMatchEngineTest {
    private final McrMatchEngine engine = new McrMatchEngine();

    @Test
    void aCompletedHandRotatesDealerRegardlessOfWinnerAndPreservesFixedPlayers() {
        McrMatchState before = matchWithWinningEast(new McrMatchConfig(2));
        McrMatchTransition won = engine.transition(
                before,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST)));

        assertTrue(won.accepted());
        McrMatchState boundary = won.state();
        assertEquals(McrMatchPhase.BETWEEN_HANDS, boundary.phase());
        assertEquals(1, boundary.handsCompleted());
        assertEquals(Wind.EAST, boundary.handResults().getFirst().dealerInitialSeat());
        assertEquals(boundary.handResults().getFirst().cumulativeAfter(), boundary.cumulativeScore());

        McrMatchTransition started = engine.transition(
                boundary, new McrMatchAction.StartNextHand());
        assertTrue(started.accepted());
        McrMatchState second = started.state();
        assertEquals(2, second.currentHandNumber());
        assertEquals(Wind.SOUTH, second.playerAt(Wind.EAST));
        assertEquals(Wind.EAST, second.playerAt(Wind.NORTH));
        assertEquals(Wind.EAST, second.logicalSeatOf(Wind.SOUTH));
        assertEquals(Wind.EAST, second.roundState().roundWind());
    }

    @Test
    void fixedHandLimitEndsTheMatchAndCumulativePaymentRemainsZeroSum() {
        McrMatchState before = matchWithWinningEast(new McrMatchConfig(1));
        McrMatchTransition won = engine.transition(
                before,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST)));

        assertTrue(won.accepted());
        assertEquals(McrMatchPhase.ENDED, won.state().phase());
        assertEquals(0, won.state().cumulativeScore().values().stream()
                .mapToInt(Integer::intValue).sum());
        assertTrue(won.events().stream().anyMatch(McrMatchEvent.MatchEnded.class::isInstance));
        var result = McrMatchResults.from(new McrProviderState(
                won.state(),
                List.of(
                        new top.ellan.mahjong.spi.PlayerId(new UUID(0, 1)),
                        new top.ellan.mahjong.spi.PlayerId(new UUID(0, 2)),
                        new top.ellan.mahjong.spi.PlayerId(new UUID(0, 3)),
                        new top.ellan.mahjong.spi.PlayerId(new UUID(0, 4)))))
                .orElseThrow();
        assertEquals("mcr.green-book.raw-score.v1", result.rankSystem());
        assertEquals(4, result.players().size());
        McrMatchTransition rejected = engine.transition(
                won.state(), new McrMatchAction.StartNextHand());
        assertSame(won.state(), rejected.state());
        assertEquals(List.of(McrMatchViolation.MATCH_ENDED), rejected.matchViolations());
    }

    @Test
    void secondDealerPaymentIsCreditedToTheFixedSouthPlayer() {
        long matchSeed = 0x7365636f6e642d64L;
        McrHandResult first = new McrHandResult(
                1,
                McrMatchState.deriveHandSeed(matchSeed, 0),
                Wind.EAST,
                Wind.EAST,
                new McrRoundOutcome.ExhaustiveDraw(),
                McrMatchState.zeroScores(),
                McrMatchState.zeroScores(),
                1);
        McrMatchState secondHand = new McrMatchState(
                new McrMatchConfig(2),
                matchSeed,
                3,
                McrMatchPhase.HAND_ACTIVE,
                1,
                McrMatchState.deriveHandSeed(matchSeed, 1),
                eastPureStraightInitialWin(),
                McrMatchState.zeroScores(),
                List.of(first));

        McrMatchState ended = engine.transition(
                secondHand,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST))).state();

        McrHandResult second = ended.handResults().getLast();
        assertEquals(Wind.SOUTH, second.dealerInitialSeat());
        assertTrue(second.deltasByInitialSeat().get(Wind.SOUTH) > 0);
        assertEquals(second.deltasByInitialSeat(), ended.cumulativeScore());
    }

    @Test
    void tournamentDeadlineCanEndOnlyBetweenHands() {
        McrMatchState active = matchWithWinningEast(McrMatchConfig.standard());
        McrMatchTransition premature = engine.transition(active, new McrMatchAction.EndMatch());
        assertSame(active, premature.state());
        assertEquals(List.of(McrMatchViolation.WRONG_MATCH_PHASE), premature.matchViolations());

        McrMatchState boundary = engine.transition(
                active,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST))).state();
        McrMatchTransition ended = engine.transition(boundary, new McrMatchAction.EndMatch());
        assertTrue(ended.accepted());
        assertEquals(McrMatchPhase.ENDED, ended.state().phase());
        assertEquals(1, ended.state().handsCompleted());
    }

    @Test
    void prevalentWindAdvancesAfterEachFourDealerRotations() {
        assertEquals(Wind.EAST, McrMatchState.roundWindForHand(1));
        assertEquals(Wind.EAST, McrMatchState.roundWindForHand(4));
        assertEquals(Wind.SOUTH, McrMatchState.roundWindForHand(5));
        assertEquals(Wind.WEST, McrMatchState.roundWindForHand(9));
        assertEquals(Wind.NORTH, McrMatchState.roundWindForHand(16));
    }

    @Test
    void rejectedRoundActionRetainsTheExactMatchObject() {
        McrMatchState state = matchWithWinningEast(McrMatchConfig.standard());
        McrMatchTransition rejected = engine.transition(
                state,
                new McrMatchAction.Play(new McrRoundAction.Discard(
                        Wind.SOUTH, state.roundState().hand(Wind.SOUTH).getFirst())));
        assertSame(state, rejected.state());
        assertEquals(List.of(McrRoundViolation.NOT_CURRENT_SEAT), rejected.roundViolations());
    }

    private static McrMatchState matchWithWinningEast(McrMatchConfig config) {
        long matchSeed = 0x4d43522d6d617463L;
        return new McrMatchState(
                config,
                matchSeed,
                0,
                McrMatchPhase.HAND_ACTIVE,
                0,
                McrMatchState.deriveHandSeed(matchSeed, 0),
                eastPureStraightInitialWin(),
                McrMatchState.zeroScores(),
                List.of());
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
