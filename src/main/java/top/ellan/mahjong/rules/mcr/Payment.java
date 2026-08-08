package top.ellan.mahjong.rules.mcr;

import java.util.Map;

public record Payment(Map<Wind, Integer> deltas) {
    public Payment {
        deltas = Map.copyOf(deltas);
        if (deltas.size() != 4) throw new IllegalArgumentException("exactly four seat deltas are required");
        int total = 0;
        for (Wind wind : Wind.values()) {
            Integer delta = deltas.get(wind);
            if (delta == null) throw new IllegalArgumentException("missing seat delta for " + wind);
            total = Math.addExact(total, delta);
        }
        if (total != 0) throw new IllegalArgumentException("payment must be zero-sum");
    }

    public int delta(Wind wind) { return deltas.get(wind); }
}
