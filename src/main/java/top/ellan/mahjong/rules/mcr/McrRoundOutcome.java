package top.ellan.mahjong.rules.mcr;

/** Immutable terminal result of one MCR hand. */
public sealed interface McrRoundOutcome
        permits McrRoundOutcome.Win, McrRoundOutcome.ExhaustiveDraw {

    /** A scored single-winner result. A self draw has no discarder. */
    record Win(Wind winner, Wind discarder, WinEvaluation evaluation, Payment payment)
            implements McrRoundOutcome {
        public Win {
            if (winner == null || evaluation == null || !evaluation.legalWin() || payment == null) {
                throw new IllegalArgumentException("winner, legal evaluation and payment are required");
            }
            if (evaluation.winMethod() == WinMethod.SELF_DRAW) {
                if (discarder != null) throw new IllegalArgumentException("self draw has no discarder");
            } else if (discarder == null || discarder == winner) {
                throw new IllegalArgumentException("discard win requires a different discarder");
            }
            Payment expected = McrPayments.settle(evaluation, winner, discarder);
            if (!expected.equals(payment)) {
                throw new IllegalArgumentException("round payment must match the issued evaluation");
            }
        }
    }

    /** The wall was exhausted without a legal winner. */
    record ExhaustiveDraw() implements McrRoundOutcome {}
}
