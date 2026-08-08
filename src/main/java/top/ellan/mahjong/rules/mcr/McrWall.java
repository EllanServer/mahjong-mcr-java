package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable two-ended MCR wall. Ordinary draws consume the front; flower and
 * kong replacements consume the back.
 */
public final class McrWall {
    private final short[] order;
    private final int frontInclusive;
    private final int backExclusive;

    private McrWall(short[] order, int frontInclusive, int backExclusive) {
        this.order = order;
        this.frontInclusive = frontInclusive;
        this.backExclusive = backExclusive;
    }

    /** Creates a complete 144-tile wall using the repository's stable shuffle. */
    public static McrWall shuffled(long seed) {
        short[] ids = new short[McrTileInstance.PHYSICAL_TILE_COUNT];
        for (short id = 0; id < ids.length; id++) ids[id] = id;
        StableRandom random = new StableRandom(seed);
        for (int i = ids.length - 1; i > 0; i--) {
            int other = random.nextInt(i + 1);
            short swap = ids[i];
            ids[i] = ids[other];
            ids[other] = swap;
        }
        return new McrWall(ids, 0, ids.length);
    }

    /**
     * Creates a complete wall with an explicit physical order. This is useful
     * for deterministic replay and normative fixtures.
     */
    public static McrWall fromOrder(List<McrTileInstance> tiles) {
        if (tiles == null || tiles.size() != McrTileInstance.PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("a complete MCR wall must contain exactly 144 tiles");
        }
        return fromRemainingOrder(tiles);
    }

    /** Restores the exact remaining slice of a previously validated round snapshot. */
    public static McrWall fromRemainingOrder(List<McrTileInstance> tiles) {
        if (tiles == null || tiles.size() > McrTileInstance.PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("remaining MCR wall must contain at most 144 tiles");
        }
        short[] ids = new short[tiles.size()];
        boolean[] seen = new boolean[McrTileInstance.PHYSICAL_TILE_COUNT];
        for (int i = 0; i < tiles.size(); i++) {
            McrTileInstance tile = tiles.get(i);
            if (tile == null) throw new IllegalArgumentException("wall cannot contain null tiles");
            if (seen[tile.id()]) {
                throw new IllegalArgumentException("duplicate physical tile id in wall: " + tile.id());
            }
            seen[tile.id()] = true;
            ids[i] = (short) tile.id();
        }
        return new McrWall(ids, 0, ids.length);
    }

    public int remaining() {
        return backExclusive - frontInclusive;
    }

    public boolean isEmpty() {
        return remaining() == 0;
    }

    public McrTileInstance peekAt(int offsetFromFront) {
        if (offsetFromFront < 0 || offsetFromFront >= remaining()) {
            throw new IndexOutOfBoundsException("wall offset outside remaining tiles: " + offsetFromFront);
        }
        return McrTileInstance.fromId(order[frontInclusive + offsetFromFront]);
    }

    public McrTileInstance peekBack() {
        ensureNotEmpty();
        return McrTileInstance.fromId(order[backExclusive - 1]);
    }

    public Draw drawFront() {
        ensureNotEmpty();
        McrTileInstance tile = McrTileInstance.fromId(order[frontInclusive]);
        return new Draw(tile, new McrWall(order, frontInclusive + 1, backExclusive));
    }

    public Draw drawBack() {
        ensureNotEmpty();
        McrTileInstance tile = McrTileInstance.fromId(order[backExclusive - 1]);
        return new Draw(tile, new McrWall(order, frontInclusive, backExclusive - 1));
    }

    public List<McrTileInstance> remainingTiles() {
        ArrayList<McrTileInstance> tiles = new ArrayList<>(remaining());
        for (int i = frontInclusive; i < backExclusive; i++) {
            tiles.add(McrTileInstance.fromId(order[i]));
        }
        return List.copyOf(tiles);
    }

    private void ensureNotEmpty() {
        if (isEmpty()) throw new IllegalStateException("the wall is empty");
    }

    /** A tile and the new wall after consuming it. */
    public record Draw(McrTileInstance tile, McrWall remainingWall) {
        public Draw {
            if (tile == null || remainingWall == null) {
                throw new IllegalArgumentException("draw tile and remaining wall are required");
            }
        }
    }

    /** SplitMix64 with rejection sampling; this algorithm is part of deterministic replay. */
    private static final class StableRandom {
        private long state;

        private StableRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            long value = state += 0x9E3779B97F4A7C15L;
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }

        private int nextInt(int bound) {
            if (bound <= 0) throw new IllegalArgumentException("bound must be positive");
            long limit = Long.MAX_VALUE - Long.MAX_VALUE % bound;
            long candidate;
            do {
                candidate = nextLong() >>> 1;
            } while (candidate >= limit);
            return (int) (candidate % bound);
        }
    }
}
