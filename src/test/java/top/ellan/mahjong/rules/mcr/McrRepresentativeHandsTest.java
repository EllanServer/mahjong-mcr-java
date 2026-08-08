package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrRepresentativeHandsTest {
    private final McrRulesEngine engine = new StandardMcrRulesEngine();

    @Test
    void scoresSourceBackedRepresentativeHands() {
        assertFan(discard(
                tiles("F1", "F1", "F1", "F2", "F2", "F2", "F3", "F3", "F3", "F4", "F4", "F4", "J1"),
                "J1"), Fan.DASIXI);
        assertFan(discard(
                tiles("J1", "J1", "J1", "J2", "J2", "J2", "J3", "J3", "J3", "W2", "W3", "W4", "B9"),
                "B9"), Fan.DASANYUAN);
        assertFan(discard(
                tiles("W1", "W9", "T1", "T9", "B1", "B9", "F1", "F2", "F3", "F4", "J1", "J2", "J3"),
                "W1"), Fan.SHISANYAO);
        assertFan(discard(
                tiles("W1", "W1", "W2", "W2", "W3", "W3", "B4", "B4", "B5", "B5", "T6", "T6", "T7"),
                "T7"), Fan.QIDUI);
        assertFan(discard(
                tiles("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8", "B1", "B1", "B1", "T2", "T2"),
                "W9"), Fan.QINGLONG);
        assertFan(discard(
                tiles("T2", "T3", "T4", "T2", "T3", "T4", "T6", "T6", "T6", "T8", "T8", "T8", "J2"),
                "J2"), Fan.LVYISE);

        WinEvaluation selfDraw = engine.evaluate(new WinInput(
                tiles("W2", "W3", "W4", "B2", "B3", "B4", "T2", "T3", "T4", "W6", "W7", "W8", "B9"),
                List.of(), Tile.parse("B9"), context(WinMethod.SELF_DRAW, List.of())));
        assertTrue(selfDraw.hasFan(Fan.BUQIUREN));
    }

    @Test
    void mixedConcealedAndMeldedKongUsesInternalSixPointCombination() {
        WinEvaluation result = engine.evaluate(new WinInput(
                tiles("W2", "W3", "W4", "B2", "B3", "B4", "T5"),
                List.of(Meld.concealedKong(Tile.parse("W1")), Meld.openKong(Tile.parse("B9"))),
                Tile.parse("T5"), context(WinMethod.DISCARD, List.of())));

        assertTrue(result.winningShape());
        assertEquals(1, result.internalAwards().size());
        assertEquals(InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG,
                result.internalAwards().getFirst().combination());
        assertEquals(6, result.internalAwards().getFirst().points());
    }

    @Test
    void exhaustedSecondFormalWaitPreventsSingleWaitFan() {
        WinEvaluation result = engine.evaluate(new WinInput(
                tiles("W1", "W2", "W3", "W4", "W4", "W4", "W4", "W6", "W7", "W8", "B4", "B5", "B6"),
                List.of(), Tile.parse("W1"), context(WinMethod.SELF_DRAW, List.of())));

        assertTrue(result.winningShape());
        assertEquals(8, result.totalFan());
        assertFalse(result.hasFan(Fan.DANDIAOJIANG));
    }

    @Test
    void flowersDoNotMeetTheEightFanMinimum() {
        List<Meld> melds = List.of(Meld.chow(Tile.parse("T2")));
        List<Tile> flowers = List.of(Tile.PLUM, Tile.ORCHID, Tile.BAMBOO_FLOWER, Tile.CHRYSANTHEMUM,
                Tile.SPRING, Tile.SUMMER, Tile.AUTUMN, Tile.WINTER);
        WinEvaluation result = engine.evaluate(new WinInput(
                tiles("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6"),
                melds, Tile.parse("B6"), context(WinMethod.DISCARD, flowers)));

        assertTrue(result.winningShape());
        assertEquals(8, result.flowerFan());
        assertTrue(result.totalFan() >= 8);
        assertTrue(result.qualifyingFan() < 8);
        assertFalse(result.legalWin());

        WaitEvaluation waits = engine.waits(new WaitInput(
                tiles("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6"),
                melds, Wind.EAST, Wind.EAST, Set.of(), flowers));
        assertFalse(hasWait(waits, Tile.parse("B6")));
    }

    @Test
    void selfDrawMayRaiseASevenFanWaitToEight() {
        List<Meld> melds = List.of(Meld.chow(Tile.parse("T5")));
        TileCounts hand = tiles("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6");

        WaitEvaluation waits = engine.waits(new WaitInput(hand, melds, Wind.EAST, Wind.EAST, Set.of(), List.of()));
        WaitCandidate candidate = waits.waits().stream()
                .filter(wait -> wait.tile() == Tile.parse("B6"))
                .findFirst().orElseThrow();
        assertTrue(candidate.discardEvaluation().isEmpty());
        assertTrue(candidate.selfDrawEvaluation().isPresent());
        assertEquals(8, candidate.selfDrawEvaluation().orElseThrow().qualifyingFan());
        assertTrue(candidate.selfDrawEvaluation().orElseThrow().hasFan(Fan.ZIMO));
    }

    private WinEvaluation discard(TileCounts concealed, String winning) {
        return engine.evaluate(new WinInput(concealed, List.of(), Tile.parse(winning),
                context(WinMethod.DISCARD, List.of())));
    }

    private static WinContext context(WinMethod method, List<Tile> flowers) {
        return new WinContext(Wind.EAST, Wind.EAST, method, Set.of(), flowers);
    }

    private static TileCounts tiles(String... codes) { return TileCounts.parse(codes); }

    private static void assertFan(WinEvaluation result, Fan fan) {
        assertTrue(result.winningShape(), result.violations().toString());
        assertTrue(result.hasFan(fan), () -> "expected " + fan + " in " + result.awards());
    }

    private static boolean hasWait(WaitEvaluation evaluation, Tile tile) {
        for (WaitCandidate wait : evaluation.waits()) if (wait.tile() == tile) return true;
        return false;
    }
}
