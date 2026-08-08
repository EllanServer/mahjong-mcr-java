package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stateless, thread-safe MCR evaluator. */
public final class StandardMcrRulesEngine implements McrRulesEngine {
    @Override
    public WinEvaluation evaluate(WinInput input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        return FanCalculator.evaluate(input);
    }

    @Override
    public WaitEvaluation waits(WaitInput input) {
        if (input == null) throw new IllegalArgumentException("input is required");
        int[] physical = physicalCounts(input.concealed(), input.melds());
        List<WaitCandidate> waits = new ArrayList<>();
        for (int i = 0; i < Tile.STANDARD_KIND_COUNT; i++) {
            if (physical[i] >= 4) continue;
            Tile tile = Tile.standard(i);
            if (!isWinningShape(input.concealed(), input.melds(), tile)) continue;

            WinContext discardContext = new WinContext(input.seatWind(), input.roundWind(),
                    WinMethod.DISCARD, input.flags(), input.flowers());
            WinContext selfDrawContext = new WinContext(input.seatWind(), input.roundWind(),
                    WinMethod.SELF_DRAW, input.flags(), input.flowers());
            WinEvaluation discard = evaluate(new WinInput(input.concealed(), input.melds(), tile, discardContext));
            WinEvaluation selfDraw = evaluate(new WinInput(input.concealed(), input.melds(), tile, selfDrawContext));
            Optional<WinEvaluation> legalDiscard = discard.legalWin() ? Optional.of(discard) : Optional.empty();
            Optional<WinEvaluation> legalSelfDraw = selfDraw.legalWin() ? Optional.of(selfDraw) : Optional.empty();
            if (legalDiscard.isPresent() || legalSelfDraw.isPresent()) {
                waits.add(new WaitCandidate(tile, legalDiscard, legalSelfDraw));
            }
        }
        return new WaitEvaluation(waits);
    }

    @Override
    public boolean isWinningShape(TileCounts concealedBeforeWin, List<Meld> melds, Tile winningTile) {
        if (concealedBeforeWin == null || melds == null || winningTile == null || !winningTile.isStandard()) {
            throw new IllegalArgumentException("concealed tiles, melds and standard winning tile are required");
        }
        List<Meld> normalizedMelds = InputValidation.melds(melds);
        InputValidation.structuralCount(concealedBeforeWin, normalizedMelds);
        try {
            InputValidation.physicalCounts(concealedBeforeWin, normalizedMelds, winningTile);
        } catch (IllegalArgumentException impossibleTile) {
            return false;
        }
        byte[] after = concealedBeforeWin.copyArray();
        after[winningTile.index()]++;
        return ShapeSolver.isWinning(after, normalizedMelds);
    }

    private static int[] physicalCounts(TileCounts concealed, List<Meld> melds) {
        return InputValidation.physicalCounts(concealed, melds, null);
    }
}
