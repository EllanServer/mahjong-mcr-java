package top.ellan.mahjong.rules.mcr;

import java.util.List;

public record WaitEvaluation(List<WaitCandidate> waits) {
    public WaitEvaluation {
        waits = waits == null ? List.of() : List.copyOf(waits);
    }
}
