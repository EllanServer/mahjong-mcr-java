package top.ellan.mahjong.rules.mcr;

/** Exact fourth tile transferred out of a failed added-kong declaration. */
public record McrRobbedKongClaim(
        Wind sourceSeat,
        Wind winner,
        McrTileInstance tile) {

    public McrRobbedKongClaim {
        if (sourceSeat == null || winner == null || winner == sourceSeat
                || tile == null || tile.kind().isFlower()) {
            throw new IllegalArgumentException("robbed-kong source, winner and standard tile are required");
        }
    }
}
