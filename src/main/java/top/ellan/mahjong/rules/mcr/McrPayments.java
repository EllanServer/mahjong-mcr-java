package top.ellan.mahjong.rules.mcr;

import java.util.EnumMap;

public final class McrPayments {
    public static final int BASIC_PAYMENT = 8;

    private McrPayments() {}

    public static Payment settle(WinEvaluation evaluation, Wind winner, Wind discarder) {
        if (evaluation == null || !evaluation.legalWin()) {
            throw new IllegalArgumentException("a legal MCR win evaluation is required");
        }
        if (winner == null) throw new IllegalArgumentException("winner is required");
        EnumMap<Wind, Integer> deltas = new EnumMap<>(Wind.class);
        for (Wind wind : Wind.values()) deltas.put(wind, 0);
        int fan = evaluation.totalFan();
        if (evaluation.winMethod() == WinMethod.SELF_DRAW) {
            if (discarder != null) throw new IllegalArgumentException("self draw has no discarder");
            int payment = Math.addExact(fan, BASIC_PAYMENT);
            for (Wind wind : Wind.values()) {
                if (wind != winner) deltas.put(wind, -payment);
            }
            deltas.put(winner, Math.multiplyExact(payment, 3));
        } else {
            if (discarder == null || discarder == winner) {
                throw new IllegalArgumentException("discard win requires a different discarder");
            }
            for (Wind wind : Wind.values()) {
                if (wind != winner) deltas.put(wind, -BASIC_PAYMENT);
            }
            deltas.put(discarder, -Math.addExact(fan, BASIC_PAYMENT));
            deltas.put(winner, Math.addExact(fan, 3 * BASIC_PAYMENT));
        }
        return new Payment(deltas);
    }
}
