package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class McrPaymentTest {
    private final McrRulesEngine engine = new StandardMcrRulesEngine();
    private final TileCounts bigFourWinds = TileCounts.parse(
            "F1", "F1", "F1", "F2", "F2", "F2", "F3", "F3", "F3", "F4", "F4", "F4", "J1");

    @Test
    void discardPaymentIncludesBasicEightFromEveryOpponentAndIsZeroSum() {
        WinEvaluation evaluation = evaluate(WinMethod.DISCARD);
        Payment payment = McrPayments.settle(evaluation, Wind.EAST, Wind.SOUTH);

        assertEquals(evaluation.totalFan() + 24, payment.delta(Wind.EAST));
        assertEquals(-(evaluation.totalFan() + 8), payment.delta(Wind.SOUTH));
        assertEquals(-8, payment.delta(Wind.WEST));
        assertEquals(-8, payment.delta(Wind.NORTH));
        assertEquals(0, payment.deltas().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void selfDrawPaymentIsThreeTimesFanPlusEightAndIsZeroSum() {
        WinEvaluation evaluation = evaluate(WinMethod.SELF_DRAW);
        Payment payment = McrPayments.settle(evaluation, Wind.EAST, null);
        int each = evaluation.totalFan() + 8;

        assertEquals(each * 3, payment.delta(Wind.EAST));
        assertEquals(-each, payment.delta(Wind.SOUTH));
        assertEquals(-each, payment.delta(Wind.WEST));
        assertEquals(-each, payment.delta(Wind.NORTH));
        assertEquals(0, payment.deltas().values().stream().mapToInt(Integer::intValue).sum());
    }

    private WinEvaluation evaluate(WinMethod method) {
        return engine.evaluate(new WinInput(bigFourWinds, List.of(), Tile.parse("J1"),
                new WinContext(Wind.EAST, Wind.EAST, method, Set.of(), List.of())));
    }
}
