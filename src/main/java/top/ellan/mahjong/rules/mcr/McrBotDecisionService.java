package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Set;

/**
 * Ting-aware deterministic bot brain for MCR.
 *
 * <p>Every discard, claim and kong is scored by the ready state the hand would reach afterwards,
 * computed with the same authoritative {@link McrRulesEngine} the referee uses. MCR's 8-fan
 * minimum needs no separate handling because {@link McrRulesEngine#waits(WaitInput)} only reports
 * waits that are legal wins.</p>
 *
 * <p>The score is intentionally ordinal, not a probability: a hand that is ready at all outranks
 * one that is not, then higher best fan, then more waits, then higher total fan. Ties fall through
 * to a shape preference so the same state always produces the same decision.</p>
 */
final class McrBotDecisionService {
    /** Any ready hand outranks every hand that is not ready. */
    private static final long READY_BASE = 1_000_000L;

    private final McrRulesEngine engine;

    McrBotDecisionService() {
        this(new StandardMcrRulesEngine());
    }

    McrBotDecisionService(McrRulesEngine engine) {
        if (engine == null) {
            throw new IllegalArgumentException("rules engine is required");
        }
        this.engine = engine;
    }

    /**
     * Scores a structurally complete pre-win hand.
     *
     * @return {@code 0} when the hand has no legal wait, otherwise a strictly positive rank
     */
    long readyScore(TileCounts concealed, List<Meld> melds, Wind seatWind, Wind roundWind,
                    List<Tile> flowers) {
        if (concealed == null || melds == null || concealed.total() != 13 - 3 * melds.size()) {
            return 0L;
        }
        WaitEvaluation evaluation;
        try {
            evaluation = engine.waits(
                    new WaitInput(concealed, melds, seatWind, roundWind, Set.of(), flowers));
        } catch (IllegalArgumentException impossibleShape) {
            return 0L;
        }
        long qualifiedWaits = 0L;
        long bestFan = 0L;
        long totalFan = 0L;
        for (WaitCandidate candidate : evaluation.waits()) {
            int fan = candidateFan(candidate);
            qualifiedWaits++;
            bestFan = Math.max(bestFan, fan);
            totalFan += fan;
        }
        if (qualifiedWaits == 0L) {
            return 0L;
        }
        return READY_BASE + bestFan * 10_000L + qualifiedWaits * 100L + totalFan;
    }

    /**
     * Scores the hand a claimant would hold after discarding its best tile.
     *
     * <p>Claiming a chow or pung leaves one tile too many, so the claim is only worth as much as
     * the best hand reachable by the discard that follows it.</p>
     */
    long bestReadyScoreAfterDiscard(TileCounts concealed, List<Meld> melds, Wind seatWind,
                                    Wind roundWind, List<Tile> flowers) {
        if (concealed == null || melds == null || concealed.total() != 14 - 3 * melds.size()) {
            return 0L;
        }
        long best = 0L;
        for (int index = 0; index < Tile.STANDARD_KIND_COUNT; index++) {
            Tile tile = Tile.standard(index);
            if (concealed.count(tile) == 0) {
                continue;
            }
            best = Math.max(
                    best,
                    readyScore(concealed.minus(tile), melds, seatWind, roundWind, flowers));
        }
        return best;
    }

    /**
     * Shape preference for discarding {@code tile} out of {@code hand}, higher meaning "more
     * willing to let it go". Terminals and honours leave first; pairs and neighbours are kept.
     */
    static int discardPreference(TileCounts hand, Tile tile) {
        int score = 0;
        if (tile.isHonor() || (tile.isNumbered() && (tile.rank() == 1 || tile.rank() == 9))) {
            score += 3;
        }
        if (hand.count(tile) >= 2) {
            score -= 4;
        }
        if (!tile.isNumbered()) {
            return score;
        }
        score -= neighbourPenalty(hand, tile, -1, 2);
        score -= neighbourPenalty(hand, tile, 1, 2);
        score -= neighbourPenalty(hand, tile, -2, 1);
        score -= neighbourPenalty(hand, tile, 2, 1);
        return score;
    }

    private static int neighbourPenalty(TileCounts hand, Tile tile, int offset, int penalty) {
        int rank = tile.rank() + offset;
        if (rank < 1 || rank > 9) {
            return 0;
        }
        // Numbered kinds are contiguous per suit, so the neighbour is a plain index step.
        return hand.count(Tile.standard(tile.index() + offset)) > 0 ? penalty : 0;
    }

    private static int candidateFan(WaitCandidate candidate) {
        if (candidate.discardEvaluation().isPresent()) {
            return candidate.discardEvaluation().get().totalFan();
        }
        return candidate.selfDrawEvaluation().map(WinEvaluation::totalFan).orElse(0);
    }
}
