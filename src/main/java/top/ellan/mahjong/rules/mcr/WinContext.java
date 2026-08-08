package top.ellan.mahjong.rules.mcr;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record WinContext(
        Wind seatWind,
        Wind roundWind,
        WinMethod method,
        Set<WinFlag> flags,
        List<Tile> flowers) {

    public WinContext {
        if (seatWind == null || roundWind == null || method == null) {
            throw new IllegalArgumentException("seat wind, round wind and win method are required");
        }
        flags = flags == null || flags.isEmpty()
                ? Set.of()
                : Set.copyOf(EnumSet.copyOf(flags));
        flowers = flowers == null ? List.of() : List.copyOf(flowers);
        boolean[] seen = new boolean[Tile.values().length];
        for (Tile flower : flowers) {
            if (flower == null || !flower.isFlower()) {
                throw new IllegalArgumentException("flower list may only contain flower tiles");
            }
            if (seen[flower.index()]) {
                throw new IllegalArgumentException("duplicate physical flower: " + flower);
            }
            seen[flower.index()] = true;
        }
        if (flags.contains(WinFlag.AFTER_KONG) && method != WinMethod.SELF_DRAW) {
            throw new IllegalArgumentException("AFTER_KONG requires self draw");
        }
        if (flags.contains(WinFlag.ROBBING_KONG) && method != WinMethod.DISCARD) {
            throw new IllegalArgumentException("ROBBING_KONG requires discard win");
        }
        if (flags.contains(WinFlag.AFTER_KONG) && flags.contains(WinFlag.ROBBING_KONG)) {
            throw new IllegalArgumentException("AFTER_KONG and ROBBING_KONG are mutually exclusive");
        }
        if (flags.contains(WinFlag.LAST_TILE) && flags.contains(WinFlag.ROBBING_KONG)) {
            throw new IllegalArgumentException("the last wall tile cannot also be a robbed kong tile");
        }
    }

    public static WinContext standard(Wind seatWind, Wind roundWind, WinMethod method) {
        return new WinContext(seatWind, roundWind, method, Set.of(), List.of());
    }

    public boolean has(WinFlag flag) { return flags.contains(flag); }
}
