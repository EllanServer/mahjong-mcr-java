package top.ellan.mahjong.rules.mcr;

final class TilePatterns {
    static final int[][] KNITTED = buildKnitted();

    private TilePatterns() {}

    static boolean containsPattern(byte[] counts, int patternIndex) {
        for (int tile : KNITTED[patternIndex]) if (counts[tile] == 0) return false;
        return true;
    }

    static boolean suitedSubsetOfKnitted(byte[] counts) {
        outer:
        for (int[] pattern : KNITTED) {
            boolean[] allowed = new boolean[27];
            for (int tile : pattern) allowed[tile] = true;
            for (int i = 0; i < 27; i++) {
                if (counts[i] > 0 && !allowed[i]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static int[][] buildKnitted() {
        int[][] permutations = {
                {0, 1, 2}, {0, 2, 1}, {1, 0, 2},
                {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
        };
        int[][] result = new int[6][9];
        int[][] ranks = {{1, 4, 7}, {2, 5, 8}, {3, 6, 9}};
        for (int p = 0; p < permutations.length; p++) {
            int at = 0;
            for (int suit = 0; suit < 3; suit++) {
                int rankClass = permutations[p][suit];
                for (int rank : ranks[rankClass]) result[p][at++] = suit * 9 + rank - 1;
            }
        }
        return result;
    }
}
