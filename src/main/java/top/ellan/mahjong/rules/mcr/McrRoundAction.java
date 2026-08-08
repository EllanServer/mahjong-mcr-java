package top.ellan.mahjong.rules.mcr;

/** Commands accepted by the pure MCR hand engine. */
public sealed interface McrRoundAction
        permits McrRoundAction.Discard,
                McrRoundAction.React,
                McrRoundAction.CloseReactions,
                McrRoundAction.SelfDrawWin,
                McrRoundAction.ConcealedKong,
                McrRoundAction.AddedKong {

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

    /** Declares hu using the exact last tile already tracked by the state. */
    record SelfDrawWin(Wind seat) implements McrRoundAction {
        public SelfDrawWin {
            if (seat == null) throw new IllegalArgumentException("winning seat is required");
        }
    }

    /** Declares a concealed kong using four exact physical instances. */
    record ConcealedKong(Wind seat, java.util.List<McrTileInstance> tiles) implements McrRoundAction {
        public ConcealedKong {
            if (seat == null || tiles == null) {
                throw new IllegalArgumentException("kong seat and tiles are required");
            }
            java.util.ArrayList<McrTileInstance> sorted = new java.util.ArrayList<>(tiles);
            sorted.sort(null);
            tiles = java.util.List.copyOf(sorted);
        }
    }

    /** Proposes adding one exact concealed tile to an existing exposed pung. */
    record AddedKong(Wind seat, McrTileInstance tile) implements McrRoundAction {
        public AddedKong {
            if (seat == null || tile == null) {
                throw new IllegalArgumentException("added-kong seat and tile are required");
            }
        }
    }
}
