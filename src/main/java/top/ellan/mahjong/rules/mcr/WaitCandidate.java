package top.ellan.mahjong.rules.mcr;

import java.util.Optional;

public record WaitCandidate(
        Tile tile,
        Optional<WinEvaluation> discardEvaluation,
        Optional<WinEvaluation> selfDrawEvaluation) {
    public WaitCandidate {
        if (tile == null) throw new IllegalArgumentException("tile is required");
        discardEvaluation = discardEvaluation == null ? Optional.empty() : discardEvaluation;
        selfDrawEvaluation = selfDrawEvaluation == null ? Optional.empty() : selfDrawEvaluation;
        if (discardEvaluation.isEmpty() && selfDrawEvaluation.isEmpty()) {
            throw new IllegalArgumentException("at least one legal win method is required");
        }
    }
}
