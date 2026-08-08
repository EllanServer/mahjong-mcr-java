package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;

/**
 * One of the 144 physical tiles in a standard MCR set.
 *
 * <p>IDs {@code 0..135} are the four copies of the 34 standard kinds, grouped
 * by {@link Tile#index()}. IDs {@code 136..143} are the eight unique flowers.
 * The mapping is part of the rule-state snapshot contract and must not change.
 */
public final class McrTileInstance implements Comparable<McrTileInstance> {
    public static final int PHYSICAL_TILE_COUNT = 144;

    private static final McrTileInstance[] BY_ID = new McrTileInstance[PHYSICAL_TILE_COUNT];
    private static final List<McrTileInstance> FULL_SET;

    static {
        ArrayList<McrTileInstance> tiles = new ArrayList<>(PHYSICAL_TILE_COUNT);
        for (int id = 0; id < PHYSICAL_TILE_COUNT; id++) {
            Tile kind;
            int copyIndex;
            if (id < Tile.STANDARD_KIND_COUNT * 4) {
                kind = Tile.standard(id / 4);
                copyIndex = id % 4;
            } else {
                kind = Tile.values()[Tile.STANDARD_KIND_COUNT + id - Tile.STANDARD_KIND_COUNT * 4];
                copyIndex = 0;
            }
            McrTileInstance tile = new McrTileInstance(id, kind, copyIndex);
            BY_ID[id] = tile;
            tiles.add(tile);
        }
        FULL_SET = List.copyOf(tiles);
    }

    private final int id;
    private final Tile kind;
    private final int copyIndex;

    private McrTileInstance(int id, Tile kind, int copyIndex) {
        this.id = id;
        this.kind = kind;
        this.copyIndex = copyIndex;
    }

    public static McrTileInstance fromId(int id) {
        if (id < 0 || id >= PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("physical tile id must be in 0..143: " + id);
        }
        return BY_ID[id];
    }

    public static McrTileInstance of(Tile kind, int copyIndex) {
        if (kind == null) throw new IllegalArgumentException("tile kind is required");
        if (kind.isStandard()) {
            if (copyIndex < 0 || copyIndex >= 4) {
                throw new IllegalArgumentException("standard tile copy index must be in 0..3");
            }
            return BY_ID[kind.index() * 4 + copyIndex];
        }
        if (copyIndex != 0) {
            throw new IllegalArgumentException("a flower has exactly one physical copy");
        }
        return BY_ID[Tile.STANDARD_KIND_COUNT * 4 + kind.index() - Tile.STANDARD_KIND_COUNT];
    }

    public static List<McrTileInstance> fullSet() {
        return FULL_SET;
    }

    public int id() {
        return id;
    }

    public Tile kind() {
        return kind;
    }

    public int copyIndex() {
        return copyIndex;
    }

    @Override
    public int compareTo(McrTileInstance other) {
        return Integer.compare(id, other.id);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof McrTileInstance tile && id == tile.id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return kind + "#" + copyIndex;
    }
}
