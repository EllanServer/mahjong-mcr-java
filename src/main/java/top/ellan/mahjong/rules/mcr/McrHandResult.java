package top.ellan.mahjong.rules.mcr;

import java.util.Map;

/** Canonical completed-hand result keyed by each player's initial seat. */
public record McrHandResult(
        int handNumber,
        long handSeed,
        Wind roundWind,
        Wind dealerInitialSeat,
        McrRoundOutcome outcome,
        Map<Wind, Integer> deltasByInitialSeat,
        Map<Wind, Integer> cumulativeAfter,
        long roundRevision) {

    public McrHandResult {
        if (handNumber < 1 || handNumber > McrMatchConfig.MAX_HAND_LIMIT
                || roundWind == null || dealerInitialSeat == null || outcome == null
                || roundRevision < 0) {
            throw new IllegalArgumentException("invalid MCR hand-result metadata");
        }
        deltasByInitialSeat = seats(deltasByInitialSeat, true, "hand deltas");
        cumulativeAfter = seats(cumulativeAfter, true, "cumulative score");
        java.util.EnumMap<Wind, Integer> expected = new java.util.EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) expected.put(seat, 0);
        if (outcome instanceof McrRoundOutcome.Win win) {
            int offset = (handNumber - 1) % Wind.values().length;
            for (Wind logicalSeat : Wind.values()) {
                Wind initialSeat = Wind.values()[
                        (logicalSeat.ordinal() + offset) % Wind.values().length];
                expected.put(initialSeat, win.payment().delta(logicalSeat));
            }
        }
        if (!expected.equals(deltasByInitialSeat)) {
            throw new IllegalArgumentException("hand deltas diverged from the bound outcome");
        }
    }

    private static Map<Wind, Integer> seats(
            Map<Wind, Integer> source, boolean zeroSum, String name) {
        if (source == null || source.size() != Wind.values().length) {
            throw new IllegalArgumentException(name + " must contain four seats");
        }
        java.util.EnumMap<Wind, Integer> result = new java.util.EnumMap<>(Wind.class);
        int sum = 0;
        for (Wind seat : Wind.values()) {
            Integer value = source.get(seat);
            if (value == null) throw new IllegalArgumentException(name + " omitted " + seat);
            result.put(seat, value);
            sum = Math.addExact(sum, value);
        }
        if (zeroSum && sum != 0) throw new IllegalArgumentException(name + " must be zero-sum");
        return java.util.Collections.unmodifiableMap(result);
    }
}
