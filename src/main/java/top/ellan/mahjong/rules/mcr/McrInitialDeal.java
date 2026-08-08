package top.ellan.mahjong.rules.mcr;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The immutable result after the initial deal and all initial flower replacements. */
public record McrInitialDeal(
        Map<Wind, List<McrTileInstance>> concealed,
        Map<Wind, List<McrTileInstance>> flowers,
        McrWall wall) {

    public McrInitialDeal {
        if (wall == null) throw new IllegalArgumentException("wall is required");
        concealed = copySeatMap(concealed, false);
        flowers = copySeatMap(flowers, true);
        validateHandSizes(concealed);
        validateConservation(concealed, flowers, wall);
    }

    public List<McrTileInstance> concealed(Wind seat) {
        return requiredSeat(concealed, seat);
    }

    public List<McrTileInstance> flowers(Wind seat) {
        return requiredSeat(flowers, seat);
    }

    private static Map<Wind, List<McrTileInstance>> copySeatMap(
            Map<Wind, List<McrTileInstance>> source, boolean expectFlowers) {
        if (source == null) throw new IllegalArgumentException("seat tile map is required");
        EnumMap<Wind, List<McrTileInstance>> copy = new EnumMap<>(Wind.class);
        for (Wind wind : Wind.values()) {
            List<McrTileInstance> tiles = source.get(wind);
            if (tiles == null) throw new IllegalArgumentException("missing tiles for seat " + wind);
            List<McrTileInstance> immutable = List.copyOf(tiles);
            for (McrTileInstance tile : immutable) {
                if (tile == null || tile.kind().isFlower() != expectFlowers) {
                    throw new IllegalArgumentException(expectFlowers
                            ? "flower area may contain only flowers"
                            : "concealed hand may contain only standard tiles");
                }
            }
            copy.put(wind, immutable);
        }
        if (source.size() != Wind.values().length) {
            throw new IllegalArgumentException("tile map may only contain the four MCR seats");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void validateHandSizes(Map<Wind, List<McrTileInstance>> concealed) {
        for (Wind wind : Wind.values()) {
            int expected = wind == Wind.EAST ? 14 : 13;
            int actual = concealed.get(wind).size();
            if (actual != expected) {
                throw new IllegalArgumentException(
                        wind + " must hold " + expected + " standard tiles after the deal, got " + actual);
            }
        }
    }

    private static void validateConservation(
            Map<Wind, List<McrTileInstance>> concealed,
            Map<Wind, List<McrTileInstance>> flowers,
            McrWall wall) {
        boolean[] seen = new boolean[McrTileInstance.PHYSICAL_TILE_COUNT];
        int count = 0;
        for (Wind wind : Wind.values()) {
            count += markAll(concealed.get(wind), seen);
            count += markAll(flowers.get(wind), seen);
        }
        for (int i = 0; i < wall.remaining(); i++) {
            mark(wall.peekAt(i), seen);
            count++;
        }
        if (count != McrTileInstance.PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("deal must conserve all 144 physical tiles, got " + count);
        }
    }

    private static int markAll(List<McrTileInstance> tiles, boolean[] seen) {
        for (McrTileInstance tile : tiles) mark(tile, seen);
        return tiles.size();
    }

    private static void mark(McrTileInstance tile, boolean[] seen) {
        if (seen[tile.id()]) {
            throw new IllegalArgumentException("physical tile appears more than once: " + tile.id());
        }
        seen[tile.id()] = true;
    }

    private static List<McrTileInstance> requiredSeat(
            Map<Wind, List<McrTileInstance>> source, Wind seat) {
        if (seat == null) throw new IllegalArgumentException("seat is required");
        return source.get(seat);
    }
}
