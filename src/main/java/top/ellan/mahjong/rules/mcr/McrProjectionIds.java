package top.ellan.mahjong.rules.mcr;

/** Per-hand opaque scene IDs produced by a deterministic Sattolo permutation. */
public final class McrProjectionIds {
    private final short[] byPhysicalId;

    private McrProjectionIds(short[] byPhysicalId) {
        this.byPhysicalId = byPhysicalId;
    }

    public static McrProjectionIds forHand(long handSalt) {
        short[] values = new short[McrTileInstance.PHYSICAL_TILE_COUNT];
        for (short id = 0; id < values.length; id++) values[id] = id;
        StableRandom random = new StableRandom(handSalt ^ 0x4d43_5250_524f_4a32L);
        for (int index = values.length - 1; index > 0; index--) {
            int other = random.nextInt(index);
            short swap = values[index];
            values[index] = values[other];
            values[other] = swap;
        }
        return new McrProjectionIds(values);
    }

    public long project(McrTileInstance tile) {
        if (tile == null) throw new IllegalArgumentException("tile is required");
        return byPhysicalId[tile.id()];
    }

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
            long limit = Long.MAX_VALUE - Long.MAX_VALUE % bound;
            long candidate;
            do {
                candidate = nextLong() >>> 1;
            } while (candidate >= limit);
            return (int) (candidate % bound);
        }
    }
}
