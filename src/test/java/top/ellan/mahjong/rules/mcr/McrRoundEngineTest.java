package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    void addedKongUpgradesOnlyAfterTheHuWindowPasses() {
        McrRoundState before = addedKongReady(false);
        McrTileInstance fourth = McrTileInstance.fromId(7);

        McrRoundTransition transition = engine.transition(
                before, new McrRoundAction.AddedKong(Wind.SOUTH, fourth));

        assertTrue(transition.accepted());
        McrRoundState after = transition.state();
        assertEquals(McrRoundPhase.AWAITING_DISCARD, after.phase());
        assertEquals(McrMeldOrigin.ADDED_KONG, after.melds(Wind.SOUTH).getFirst().origin());
        assertTrue(after.hand(Wind.SOUTH).stream().noneMatch(tile -> tile.equals(fourth)));
        assertEquals(8, after.flowers(Wind.SOUTH).size());
        assertEquals(McrDrawSource.FLOWER_REPLACEMENT, after.lastDrawSource().orElseThrow());
    }

    @Test
    void robbedAddedKongRemovesFourthTileAndLeavesOriginalPung() {
        McrRoundState before = addedKongReady(true);
        McrTileInstance fourth = McrTileInstance.fromId(7);

        McrRoundState reacting = engine.transition(
                before, new McrRoundAction.AddedKong(Wind.SOUTH, fourth)).state();
        assertEquals(McrRoundPhase.REACTIONS, reacting.phase());
        assertEquals(McrReactionOrigin.ADDED_KONG,
                reacting.reactionWindow().orElseThrow().origin());
        McrReaction hu = reacting.reactionWindow().orElseThrow()
                .legalOptions(Wind.WEST).stream()
                .filter(option -> option.type() == McrReactionType.HU)
                .findFirst()
                .orElseThrow();
        McrRoundState ended = engine.transition(
                reacting, new McrRoundAction.React(hu)).state();
        if (ended.phase() == McrRoundPhase.REACTIONS) {
            ended = engine.transition(ended, new McrRoundAction.CloseReactions()).state();
        }

        assertEquals(McrRoundPhase.ENDED, ended.phase());
        assertEquals(McrMeldOrigin.PUNG, ended.melds(Wind.SOUTH).getFirst().origin());
        assertTrue(ended.hand(Wind.SOUTH).stream().noneMatch(tile -> tile.equals(fourth)));
        McrRobbedKongClaim claim = ended.robbedKongClaim().orElseThrow();
        assertEquals(Wind.SOUTH, claim.sourceSeat());
        assertEquals(Wind.WEST, claim.winner());
        assertEquals(fourth, claim.tile());
        McrRoundOutcome.Win outcome = assertInstanceOf(
                McrRoundOutcome.Win.class, ended.outcome().orElseThrow());
        assertTrue(outcome.evaluation().hasFan(Fan.QIANGGANGHU));
    }

    @Test
    void addedKongIsForbiddenInTheSameTurnAsPung() {
        McrRoundState claimed = pungClaimState();
        McrRoundTransition rejected = engine.transition(
                claimed,
                new McrRoundAction.AddedKong(Wind.SOUTH, McrTileInstance.fromId(7)));
        assertSame(claimed, rejected.state());
        assertEquals(List.of(McrRoundViolation.ADDED_KONG_NOT_LEGAL), rejected.violations());
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

    private McrRoundState pungClaimState() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 0, 4);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrRoundState reacting = engine.transition(
                before,
                new McrRoundAction.Discard(Wind.EAST, McrTileInstance.fromId(4))).state();
        McrReaction pung = reacting.reactionWindow().orElseThrow()
                .legalOptions(Wind.SOUTH).stream()
                .filter(option -> option.type() == McrReactionType.PUNG)
                .filter(option -> option.concealedTiles().contains(McrTileInstance.fromId(5)))
                .filter(option -> option.concealedTiles().contains(McrTileInstance.fromId(6)))
                .findFirst()
                .orElseThrow();
        return engine.transition(reacting, new McrRoundAction.React(pung)).state();
    }

    private McrRoundState addedKongReady(boolean robbable) {
        McrInitialDeal deal;
        if (robbable) {
            Map<Integer, Integer> fixed = new HashMap<>();
            fixed.put(0, 4);
            fixed.put(4, 5);
            fixed.put(5, 6);
            fixed.put(6, 7);
            int[] westPositions = {8, 9, 10, 11, 24, 25, 26, 27, 40, 41, 42, 43, 50};
            int[] westTiles = {0, 8, 72, 73, 74, 36, 37, 38, 108, 109, 110, 132, 133};
            for (int index = 0; index < westPositions.length; index++) {
                fixed.put(westPositions[index], westTiles[index]);
            }
            deal = McrInitialDealer.deal(McrWall.fromOrder(orderWith(fixed)));
        } else {
            ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
            place(order, 0, 4);
            deal = McrInitialDealer.deal(McrWall.fromOrder(order));
        }

        EnumMap<Wind, List<McrTileInstance>> hands = copySeats(deal.concealed());
        hands.put(Wind.EAST, without(hands.get(Wind.EAST), McrTileInstance.fromId(4)));
        List<McrTileInstance> south = without(hands.get(Wind.SOUTH), McrTileInstance.fromId(5));
        south = without(south, McrTileInstance.fromId(6));
        McrTileInstance southDiscard = south.stream()
                .filter(tile -> tile.id() != 7)
                .findFirst()
                .orElseThrow();
        south = without(south, southDiscard);

        McrWall.Draw draw = deal.wall().drawFront();
        south = with(south, draw.tile());
        hands.put(Wind.SOUTH, south);

        EnumMap<Wind, List<McrPhysicalMeld>> melds = emptySeats();
        McrPhysicalMeld pung = McrPhysicalMeld.pung(
                List.of(
                        McrTileInstance.fromId(4),
                        McrTileInstance.fromId(5),
                        McrTileInstance.fromId(6)),
                McrTileInstance.fromId(4),
                Wind.EAST);
        melds.put(Wind.SOUTH, List.of(pung));

        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = emptySeats();
        rivers.put(Wind.EAST, List.of(
                McrPhysicalDiscard.pending(Wind.EAST, McrTileInstance.fromId(4))
                        .claimedForMeld(Wind.SOUTH)));
        rivers.put(Wind.SOUTH, List.of(
                McrPhysicalDiscard.pending(Wind.SOUTH, southDiscard).unclaimed()));
        return new McrRoundState(
                5,
                Wind.EAST,
                Wind.SOUTH,
                McrRoundPhase.AWAITING_DISCARD,
                hands,
                deal.flowers(),
                melds,
                rivers,
                draw.remainingWall(),
                draw.tile(),
                McrDrawSource.NORMAL,
                null,
                null);
    }

    private static ArrayList<McrTileInstance> orderWith(Map<Integer, Integer> fixed) {
        HashSet<Integer> used = new HashSet<>(fixed.values());
        if (used.size() != fixed.size()) throw new IllegalArgumentException("fixed tile ids must be unique");
        ArrayList<McrTileInstance> remaining = new ArrayList<>();
        for (McrTileInstance tile : McrTileInstance.fullSet()) {
            if (!used.contains(tile.id())) remaining.add(tile);
        }
        ArrayList<McrTileInstance> result = new ArrayList<>(144);
        int remainingIndex = 0;
        for (int position = 0; position < 144; position++) {
            Integer tileId = fixed.get(position);
            result.add(tileId == null
                    ? remaining.get(remainingIndex++)
                    : McrTileInstance.fromId(tileId));
        }
        return result;
    }

    private static <T> EnumMap<Wind, List<T>> copySeats(Map<Wind, List<T>> source) {
        EnumMap<Wind, List<T>> result = new EnumMap<>(Wind.class);
        result.putAll(source);
        return result;
    }

    private static <T> EnumMap<Wind, List<T>> emptySeats() {
        EnumMap<Wind, List<T>> result = new EnumMap<>(Wind.class);
        for (Wind wind : Wind.values()) result.put(wind, List.of());
        return result;
    }

    private static List<McrTileInstance> without(
            List<McrTileInstance> source, McrTileInstance tile) {
        ArrayList<McrTileInstance> result = new ArrayList<>(source);
        if (!result.remove(tile)) throw new IllegalArgumentException("test tile is absent");
        return List.copyOf(result);
    }

    private static List<McrTileInstance> with(
            List<McrTileInstance> source, McrTileInstance tile) {
        ArrayList<McrTileInstance> result = new ArrayList<>(source);
        result.add(tile);
        result.sort(null);
        return List.copyOf(result);
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
