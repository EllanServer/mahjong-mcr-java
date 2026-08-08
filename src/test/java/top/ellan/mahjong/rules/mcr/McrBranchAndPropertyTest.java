package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrBranchAndPropertyTest {
    private final McrRulesEngine engine = new StandardMcrRulesEngine();

    @Test
    void overallAttributeBranchAppliesHighFanExclusions() {
        WinEvaluation allGreen = discard(
                tiles("T2", "T3", "T4", "T2", "T3", "T4", "T6", "T6", "T6", "T8", "T8", "T8", "J2"),
                List.of(), "J2", Set.of());
        assertTrue(allGreen.hasFan(Fan.LVYISE));
        assertFalse(allGreen.hasFan(Fan.HUNYISE), "all green excludes half flush");

        WinEvaluation pure = discard(
                tiles("W1", "W2", "W3", "W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "W9", "W5"),
                List.of(), "W5", Set.of());
        assertTrue(pure.hasFan(Fan.QINGYISE));
        assertFalse(pure.hasFan(Fan.WUZI), "full flush excludes no-honors");
    }

    @Test
    void kongBranchKeepsDoubleConcealedKongAndExcludesDoubleConcealedPung() {
        WinEvaluation result = discard(
                tiles("W2", "W3", "W4", "B2", "B3", "B4", "T5"),
                List.of(Meld.concealedKong(Tile.M1), Meld.concealedKong(Tile.P9)), "T5", Set.of());
        assertTrue(result.hasFan(Fan.SHUANGANGANG));
        assertFalse(result.hasFan(Fan.SHUANGANKE));
    }

    @Test
    void associatedCombinationBranchExcludesContainedLowerFans() {
        WinEvaluation bigFour = discard(
                tiles("F1", "F1", "F1", "F2", "F2", "F2", "F3", "F3", "F3", "F4", "F4", "F4", "J1"),
                List.of(), "J1", Set.of());
        assertTrue(bigFour.hasFan(Fan.DASIXI));
        assertFalse(bigFour.hasFan(Fan.PENGPENGHU));
        assertFalse(bigFour.hasFan(Fan.QUANFENGKE));
        assertFalse(bigFour.hasFan(Fan.MENFENGKE));
    }

    @Test
    void singleGroupBranchDragonAwardsAreExcludedByBigThreeDragons() {
        WinEvaluation bigDragons = discard(
                tiles("J1", "J1", "J1", "J2", "J2", "J2", "J3", "J3", "J3", "W2", "W3", "W4", "B9"),
                List.of(), "B9", Set.of());
        assertTrue(bigDragons.hasFan(Fan.DASANYUAN));
        assertFalse(bigDragons.hasFan(Fan.JIANKE));
    }

    @Test
    void winModeBranchAppliesAfterKongAndRobbingKongExclusions() {
        TileCounts hand = tiles("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6");
        List<Meld> afterKongMelds = List.of(Meld.openKong(Tile.S5));

        WinEvaluation afterKong = engine.evaluate(new WinInput(hand, afterKongMelds, Tile.P6,
                new WinContext(Wind.EAST, Wind.EAST, WinMethod.SELF_DRAW,
                        Set.of(WinFlag.AFTER_KONG), List.of())));
        assertTrue(afterKong.hasFan(Fan.GANGSHANGKAIHUA));
        assertFalse(afterKong.hasFan(Fan.ZIMO));

        TileCounts robbingHand = tiles("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B7", "B7");
        WinEvaluation robbing = discard(robbingHand, List.of(Meld.chow(Tile.S5)), "B6",
                Set.of(WinFlag.ROBBING_KONG, WinFlag.LAST_OF_KIND));
        assertTrue(robbing.hasFan(Fan.QIANGGANGHU));
        assertFalse(robbing.hasFan(Fan.HUJUEZHANG));
    }

    @Test
    void tileOrderPermutationDoesNotChangeEvaluation() {
        List<Tile> source = parsed("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8",
                "B1", "B1", "B1", "T2", "T2");
        WinEvaluation expected = discard(TileCounts.of(source), List.of(), "W9", Set.of());
        Random random = new Random(0x4d43524cL);
        for (int iteration = 0; iteration < 250; iteration++) {
            List<Tile> shuffled = new ArrayList<>(source);
            Collections.shuffle(shuffled, random);
            WinEvaluation actual = discard(TileCounts.of(shuffled), List.of(), "W9", Set.of());
            assertEquals(expected.qualifyingFan(), actual.qualifyingFan());
            assertEquals(expected.awards(), actual.awards());
            assertEquals(expected.internalAwards(), actual.internalAwards());
        }
    }

    @Test
    void paymentIsZeroSumForEveryWinnerAndDiscarderCombination() {
        TileCounts hand = tiles("F1", "F1", "F1", "F2", "F2", "F2", "F3", "F3", "F3",
                "F4", "F4", "F4", "J1");
        WinEvaluation discard = discard(hand, List.of(), "J1", Set.of());
        WinEvaluation selfDraw = engine.evaluate(new WinInput(hand, List.of(), Tile.WHITE_DRAGON,
                WinContext.standard(Wind.EAST, Wind.EAST, WinMethod.SELF_DRAW)));

        for (Wind winner : Wind.values()) {
            Payment selfDrawPayment = McrPayments.settle(selfDraw, winner, null);
            assertEquals(0, sum(selfDrawPayment));
            for (Wind discarder : Wind.values()) {
                if (discarder == winner) continue;
                Payment discardPayment = McrPayments.settle(discard, winner, discarder);
                assertEquals(0, sum(discardPayment));
            }
        }
    }

    @Test
    void invalidAndUnsupportedStatesFailClosed() {
        assertThrows(IllegalArgumentException.class, () -> TileCounts.of(
                Tile.M1, Tile.M1, Tile.M1, Tile.M1, Tile.M1));
        assertThrows(IllegalArgumentException.class, () -> engine.evaluate(null));

        WinEvaluation notWinning = discard(
                tiles("W1", "W1", "W1", "W2", "W2", "W2", "W3", "W3", "W3", "W4", "W4", "W4", "F1"),
                List.of(), "F2", Set.of());
        assertEquals(EvaluationStatus.NOT_WINNING, notWinning.status());
        assertFalse(notWinning.legalWin());
        assertThrows(IllegalArgumentException.class, () -> McrPayments.settle(notWinning, Wind.EAST, Wind.SOUTH));

        WinEvaluation unsupported = new WinEvaluation(EvaluationStatus.UNSUPPORTED, Tile.M1,
                WinMethod.DISCARD, 0, 0, 0, List.of(), List.of(),
                List.of("unsupported rule profile"));
        WinEvaluation invalid = new WinEvaluation(EvaluationStatus.INVALID_INPUT, Tile.M1,
                WinMethod.DISCARD, 0, 0, 0, List.of(), List.of(),
                List.of("invalid input"));
        assertFalse(unsupported.legalWin());
        assertFalse(invalid.legalWin());
        assertThrows(IllegalArgumentException.class, () -> McrPayments.settle(unsupported, Wind.EAST, Wind.SOUTH));
        assertThrows(IllegalArgumentException.class, () -> McrPayments.settle(invalid, Wind.EAST, Wind.SOUTH));
    }

    private WinEvaluation discard(TileCounts concealed, List<Meld> melds, String winning, Set<WinFlag> flags) {
        return engine.evaluate(new WinInput(concealed, melds, Tile.parse(winning),
                new WinContext(Wind.EAST, Wind.EAST, WinMethod.DISCARD, flags, List.of())));
    }

    private static int sum(Payment payment) {
        int total = 0;
        for (int delta : payment.deltas().values()) total += delta;
        return total;
    }

    private static TileCounts tiles(String... codes) { return TileCounts.parse(codes); }

    private static List<Tile> parsed(String... codes) {
        List<Tile> result = new ArrayList<>(codes.length);
        for (String code : codes) result.add(Tile.parse(code));
        return result;
    }
}
