package top.ellan.mahjong.rules.mcr;

/** Immutable river entry retaining an exact tile and its final disposition. */
public record McrPhysicalDiscard(
        Wind sourceSeat,
        McrTileInstance tile,
        McrDiscardStatus status,
        Wind claimant) {

    public McrPhysicalDiscard {
        if (sourceSeat == null || tile == null || tile.kind().isFlower() || status == null) {
            throw new IllegalArgumentException("source, standard tile and discard status are required");
        }
        boolean claimed = status == McrDiscardStatus.MELD_CLAIMED
                || status == McrDiscardStatus.WIN_CLAIMED;
        if (claimed != (claimant != null) || claimant == sourceSeat) {
            throw new IllegalArgumentException("discard claimant does not match its status");
        }
    }

    public static McrPhysicalDiscard pending(Wind sourceSeat, McrTileInstance tile) {
        return new McrPhysicalDiscard(sourceSeat, tile, McrDiscardStatus.PENDING, null);
    }

    public McrPhysicalDiscard unclaimed() {
        requirePending();
        return new McrPhysicalDiscard(sourceSeat, tile, McrDiscardStatus.UNCLAIMED, null);
    }

    public McrPhysicalDiscard claimedForMeld(Wind seat) {
        requirePending();
        return new McrPhysicalDiscard(sourceSeat, tile, McrDiscardStatus.MELD_CLAIMED, seat);
    }

    public McrPhysicalDiscard claimedForWin(Wind seat) {
        requirePending();
        return new McrPhysicalDiscard(sourceSeat, tile, McrDiscardStatus.WIN_CLAIMED, seat);
    }

    private void requirePending() {
        if (status != McrDiscardStatus.PENDING) {
            throw new IllegalStateException("only a pending discard can be resolved");
        }
    }
}
