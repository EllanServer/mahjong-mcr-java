package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;

final class InputValidation {
    private InputValidation() {}

    static List<Meld> melds(List<Meld> source) {
        if (source == null || source.isEmpty()) return List.of();
        if (source.size() > 4) throw new IllegalArgumentException("at most four melds are allowed");
        List<Meld> result = new ArrayList<>(source.size());
        for (Meld meld : source) {
            if (meld == null) throw new IllegalArgumentException("melds cannot contain null");
            result.add(meld);
        }
        return List.copyOf(result);
    }

    static void structuralCount(TileCounts concealed, List<Meld> melds) {
        int expected = 13 - 3 * melds.size();
        if (concealed.total() != expected) {
            throw new IllegalArgumentException(
                    "expected " + expected + " concealed tiles before win, got " + concealed.total());
        }
    }

    static int[] physicalCounts(TileCounts concealed, List<Meld> melds, Tile extra) {
        int[] counts = new int[Tile.STANDARD_KIND_COUNT];
        for (int i = 0; i < counts.length; i++) counts[i] = concealed.count(Tile.standard(i));
        if (extra != null) counts[extra.index()]++;
        for (Meld meld : melds) {
            if (meld.type() == MeldType.CHOW) {
                counts[meld.tile().index() - 1]++;
                counts[meld.tile().index()]++;
                counts[meld.tile().index() + 1]++;
            } else {
                counts[meld.tile().index()] += meld.type() == MeldType.KONG ? 4 : 3;
            }
        }
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > 4) {
                throw new IllegalArgumentException("more than four physical copies of " + Tile.standard(i));
            }
        }
        return counts;
    }

    static boolean hasKong(List<Meld> melds) {
        for (Meld meld : melds) if (meld.type() == MeldType.KONG) return true;
        return false;
    }
}
