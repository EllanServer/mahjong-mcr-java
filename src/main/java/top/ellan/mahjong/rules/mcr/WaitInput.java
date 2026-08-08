package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Set;

/** A structurally 13-tile state, adjusted for declared melds. */
public record WaitInput(
        TileCounts concealed,
        List<Meld> melds,
        Wind seatWind,
        Wind roundWind,
        Set<WinFlag> flags,
        List<Tile> flowers) {
    public WaitInput {
        if (concealed == null || seatWind == null || roundWind == null) {
            throw new IllegalArgumentException("concealed tiles and winds are required");
        }
        melds = InputValidation.melds(melds);
        InputValidation.structuralCount(concealed, melds);
        flags = flags == null ? Set.of() : Set.copyOf(flags);
        for (WinFlag flag : flags) {
            if (flag == WinFlag.AFTER_KONG || flag == WinFlag.ROBBING_KONG) {
                throw new IllegalArgumentException("method-specific kong flags belong on a concrete WinContext");
            }
        }
        WinContext normalized = new WinContext(seatWind, roundWind, WinMethod.DISCARD, flags, flowers);
        flags = normalized.flags();
        flowers = normalized.flowers();
        InputValidation.physicalCounts(concealed, melds, null);
    }
}
