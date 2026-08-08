package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class ShapeSolver {
    enum SpecialShape { NONE, THIRTEEN_ORPHANS, SEVEN_PAIRS, CONSECUTIVE_SEVEN_PAIRS,
        FULLY_DISCONNECTED, SEVEN_STAR_DISCONNECTED }

    private ShapeSolver() {}

    static SpecialShape special(byte[] afterWin, List<Meld> melds) {
        if (!melds.isEmpty()) return SpecialShape.NONE;
        if (isThirteenOrphans(afterWin)) return SpecialShape.THIRTEEN_ORPHANS;
        SpecialShape pairs = sevenPairs(afterWin);
        if (pairs != SpecialShape.NONE) return pairs;
        if (isFullyDisconnected(afterWin)) {
            boolean allHonors = true;
            for (int i = 27; i < 34; i++) if (afterWin[i] == 0) allHonors = false;
            return allHonors ? SpecialShape.SEVEN_STAR_DISCONNECTED : SpecialShape.FULLY_DISCONNECTED;
        }
        return SpecialShape.NONE;
    }

    static boolean isWinning(byte[] afterWin, List<Meld> melds) {
        if (special(afterWin, melds) != SpecialShape.NONE) return true;
        if (!basicGroups(afterWin.clone(), 4 - melds.size(), 1).isEmpty()) return true;
        for (int p = 0; p < TilePatterns.KNITTED.length; p++) {
            if (!TilePatterns.containsPattern(afterWin, p)) continue;
            byte[] remaining = afterWin.clone();
            for (int tile : TilePatterns.KNITTED[p]) remaining[tile]--;
            if (!basicGroups(remaining, 1 - melds.size(), 1).isEmpty()) return true;
        }
        return false;
    }

    static List<Decomposition> decompositions(byte[] afterWin, List<Meld> melds,
                                               Tile winningTile, WinMethod method) {
        List<Group> declared = new ArrayList<>(melds.size());
        for (Meld meld : melds) declared.add(Group.fromMeld(meld));
        List<Decomposition> result = new ArrayList<>();
        LongKeySet seen = new LongKeySet();

        for (List<Group> concealedGroups : basicGroups(afterWin.clone(), 4 - melds.size(), 1)) {
            allocateWinning(declared, concealedGroups, -1, winningTile.index(), method, result, seen);
        }
        for (int p = 0; p < TilePatterns.KNITTED.length; p++) {
            if (!TilePatterns.containsPattern(afterWin, p)) continue;
            byte[] remaining = afterWin.clone();
            for (int tile : TilePatterns.KNITTED[p]) remaining[tile]--;
            for (List<Group> concealedGroups : basicGroups(remaining, 1 - melds.size(), 1)) {
                boolean inKnitted = false;
                for (int tile : TilePatterns.KNITTED[p]) {
                    if (tile == winningTile.index()) { inKnitted = true; break; }
                }
                allocateWinning(declared, concealedGroups, p, winningTile.index(), method, result, seen);
                if (inKnitted) {
                    List<Group> combined = new ArrayList<>(declared);
                    combined.addAll(concealedGroups);
                    addUnique(new Decomposition(combined, p, true), result, seen);
                }
            }
        }
        return result;
    }

    private static void allocateWinning(List<Group> declared, List<Group> concealedGroups,
                                        int knittedPattern, int winningTile, WinMethod method,
                                        List<Decomposition> result, LongKeySet seen) {
        for (int i = 0; i < concealedGroups.size(); i++) {
            Group group = concealedGroups.get(i);
            if (!group.contains(winningTile)) continue;
            List<Group> combined = new ArrayList<>(declared.size() + concealedGroups.size());
            combined.addAll(declared);
            for (int j = 0; j < concealedGroups.size(); j++) {
                combined.add(i == j ? concealedGroups.get(j).markWinning(method) : concealedGroups.get(j));
            }
            addUnique(new Decomposition(combined, knittedPattern, false), result, seen);
        }
    }

    private static void addUnique(Decomposition decomposition, List<Decomposition> result, LongKeySet seen) {
        long key = decomposition.knittedPattern() + 1L;
        key = key << 1 | (decomposition.winningInKnitted() ? 1 : 0);
        key = key << 3 | decomposition.groups().size();
        for (Group group : decomposition.groups()) {
            long encoded = group.tile;
            encoded = encoded << 2 | group.kind.ordinal();
            encoded = encoded << 1 | (group.declared ? 1 : 0);
            encoded = encoded << 1 | (group.concealed ? 1 : 0);
            encoded = encoded << 1 | (group.containsWinning ? 1 : 0);
            key = key << 11 | encoded;
        }
        if (seen.add(key)) result.add(decomposition);
    }

    private static List<List<Group>> basicGroups(byte[] counts, int setsNeeded, int pairsNeeded) {
        List<List<Group>> result = new ArrayList<>();
        if (setsNeeded < 0 || pairsNeeded < 0) return result;
        collectGroups(counts, setsNeeded, pairsNeeded, new ArrayList<>(setsNeeded + pairsNeeded), result);
        return result;
    }

    private static void collectGroups(byte[] counts, int setsNeeded, int pairsNeeded,
                                      List<Group> current, List<List<Group>> result) {
        int first = -1;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] != 0) { first = i; break; }
        }
        if (first < 0) {
            if (setsNeeded == 0 && pairsNeeded == 0) result.add(List.copyOf(current));
            return;
        }
        if (pairsNeeded > 0 && counts[first] >= 2) {
            counts[first] -= 2;
            current.add(new Group(Group.Kind.PAIR, first, false, true, false));
            collectGroups(counts, setsNeeded, pairsNeeded - 1, current, result);
            current.remove(current.size() - 1);
            counts[first] += 2;
        }
        if (setsNeeded == 0) return;
        if (counts[first] >= 3) {
            counts[first] -= 3;
            current.add(new Group(Group.Kind.PUNG, first, false, true, false));
            collectGroups(counts, setsNeeded - 1, pairsNeeded, current, result);
            current.remove(current.size() - 1);
            counts[first] += 3;
        }
        Tile tile = Tile.standard(first);
        if (tile.isNumbered() && tile.rank() <= 7 && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--; counts[first + 1]--; counts[first + 2]--;
            current.add(new Group(Group.Kind.CHOW, first + 1, false, true, false));
            collectGroups(counts, setsNeeded - 1, pairsNeeded, current, result);
            current.remove(current.size() - 1);
            counts[first]++; counts[first + 1]++; counts[first + 2]++;
        }
    }

    private static boolean isThirteenOrphans(byte[] counts) {
        int[] required = {0, 8, 9, 17, 18, 26, 27, 28, 29, 30, 31, 32, 33};
        int total = 0;
        int distinct = 0;
        for (int i = 0; i < counts.length; i++) {
            total += counts[i];
            if (counts[i] > 0) distinct++;
            if (counts[i] > 0 && !isTerminalOrHonor(i)) return false;
        }
        if (total != 14 || distinct != 13) return false;
        for (int tile : required) if (counts[tile] == 0) return false;
        return true;
    }

    private static SpecialShape sevenPairs(byte[] counts) {
        int pairs = 0;
        int distinct = 0;
        int first = -1;
        int last = -1;
        int suit = -1;
        boolean consecutive = true;
        for (int i = 0; i < counts.length; i++) {
            if ((counts[i] & 1) != 0) return SpecialShape.NONE;
            pairs += counts[i] / 2;
            if (counts[i] > 0) {
                distinct++;
                Tile tile = Tile.standard(i);
                if (!tile.isNumbered() || counts[i] != 2) consecutive = false;
                if (suit < 0) suit = tile.suit().ordinal();
                else if (suit != tile.suit().ordinal()) consecutive = false;
                if (first < 0) first = i;
                last = i;
            }
        }
        if (pairs != 7) return SpecialShape.NONE;
        if (consecutive && distinct == 7 && last - first == 6) return SpecialShape.CONSECUTIVE_SEVEN_PAIRS;
        return SpecialShape.SEVEN_PAIRS;
    }

    private static boolean isFullyDisconnected(byte[] counts) {
        int total = 0;
        for (byte count : counts) {
            if (count > 1) return false;
            total += count;
        }
        return total == 14 && TilePatterns.suitedSubsetOfKnitted(counts);
    }

    private static boolean isTerminalOrHonor(int index) {
        return Tile.standard(index).isTerminalOrHonor();
    }

    /** Tiny primitive set; a hand normally produces fewer than twenty decompositions. */
    private static final class LongKeySet {
        private long[] values = new long[16];
        private int size;

        boolean add(long value) {
            for (int i = 0; i < size; i++) if (values[i] == value) return false;
            if (size == values.length) values = Arrays.copyOf(values, size * 2);
            values[size++] = value;
            return true;
        }
    }
}
