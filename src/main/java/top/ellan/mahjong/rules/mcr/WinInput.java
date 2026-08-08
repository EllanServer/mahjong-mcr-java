package top.ellan.mahjong.rules.mcr;

import java.util.List;

/** Concealed tiles exclude the winning tile. Kongs count as three structural tiles. */
public record WinInput(TileCounts concealed, List<Meld> melds, Tile winningTile, WinContext context) {
    public WinInput {
        if (concealed == null || winningTile == null || !winningTile.isStandard() || context == null) {
            throw new IllegalArgumentException("concealed tiles, standard winning tile and context are required");
        }
        melds = InputValidation.melds(melds);
        InputValidation.structuralCount(concealed, melds);
        int[] physical = InputValidation.physicalCounts(concealed, melds, winningTile);
        if (context.has(WinFlag.AFTER_KONG) && !InputValidation.hasKong(melds)) {
            throw new IllegalArgumentException("AFTER_KONG requires a declared kong in the winning hand");
        }
        if (context.has(WinFlag.LAST_OF_KIND) && concealed.count(winningTile) != 0) {
            throw new IllegalArgumentException("LAST_OF_KIND requires no concealed copy of the winning tile");
        }
        if (context.has(WinFlag.ROBBING_KONG) && physical[winningTile.index()] != 1) {
            throw new IllegalArgumentException(
                    "ROBBING_KONG requires the robbed tile to be the hand's only physical copy");
        }
    }
}
