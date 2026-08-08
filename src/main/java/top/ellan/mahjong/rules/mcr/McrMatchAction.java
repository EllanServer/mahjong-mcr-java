package top.ellan.mahjong.rules.mcr;

/** Player and actor-owned commands accepted by the pure match state machine. */
public sealed interface McrMatchAction
        permits McrMatchAction.Play, McrMatchAction.StartNextHand, McrMatchAction.EndMatch {

    record Play(McrRoundAction action) implements McrMatchAction {
        public Play {
            if (action == null) throw new IllegalArgumentException("round action is required");
        }
    }

    /** Actor-owned boundary action; never exposed as a player command. */
    record StartNextHand() implements McrMatchAction {}

    /** Actor-owned tournament deadline action, legal only after a completed hand. */
    record EndMatch() implements McrMatchAction {}
}
