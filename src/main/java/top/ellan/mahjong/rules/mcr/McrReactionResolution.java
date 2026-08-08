package top.ellan.mahjong.rules.mcr;

/** The deterministic outcome of a fully closed discard reaction window. */
public sealed interface McrReactionResolution
        permits McrReactionResolution.NoClaim,
                McrReactionResolution.MeldClaim,
                McrReactionResolution.Win {

    /** No claim was made, so normal play continues with {@code nextDrawer}. */
    record NoClaim(Wind nextDrawer) implements McrReactionResolution {
        public NoClaim {
            if (nextDrawer == null) throw new IllegalArgumentException("next drawer is required");
        }
    }

    /** One chow, pung, or kong claim won priority. */
    record MeldClaim(McrReaction reaction) implements McrReactionResolution {
        public MeldClaim {
            if (reaction == null || reaction.type() == McrReactionType.PASS
                    || reaction.type() == McrReactionType.HU) {
                throw new IllegalArgumentException("a meld resolution requires a chow, pung, or kong");
            }
        }
    }

    /** A legal hu claim won priority; MCR permits only this one winner. */
    record Win(McrReaction reaction) implements McrReactionResolution {
        public Win {
            if (reaction == null || reaction.type() != McrReactionType.HU) {
                throw new IllegalArgumentException("a win resolution requires a hu reaction");
            }
        }
    }
}
