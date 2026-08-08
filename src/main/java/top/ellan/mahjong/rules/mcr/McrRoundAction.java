package top.ellan.mahjong.rules.mcr;

/** Commands accepted by the pure MCR hand engine. */
public sealed interface McrRoundAction
        permits McrRoundAction.Discard, McrRoundAction.React, McrRoundAction.CloseReactions {

    /** Discards one exact physical tile from the current hand. */
    record Discard(Wind seat, McrTileInstance tile) implements McrRoundAction {
        public Discard {
            if (seat == null || tile == null) {
                throw new IllegalArgumentException("discard seat and tile are required");
            }
        }
    }

    /** Selects an exact option issued by the current reaction window, or passes. */
    record React(McrReaction reaction) implements McrRoundAction {
        public React {
            if (reaction == null) throw new IllegalArgumentException("reaction is required");
        }
    }

    /** Actor-owned timeout action which passes every seat still pending. */
    record CloseReactions() implements McrRoundAction {}
}
