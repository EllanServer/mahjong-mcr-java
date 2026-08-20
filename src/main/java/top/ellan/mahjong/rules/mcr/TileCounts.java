package top.ellan.mahjong.rules.mcr;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/** Immutable compact counts for the 34 non-flower tile kinds. */
public final class TileCounts {
    private final byte[] counts;
    private final int total;

    private TileCounts(byte[] counts, boolean trusted) {
        this.counts = trusted ? counts : counts.clone();
        int sum = 0;
        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            if (count < 0 || count > 4) {
                throw new IllegalArgumentException("tile count must be in 0..4 at index " + i);
            }
            sum += count;
        }
        this.total = sum;
    }

    public static TileCounts empty() {
        return new TileCounts(new byte[Tile.STANDARD_KIND_COUNT], true);
    }

    public static TileCounts of(Collection<Tile> tiles) {
        if (tiles == null) {
            throw new IllegalArgumentException("tiles cannot be null");
        }
        byte[] counts = new byte[Tile.STANDARD_KIND_COUNT];
        for (Tile tile : tiles) {
            if (tile == null || !tile.isStandard()) {
                throw new IllegalArgumentException("concealed tiles must be non-null standard tiles");
            }
            if (++counts[tile.index()] > 4) {
                throw new IllegalArgumentException("more than four copies of " + tile);
            }
        }
        return new TileCounts(counts, true);
    }

    public static TileCounts of(Tile... tiles) {
        return of(List.of(tiles));
    }

    public static TileCounts parse(String... codes) {
        Tile[] tiles = new Tile[codes.length];
        for (int i = 0; i < codes.length; i++) {
            tiles[i] = Tile.parse(codes[i]);
        }
        return of(tiles);
    }

    static TileCounts fromTrusted(byte[] counts) {
        return new TileCounts(counts, true);
    }

    public int count(Tile tile) {
        requireStandard(tile);
        return counts[tile.index()];
    }

    public int total() { return total; }

    public TileCounts plus(Tile tile) {
        requireStandard(tile);
        int index = tile.index();
        if (counts[index] == 4) {
            throw new IllegalArgumentException("more than four copies of " + tile);
        }
        byte[] copy = counts.clone();
        copy[index]++;
        return new TileCounts(copy, true);
    }

    public TileCounts minus(Tile tile) {
        requireStandard(tile);
        int index = tile.index();
        if (counts[index] == 0) {
            throw new IllegalArgumentException("tile is not present: " + tile);
        }
        byte[] copy = counts.clone();
        copy[index]--;
        return new TileCounts(copy, true);
    }

    byte[] copyArray() { return counts.clone(); }

    private static void requireStandard(Tile tile) {
        if (tile == null || !tile.isStandard()) {
            throw new IllegalArgumentException("expected a standard tile");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof TileCounts that && Arrays.equals(counts, that.counts);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(counts); }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[");
        boolean first = true;
        for (int i = 0; i < counts.length; i++) {
            for (int n = 0; n < counts[i]; n++) {
                if (!first) result.append(',');
                result.append(Tile.standard(i).code());
                first = false;
            }
        }
        return result.append(']').toString();
    }
}
