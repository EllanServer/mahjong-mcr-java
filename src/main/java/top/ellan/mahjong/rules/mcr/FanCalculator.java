package top.ellan.mahjong.rules.mcr;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

final class FanCalculator {
    private static final List<Integer> WHOLE_HAND = List.of();

    private FanCalculator() {}

    static WinEvaluation evaluate(WinInput input) {
        HandData hand = new HandData(input);
        Result best = null;

        ShapeSolver.SpecialShape special = ShapeSolver.special(hand.afterWin, input.melds());
        if (special != ShapeSolver.SpecialShape.NONE) {
            best = better(best, scoreSpecial(hand, special));
        }

        List<Decomposition> decompositions = ShapeSolver.decompositions(
                hand.afterWin, input.melds(), input.winningTile(), input.context().method());
        int formalWaitCount = formalWaitCount(hand);
        for (Decomposition decomposition : decompositions) {
            best = better(best, scoreBasic(hand, decomposition, formalWaitCount));
        }

        if (best == null) return WinEvaluation.notWinning(input.winningTile(), input.context().method());

        List<FanAward> awards = new ArrayList<>(best.awards);
        int flowers = input.context().flowers().size();
        if (flowers > 0) awards.add(new FanAward(Fan.HUAPAI, flowers));
        int qualifying = best.points;
        return new WinEvaluation(EvaluationStatus.COMPLETE, input.winningTile(), input.context().method(),
                qualifying, flowers, qualifying + flowers, awards, best.internalAwards, List.of());
    }

    private static Result better(Result current, Result candidate) {
        return current == null || candidate.points > current.points ? candidate : current;
    }

    private static Result scoreSpecial(HandData hand, ShapeSolver.SpecialShape shape) {
        Accumulator acc = new Accumulator();
        switch (shape) {
            case THIRTEEN_ORPHANS -> acc.add(Fan.SHISANYAO);
            case FULLY_DISCONNECTED -> acc.add(Fan.QUANBUKAO);
            case SEVEN_STAR_DISCONNECTED -> acc.add(Fan.QIXINGBUKAO);
            case SEVEN_PAIRS, CONSECUTIVE_SEVEN_PAIRS -> {
                acc.add(shape == ShapeSolver.SpecialShape.CONSECUTIVE_SEVEN_PAIRS ? Fan.LIANQIDUI : Fan.QIDUI);
                countOverall(hand, List.of(), false, acc);
            }
            case NONE -> throw new IllegalStateException("not a special shape");
        }

        countWinMode(hand, List.of(), -1, false, 0, acc);
        if (shape == ShapeSolver.SpecialShape.FULLY_DISCONNECTED
                || shape == ShapeSolver.SpecialShape.SEVEN_STAR_DISCONNECTED) {
            int pattern = firstKnittedPattern(hand.afterWin);
            if (pattern >= 0) acc.add(Fan.ZUHELONG);
        }
        acc.exclude(Fan.BUQIUREN);
        acc.exclude(Fan.MENQIANQING);
        if (shape == ShapeSolver.SpecialShape.CONSECUTIVE_SEVEN_PAIRS) {
            acc.exclude(Fan.QINGYISE);
            acc.exclude(Fan.WUZI);
        }
        if (hand.context.method() == WinMethod.SELF_DRAW) acc.forceOne(Fan.ZIMO);
        return acc.finish();
    }

    private static Result scoreBasic(HandData hand, Decomposition decomposition, int formalWaitCount) {
        Accumulator acc = new Accumulator();
        List<Group> groups = decomposition.groups();
        countOverall(hand, groups, decomposition.hasKnittedStraight(), acc);
        countKongAndConcealedPung(groups, acc);
        countAssociated(groups, acc);
        countSingleGroup(hand, groups, acc);
        countWinMode(hand, groups, decomposition.knittedPattern(), decomposition.winningInKnitted(),
                formalWaitCount, acc);
        if (decomposition.hasKnittedStraight()) acc.add(Fan.ZUHELONG);
        return acc.finish();
    }

    private static void countOverall(HandData hand, List<Group> groups, boolean knitted, Accumulator acc) {
        if (!knitted) {
            if (allTiles(hand, FanCalculator::isGreenTile)) {
                acc.add(Fan.LVYISE);
                acc.exclude(Fan.HUNYISE);
            }
            if (isNineGates(hand)) {
                acc.add(Fan.JIULIANBAODENG);
                acc.exclude(Fan.QINGYISE);
                acc.exclude(Fan.BUQIUREN);
                acc.exclude(Fan.MENQIANQING);
                acc.exclude(Fan.WUZI);
                for (int i = 0; i < groups.size(); i++) {
                    Group group = groups.get(i);
                    if (group.isTripletOrKong() && Tile.standard(group.tile).isTerminal()) {
                        acc.exclude(Fan.YAOJIUKE, evidence(i));
                        break;
                    }
                }
            }
            if (allTiles(hand, tile -> tile.isTerminal())) {
                acc.add(Fan.QINGYAOJIU);
                acc.exclude(Fan.PENGPENGHU);
                acc.exclude(Fan.QUANDAIYAO);
                acc.exclude(Fan.WUZI);
                for (int i = 0; i < groups.size(); i++) {
                    for (int j = i + 1; j < groups.size(); j++) {
                        Group a = groups.get(i);
                        Group b = groups.get(j);
                        if (a.isTripletOrKong() && b.isTripletOrKong()
                                && Tile.standard(a.tile).rank() == Tile.standard(b.tile).rank()) {
                            acc.exclude(Fan.SHUANGTONGKE, evidence(i, j));
                        }
                    }
                }
                excludeTerminalHonorPungs(groups, acc);
            }
            if (allTiles(hand, Tile::isHonor)) {
                acc.add(Fan.ZIYISE);
                acc.exclude(Fan.PENGPENGHU);
                acc.exclude(Fan.QUANDAIYAO);
                excludeTerminalHonorPungs(groups, acc);
            }
            if (hasTile(hand, tile -> tile.isTerminal()) && hasTile(hand, Tile::isHonor)
                    && allTiles(hand, Tile::isTerminalOrHonor)) {
                acc.add(Fan.HUNYAOJIU);
                acc.exclude(Fan.PENGPENGHU);
                acc.exclude(Fan.QUANDAIYAO);
                excludeTerminalHonorPungs(groups, acc);
            }
            if (groups.size() == 5 && allGroups(groups, group -> {
                Tile tile = Tile.standard(group.tile);
                return (group.isTripletOrKong() || group.isPair())
                        && tile.isNumbered() && (tile.rank() & 1) == 0;
            })) {
                acc.add(Fan.QUANSHUANGKE);
                acc.exclude(Fan.PENGPENGHU);
                acc.exclude(Fan.DUANYAO);
                acc.exclude(Fan.WUZI);
            }
            if (allTilesSameNumberedSuit(hand)) {
                acc.add(Fan.QINGYISE);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && tile.rank() >= 7)) {
                acc.add(Fan.QUANDA);
                acc.exclude(Fan.DAYUWU);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && tile.rank() >= 4 && tile.rank() <= 6)) {
                acc.add(Fan.QUANZHONG);
                acc.exclude(Fan.DUANYAO);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && tile.rank() <= 3)) {
                acc.add(Fan.QUANXIAO);
                acc.exclude(Fan.XIAOYUWU);
                acc.exclude(Fan.WUZI);
            }
            if (groups.size() == 5 && allGroups(groups, group -> {
                Tile tile = Tile.standard(group.tile);
                return group.isSequence() ? tile.rank() >= 4 && tile.rank() <= 6
                        : (group.isTripletOrKong() || group.isPair()) && tile.rank() == 5;
            })) {
                acc.add(Fan.QUANDAIWU);
                acc.exclude(Fan.DUANYAO);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && tile.rank() >= 6)) {
                acc.add(Fan.DAYUWU);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && tile.rank() <= 4)) {
                acc.add(Fan.XIAOYUWU);
                acc.exclude(Fan.WUZI);
            }
            if (allTiles(hand, FanCalculator::isReversibleTile)) {
                acc.add(Fan.TUIBUDAO);
                acc.exclude(Fan.QUEYIMEN);
            }
            if (groups.size() == 5 && allGroups(groups, group -> group.isTripletOrKong() || group.isPair())) {
                acc.add(Fan.PENGPENGHU);
            }
            if (isHalfFlush(hand)) acc.add(Fan.HUNYISE);
            if (groups.size() == 5 && allGroups(groups, group -> {
                Tile tile = Tile.standard(group.tile);
                return group.isSequence() ? tile.rank() == 2 || tile.rank() == 8
                        : (group.isTripletOrKong() || group.isPair()) && tile.isTerminalOrHonor();
            })) {
                acc.add(Fan.QUANDAIYAO);
            }
            if (allTiles(hand, tile -> tile.isNumbered() && !tile.isTerminal())) {
                acc.add(Fan.DUANYAO);
                acc.exclude(Fan.WUZI);
            }
            if (numberedSuitCount(hand) == 2) acc.add(Fan.QUEYIMEN);
        }

        if (hasAllFiveGates(hand)) acc.add(Fan.WUMENQI);
        if (groups.size() != 7 && !groups.isEmpty() && allGroups(groups, group ->
                group.isSequence() || group.isPair() && Tile.standard(group.tile).isNumbered())) {
            acc.add(Fan.PINGHU);
            acc.exclude(Fan.WUZI);
        }
        for (int tile = 0; tile < Tile.STANDARD_KIND_COUNT; tile++) {
            boolean kong = false;
            for (Group group : groups) {
                if (group.isKong() && group.tile == tile) { kong = true; break; }
            }
            if (!kong && hand.physicalCounts[tile] == 4) acc.add(Fan.SIGUIYI);
        }
        if (!hasTile(hand, Tile::isHonor)) acc.add(Fan.WUZI);
    }

    private static boolean isNineGates(HandData hand) {
        if (!hand.input.melds().isEmpty()) return false;
        byte[] before = hand.input.concealed().copyArray();
        for (int suit = 0; suit < 3; suit++) {
            int base = suit * 9;
            boolean match = before[base] == 3 && before[base + 8] == 3;
            for (int rank = 1; rank < 8 && match; rank++) match = before[base + rank] == 1;
            if (!match) continue;
            for (int i = 0; i < 34; i++) {
                if ((i < base || i > base + 8) && before[i] != 0) { match = false; break; }
            }
            if (match) return true;
        }
        return false;
    }

    private static boolean isGreenTile(Tile tile) {
        return tile == Tile.S2 || tile == Tile.S3 || tile == Tile.S4 || tile == Tile.S6
                || tile == Tile.S8 || tile == Tile.GREEN_DRAGON;
    }

    private static boolean isReversibleTile(Tile tile) {
        return tile == Tile.S2 || tile == Tile.S4 || tile == Tile.S5 || tile == Tile.S6
                || tile == Tile.S8 || tile == Tile.S9 || tile == Tile.P1 || tile == Tile.P2
                || tile == Tile.P3 || tile == Tile.P4 || tile == Tile.P5 || tile == Tile.P8
                || tile == Tile.P9 || tile == Tile.WHITE_DRAGON;
    }

    private static void excludeTerminalHonorPungs(List<Group> groups, Accumulator acc) {
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group.isTripletOrKong() && Tile.standard(group.tile).isTerminalOrHonor()) {
                acc.exclude(Fan.YAOJIUKE, evidence(i));
            }
        }
    }

    private static boolean allTilesSameNumberedSuit(HandData hand) {
        int suit = -1;
        for (int i = 0; i < hand.physicalCounts.length; i++) {
            if (hand.physicalCounts[i] == 0) continue;
            Tile tile = Tile.standard(i);
            if (!tile.isNumbered()) return false;
            if (suit < 0) suit = tile.suit().ordinal();
            else if (suit != tile.suit().ordinal()) return false;
        }
        return suit >= 0;
    }

    private static boolean isHalfFlush(HandData hand) {
        int suit = -1;
        boolean numbered = false;
        boolean honor = false;
        for (int i = 0; i < hand.physicalCounts.length; i++) {
            if (hand.physicalCounts[i] == 0) continue;
            Tile tile = Tile.standard(i);
            if (tile.isHonor()) honor = true;
            else {
                numbered = true;
                if (suit < 0) suit = tile.suit().ordinal();
                else if (suit != tile.suit().ordinal()) return false;
            }
        }
        return numbered && honor;
    }

    private static int numberedSuitCount(HandData hand) {
        boolean[] suits = new boolean[3];
        for (int i = 0; i < 27; i++) if (hand.physicalCounts[i] > 0) suits[i / 9] = true;
        return (suits[0] ? 1 : 0) + (suits[1] ? 1 : 0) + (suits[2] ? 1 : 0);
    }

    private static boolean hasAllFiveGates(HandData hand) {
        boolean[] gate = new boolean[5];
        for (int i = 0; i < hand.physicalCounts.length; i++) {
            if (hand.physicalCounts[i] == 0) continue;
            Tile tile = Tile.standard(i);
            if (tile.isNumbered()) gate[tile.suit().ordinal()] = true;
            else if (tile.isWind()) gate[3] = true;
            else if (tile.isDragon()) gate[4] = true;
        }
        for (boolean present : gate) if (!present) return false;
        return true;
    }

    private static int firstKnittedPattern(byte[] counts) {
        for (int p = 0; p < TilePatterns.KNITTED.length; p++) {
            if (TilePatterns.containsPattern(counts, p)) return p;
        }
        return -1;
    }

    private static int formalWaitCount(HandData hand) {
        int result = 0;
        byte[] before = hand.input.concealed().copyArray();
        for (int tile = 0; tile < Tile.STANDARD_KIND_COUNT; tile++) {
            before[tile]++;
            if (ShapeSolver.isWinning(before, hand.input.melds())) result++;
            before[tile]--;
        }
        return result;
    }

    private static boolean allTiles(HandData hand, TilePredicate predicate) {
        boolean any = false;
        for (int i = 0; i < hand.physicalCounts.length; i++) {
            if (hand.physicalCounts[i] == 0) continue;
            any = true;
            if (!predicate.test(Tile.standard(i))) return false;
        }
        return any;
    }

    private static boolean hasTile(HandData hand, TilePredicate predicate) {
        for (int i = 0; i < hand.physicalCounts.length; i++) {
            if (hand.physicalCounts[i] > 0 && predicate.test(Tile.standard(i))) return true;
        }
        return false;
    }

    private static boolean allGroups(List<Group> groups, GroupPredicate predicate) {
        for (Group group : groups) if (!predicate.test(group)) return false;
        return true;
    }

    private static List<Integer> evidence(int... ids) {
        List<Integer> result = new ArrayList<>(ids.length);
        for (int id : ids) result.add(id);
        return List.copyOf(result);
    }

    @FunctionalInterface private interface TilePredicate { boolean test(Tile tile); }
    @FunctionalInterface private interface GroupPredicate { boolean test(Group group); }

    private static final class HandData {
        final WinInput input;
        final WinContext context;
        final byte[] afterWin;
        final int[] physicalCounts;

        HandData(WinInput input) {
            this.input = input;
            this.context = input.context();
            this.afterWin = input.concealed().copyArray();
            this.afterWin[input.winningTile().index()]++;
            this.physicalCounts = new int[Tile.STANDARD_KIND_COUNT];
            for (int i = 0; i < afterWin.length; i++) physicalCounts[i] = afterWin[i];
            for (Meld meld : input.melds()) {
                if (meld.type() == MeldType.CHOW) {
                    physicalCounts[meld.tile().index() - 1]++;
                    physicalCounts[meld.tile().index()]++;
                    physicalCounts[meld.tile().index() + 1]++;
                } else {
                    physicalCounts[meld.tile().index()] += meld.type() == MeldType.KONG ? 4 : 3;
                }
            }
        }

        boolean menqing() {
            for (Meld meld : input.melds()) if (!meld.concealed()) return false;
            return true;
        }

        boolean totallyExposed() {
            if (input.melds().size() != 4) return false;
            for (Meld meld : input.melds()) if (meld.concealed()) return false;
            return true;
        }
    }

    private static final class Accumulator {
        private final EnumMap<Fan, List<List<Integer>>> awards = new EnumMap<>(Fan.class);
        private final EnumMap<Fan, List<List<Integer>>> exclusions = new EnumMap<>(Fan.class);
        private final EnumMap<InternalCombination, Integer> internal = new EnumMap<>(InternalCombination.class);

        void add(Fan fan) { add(fan, WHOLE_HAND); }
        void add(Fan fan, List<Integer> evidence) {
            awards.computeIfAbsent(fan, ignored -> new ArrayList<>()).add(evidence);
        }
        void addInternal(InternalCombination combination) { internal.merge(combination, 1, Integer::sum); }
        void exclude(Fan fan) { exclude(fan, WHOLE_HAND); }
        void exclude(Fan fan, List<Integer> evidence) {
            exclusions.computeIfAbsent(fan, ignored -> new ArrayList<>()).add(evidence);
        }
        boolean has(Fan fan) { return awards.containsKey(fan) && !awards.get(fan).isEmpty(); }
        void clearExclusions(Fan fan) { exclusions.remove(fan); }
        void forceOne(Fan fan) {
            exclusions.remove(fan);
            List<List<Integer>> forced = new ArrayList<>();
            forced.add(WHOLE_HAND);
            awards.put(fan, forced);
        }

        Result finish() {
            for (Map.Entry<Fan, List<List<Integer>>> entry : exclusions.entrySet()) {
                List<List<Integer>> present = awards.get(entry.getKey());
                if (present == null) continue;
                for (List<Integer> excluded : entry.getValue()) present.remove(excluded);
            }
            int points = 0;
            for (Map.Entry<Fan, List<List<Integer>>> entry : awards.entrySet()) {
                points += entry.getKey().points() * entry.getValue().size();
            }
            for (Map.Entry<InternalCombination, Integer> entry : internal.entrySet()) {
                points += entry.getKey().points() * entry.getValue();
            }
            if (points == 0) {
                add(Fan.WUFANHU);
                points = Fan.WUFANHU.points();
            }
            List<FanAward> fanAwards = new ArrayList<>();
            for (Fan fan : Fan.values()) {
                List<List<Integer>> entries = awards.get(fan);
                if (entries != null && !entries.isEmpty()) fanAwards.add(new FanAward(fan, entries.size()));
            }
            List<InternalAward> internalAwards = new ArrayList<>();
            for (InternalCombination combination : InternalCombination.values()) {
                int count = internal.getOrDefault(combination, 0);
                if (count > 0) internalAwards.add(new InternalAward(combination, count));
            }
            return new Result(points, fanAwards, internalAwards);
        }
    }

    private record Result(int points, List<FanAward> awards, List<InternalAward> internalAwards) {}

    private record Combo(Fan fan, List<Integer> groups) {}

    private static final class ComboStatus {
        final int[] parent;
        final List<Integer> selected;
        final int points;

        ComboStatus(int groupCount) {
            parent = new int[groupCount];
            for (int i = 0; i < groupCount; i++) parent[i] = i;
            selected = List.of();
            points = 0;
        }

        ComboStatus(int[] parent, List<Integer> selected, int points) {
            this.parent = parent;
            this.selected = selected;
            this.points = points;
        }

        ComboStatus add(int comboId, Combo combo) {
            int[] copy = parent.clone();
            int[] roots = new int[combo.groups().size()];
            for (int i = 0; i < roots.length; i++) {
                roots[i] = find(copy, combo.groups().get(i));
                for (int j = 0; j < i; j++) if (roots[j] == roots[i]) return null;
            }
            for (int i = 1; i < roots.length; i++) union(copy, roots[0], roots[i]);
            List<Integer> ids = new ArrayList<>(selected);
            ids.add(comboId);
            return new ComboStatus(copy, List.copyOf(ids), points + combo.fan().points());
        }

        int hash() {
            int result = 0;
            for (int i = 0; i < parent.length; i++) result = result * 5 + find(parent, i);
            return result;
        }

        private static int find(int[] values, int value) {
            int current = value;
            while (values[current] != current) current = values[current];
            while (values[value] != value) {
                int next = values[value]; values[value] = current; value = next;
            }
            return current;
        }

        private static void union(int[] values, int a, int b) {
            int rootA = find(values, a);
            int rootB = find(values, b);
            if (rootA == rootB) return;
            if (rootA < rootB) values[rootB] = rootA;
            else values[rootA] = rootB;
        }
    }

    private static void countKongAndConcealedPung(List<Group> groups, Accumulator acc) {
        List<Integer> concealedKongs = new ArrayList<>();
        List<Integer> meldedKongs = new ArrayList<>();
        List<Integer> concealedPungs = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group.isKong()) {
                (group.concealed ? concealedKongs : meldedKongs).add(i);
            } else if (group.kind == Group.Kind.PUNG && group.concealed) {
                concealedPungs.add(i);
            }
        }
        int key = concealedKongs.size() * 100 + meldedKongs.size() * 10 + concealedPungs.size();
        switch (key) {
            case 400 -> {
                acc.add(Fan.SIGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SIANKE, List.copyOf(concealedKongs));
            }
            case 310 -> {
                acc.add(Fan.SIGANG, combined(concealedKongs, meldedKongs));
                acc.add(Fan.SANANKE, List.copyOf(concealedKongs));
            }
            case 220 -> {
                acc.add(Fan.SIGANG, combined(concealedKongs, meldedKongs));
                acc.add(Fan.SHUANGANKE, List.copyOf(concealedKongs));
            }
            case 130 -> acc.add(Fan.SIGANG, combined(concealedKongs, meldedKongs));
            case 301 -> {
                acc.add(Fan.SANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SIANKE, combined(concealedKongs, concealedPungs));
            }
            case 300 -> {
                acc.add(Fan.SANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SANANKE, List.copyOf(concealedKongs));
            }
            case 211 -> {
                acc.add(Fan.SANGANG, combined(concealedKongs, meldedKongs));
                acc.add(Fan.SANANKE, combined(concealedKongs, concealedPungs));
            }
            case 210 -> {
                acc.add(Fan.SANGANG, combined(concealedKongs, meldedKongs));
                acc.add(Fan.SHUANGANKE, List.copyOf(concealedKongs));
            }
            case 121 -> {
                acc.add(Fan.SANGANG, combined(concealedKongs, meldedKongs));
                acc.add(Fan.SHUANGANKE, evidence(concealedKongs.get(0), concealedPungs.get(0)));
            }
            case 120 -> acc.add(Fan.SANGANG, combined(concealedKongs, meldedKongs));
            case 202 -> {
                acc.add(Fan.SHUANGANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SIANKE, combined(concealedKongs, concealedPungs));
            }
            case 201 -> {
                acc.add(Fan.SHUANGANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SANANKE, combined(concealedKongs, concealedPungs));
            }
            case 112 -> {
                acc.addInternal(InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG);
                acc.add(Fan.SANANKE, evidence(concealedKongs.get(0), concealedPungs.get(0), concealedPungs.get(1)));
            }
            case 111 -> {
                acc.addInternal(InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG);
                acc.add(Fan.SHUANGANKE, evidence(concealedKongs.get(0), concealedPungs.get(0)));
            }
            case 22 -> {
                acc.add(Fan.SHUANGMINGGANG, List.copyOf(meldedKongs));
                acc.add(Fan.SHUANGANKE, List.copyOf(concealedPungs));
            }
            case 103 -> {
                acc.add(Fan.ANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SIANKE, combined(concealedKongs, concealedPungs));
            }
            case 102 -> {
                acc.add(Fan.ANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SANANKE, combined(concealedKongs, concealedPungs));
            }
            case 101 -> {
                acc.add(Fan.ANGANG, List.copyOf(concealedKongs));
                acc.add(Fan.SHUANGANKE, combined(concealedKongs, concealedPungs));
            }
            case 13 -> {
                acc.add(Fan.MINGGANG, List.copyOf(meldedKongs));
                acc.add(Fan.SANANKE, List.copyOf(concealedPungs));
            }
            case 12 -> {
                acc.add(Fan.MINGGANG, List.copyOf(meldedKongs));
                acc.add(Fan.SHUANGANKE, List.copyOf(concealedPungs));
            }
            default -> {
                if (meldedKongs.size() == 4) acc.add(Fan.SIGANG, List.copyOf(meldedKongs));
                else if (concealedPungs.size() == 4) acc.add(Fan.SIANKE, List.copyOf(concealedPungs));
                else if (meldedKongs.size() == 3) acc.add(Fan.SANGANG, List.copyOf(meldedKongs));
                else if (concealedPungs.size() == 3) acc.add(Fan.SANANKE, List.copyOf(concealedPungs));
                else if (concealedKongs.size() == 2) acc.add(Fan.SHUANGANGANG, List.copyOf(concealedKongs));
                else if (meldedKongs.size() == 2) acc.add(Fan.SHUANGMINGGANG, List.copyOf(meldedKongs));
                else if (concealedPungs.size() == 2) acc.add(Fan.SHUANGANKE, List.copyOf(concealedPungs));
                else if (meldedKongs.size() == 1 && concealedKongs.size() == 1) {
                    acc.addInternal(InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG);
                } else if (concealedKongs.size() == 1) acc.add(Fan.ANGANG, List.copyOf(concealedKongs));
                else if (meldedKongs.size() == 1) acc.add(Fan.MINGGANG, List.copyOf(meldedKongs));
            }
        }
        if (acc.has(Fan.SIGANG)) {
            acc.exclude(Fan.PENGPENGHU);
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).isPair()) { acc.exclude(Fan.DANDIAOJIANG, evidence(i)); break; }
            }
        }
        if (acc.has(Fan.SHUANGANGANG)) {
            acc.exclude(Fan.SHUANGANKE, List.copyOf(concealedKongs));
        }
        if (acc.has(Fan.SIANKE)) {
            acc.exclude(Fan.PENGPENGHU);
            acc.exclude(Fan.BUQIUREN);
            acc.exclude(Fan.MENQIANQING);
        }
    }

    private static List<Integer> combined(List<Integer> first, List<Integer> second) {
        List<Integer> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return List.copyOf(result);
    }

    private static void countAssociated(List<Group> groups, Accumulator acc) {
        List<Combo> candidates = new ArrayList<>();
        List<Integer> sequences = new ArrayList<>();
        List<Integer> triplets = new ArrayList<>();
        List<Integer> pairs = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (group.isSequence()) sequences.add(i);
            else if (group.isTripletOrKong()) triplets.add(i);
            else if (group.isPair()) pairs.add(i);
        }

        List<Integer> windTriplets = new ArrayList<>();
        List<Integer> windPairs = new ArrayList<>();
        List<Integer> dragonTriplets = new ArrayList<>();
        List<Integer> dragonPairs = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            Tile tile = Tile.standard(groups.get(i).tile);
            if (tile.isWind()) (groups.get(i).isTripletOrKong() ? windTriplets : windPairs).add(i);
            if (tile.isDragon()) (groups.get(i).isTripletOrKong() ? dragonTriplets : dragonPairs).add(i);
        }
        if (windTriplets.size() == 4) candidates.add(new Combo(Fan.DASIXI, List.copyOf(windTriplets)));
        if (windTriplets.size() == 3 && windPairs.size() == 1) {
            candidates.add(new Combo(Fan.XIAOSIXI, combined(windTriplets, windPairs)));
        }
        if (windTriplets.size() == 3) candidates.add(new Combo(Fan.SANFENGKE, List.copyOf(windTriplets)));
        if (dragonTriplets.size() == 3) candidates.add(new Combo(Fan.DASANYUAN, List.copyOf(dragonTriplets)));
        if (dragonTriplets.size() == 2 && dragonPairs.size() == 1) {
            candidates.add(new Combo(Fan.XIAOSANYUAN, combined(dragonTriplets, dragonPairs)));
        }
        if (dragonTriplets.size() == 2) candidates.add(new Combo(Fan.SHUANGJIANKE, List.copyOf(dragonTriplets)));

        List<Integer> low = new ArrayList<>();
        List<Integer> high = new ArrayList<>();
        for (int id : sequences) {
            int rank = Tile.standard(groups.get(id).tile).rank();
            if (rank == 2) low.add(id);
            else if (rank == 8) high.add(id);
        }
        if (low.size() == 2 && high.size() == 2 && pairs.size() == 1
                && Tile.standard(groups.get(pairs.get(0)).tile).rank() == 5) {
            Tile lowA = Tile.standard(groups.get(low.get(0)).tile);
            Tile lowB = Tile.standard(groups.get(low.get(1)).tile);
            Tile highA = Tile.standard(groups.get(high.get(0)).tile);
            Tile highB = Tile.standard(groups.get(high.get(1)).tile);
            Tile pair = Tile.standard(groups.get(pairs.get(0)).tile);
            List<Integer> ids = evidence(low.get(0), low.get(1), high.get(0), high.get(1), pairs.get(0));
            if (lowA.suit() == lowB.suit() && lowA.suit() == highA.suit()
                    && lowA.suit() == highB.suit() && lowA.suit() == pair.suit()) {
                candidates.add(new Combo(Fan.YISESHUANGLONGHUI, ids));
            } else if (lowA.suit() != lowB.suit() && pair.suit() != lowA.suit()
                    && pair.suit() != lowB.suit()
                    && ((lowA.suit() == highA.suit() && lowB.suit() == highB.suit())
                    || (lowA.suit() == highB.suit() && lowB.suit() == highA.suit()))) {
                candidates.add(new Combo(Fan.SANSESHUANGLONGHUI, ids));
            }
        }

        for (int a = 0; a < sequences.size(); a++) {
            int i = sequences.get(a);
            for (int b = a + 1; b < sequences.size(); b++) {
                int j = sequences.get(b);
                if (!sameGroup(groups.get(i), groups.get(j))) continue;
                candidates.add(new Combo(Fan.YIBANGAO, evidence(i, j)));
                for (int c = b + 1; c < sequences.size(); c++) {
                    int k = sequences.get(c);
                    if (!sameGroup(groups.get(j), groups.get(k))) continue;
                    candidates.add(new Combo(Fan.YISESANTONGSHUN, evidence(i, j, k)));
                    for (int d = c + 1; d < sequences.size(); d++) {
                        int l = sequences.get(d);
                        if (sameGroup(groups.get(k), groups.get(l))) {
                            candidates.add(new Combo(Fan.YISESITONGSHUN, evidence(i, j, k, l)));
                        }
                    }
                }
            }
        }

        List<Integer> numberedTriplets = sortedByRank(groups, triplets, true);
        for (int a = 0; a < numberedTriplets.size(); a++) {
            int i = numberedTriplets.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            for (int b = a + 1; b < numberedTriplets.size(); b++) {
                int j = numberedTriplets.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                if (tj.rank() != ti.rank() + 1) continue;
                for (int c = b + 1; c < numberedTriplets.size(); c++) {
                    int k = numberedTriplets.get(c);
                    Tile tk = Tile.standard(groups.get(k).tile);
                    if (tk.rank() != tj.rank() + 1) continue;
                    if (allDifferent(ti.suit(), tj.suit(), tk.suit())) {
                        candidates.add(new Combo(Fan.SANSESANJIEGAO, evidence(i, j, k)));
                    } else if (ti.suit() == tj.suit() && ti.suit() == tk.suit()) {
                        candidates.add(new Combo(Fan.YISESANJIEGAO, evidence(i, j, k)));
                        for (int d = c + 1; d < numberedTriplets.size(); d++) {
                            int l = numberedTriplets.get(d);
                            Tile tl = Tile.standard(groups.get(l).tile);
                            if (tl.suit() == ti.suit() && tl.rank() == tk.rank() + 1) {
                                candidates.add(new Combo(Fan.YISESIJIEGAO, evidence(i, j, k, l)));
                            }
                        }
                    }
                }
            }
        }

        List<Integer> sortedSequences = sortedByRank(groups, sequences, false);
        for (int a = 0; a < sortedSequences.size(); a++) {
            int i = sortedSequences.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            for (int b = a + 1; b < sortedSequences.size(); b++) {
                int j = sortedSequences.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                int step1 = tj.rank() - ti.rank();
                if ((step1 != 1 && step1 != 2) || ti.suit() != tj.suit()) continue;
                for (int c = b + 1; c < sortedSequences.size(); c++) {
                    int k = sortedSequences.get(c);
                    Tile tk = Tile.standard(groups.get(k).tile);
                    int step2 = tk.rank() - tj.rank();
                    if ((step2 != 1 && step2 != 2) || tj.suit() != tk.suit()) continue;
                    if (step1 == step2) candidates.add(new Combo(Fan.YISESANBUGAO, evidence(i, j, k)));
                    for (int d = c + 1; d < sortedSequences.size(); d++) {
                        int l = sortedSequences.get(d);
                        Tile tl = Tile.standard(groups.get(l).tile);
                        int step3 = tl.rank() - tk.rank();
                        if ((step3 == 1 || step3 == 2) && tk.suit() == tl.suit() && step1 == step2 && step1 == step3) {
                            candidates.add(new Combo(Fan.YISESIBUGAO, evidence(i, j, k, l)));
                        }
                    }
                }
            }
        }
        for (int a = 0; a < sortedSequences.size(); a++) {
            int i = sortedSequences.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            for (int b = a + 1; b < sortedSequences.size(); b++) {
                int j = sortedSequences.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                if (tj.rank() != ti.rank() + 1 || ti.suit() == tj.suit()) continue;
                for (int c = b + 1; c < sortedSequences.size(); c++) {
                    int k = sortedSequences.get(c);
                    Tile tk = Tile.standard(groups.get(k).tile);
                    if (tk.rank() == tj.rank() + 1 && allDifferent(ti.suit(), tj.suit(), tk.suit())) {
                        candidates.add(new Combo(Fan.SANSESANBUGAO, evidence(i, j, k)));
                    }
                }
            }
        }

        List<Integer>[] byMiddleRank = rankBuckets(groups, sequences);
        for (int lowId : byMiddleRank[2]) {
            for (int middleId : byMiddleRank[5]) {
                for (int highId : byMiddleRank[8]) {
                    Tile lowTile = Tile.standard(groups.get(lowId).tile);
                    Tile middleTile = Tile.standard(groups.get(middleId).tile);
                    Tile highTile = Tile.standard(groups.get(highId).tile);
                    if (lowTile.suit() == middleTile.suit() && lowTile.suit() == highTile.suit()) {
                        candidates.add(new Combo(Fan.QINGLONG, evidence(lowId, middleId, highId)));
                    } else if (allDifferent(lowTile.suit(), middleTile.suit(), highTile.suit())) {
                        candidates.add(new Combo(Fan.HUALONG, evidence(lowId, middleId, highId)));
                    }
                }
            }
        }

        for (int a = 0; a < triplets.size(); a++) {
            int i = triplets.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            if (!ti.isNumbered()) continue;
            for (int b = a + 1; b < triplets.size(); b++) {
                int j = triplets.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                if (!tj.isNumbered() || tj.rank() != ti.rank()) continue;
                candidates.add(new Combo(Fan.SHUANGTONGKE, evidence(i, j)));
                for (int c = b + 1; c < triplets.size(); c++) {
                    int k = triplets.get(c);
                    Tile tk = Tile.standard(groups.get(k).tile);
                    if (tk.isNumbered() && tk.rank() == ti.rank()) {
                        candidates.add(new Combo(Fan.SANTONGKE, evidence(i, j, k)));
                    }
                }
            }
        }

        for (int a = 0; a < sequences.size(); a++) {
            int i = sequences.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            for (int b = a + 1; b < sequences.size(); b++) {
                int j = sequences.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                if (ti.rank() != tj.rank() || ti.suit() == tj.suit()) continue;
                for (int c = b + 1; c < sequences.size(); c++) {
                    int k = sequences.get(c);
                    Tile tk = Tile.standard(groups.get(k).tile);
                    if (tk.rank() == ti.rank() && allDifferent(ti.suit(), tj.suit(), tk.suit())) {
                        candidates.add(new Combo(Fan.SANSESANTONGSHUN, evidence(i, j, k)));
                    }
                }
            }
        }

        for (int a = 0; a < sequences.size(); a++) {
            int i = sequences.get(a);
            Tile ti = Tile.standard(groups.get(i).tile);
            for (int b = a + 1; b < sequences.size(); b++) {
                int j = sequences.get(b);
                Tile tj = Tile.standard(groups.get(j).tile);
                if (ti.suit() != tj.suit() && ti.rank() == tj.rank()) {
                    candidates.add(new Combo(Fan.XIXIANGFENG, evidence(i, j)));
                } else if (ti.suit() == tj.suit()) {
                    int delta = Math.abs(ti.rank() - tj.rank());
                    if (delta == 3) candidates.add(new Combo(Fan.LIANLIU, evidence(i, j)));
                    else if (delta == 6) candidates.add(new Combo(Fan.LAOSHAOFU, evidence(i, j)));
                }
            }
        }

        ComboStatus selected = selectAssociated(groups.size(), candidates);
        for (int comboId : selected.selected) {
            Combo combo = candidates.get(comboId);
            acc.add(combo.fan(), combo.groups());
            switch (combo.fan()) {
                case DASIXI -> {
                    acc.exclude(Fan.PENGPENGHU);
                    for (int id : combo.groups()) {
                        if (Tile.standard(groups.get(id).tile).isWind()) {
                            acc.exclude(Fan.QUANFENGKE, evidence(id));
                            acc.exclude(Fan.MENFENGKE, evidence(id));
                            acc.exclude(Fan.YAOJIUKE, evidence(id));
                        }
                    }
                }
                case DASANYUAN, XIAOSANYUAN, SHUANGJIANKE -> {
                    for (int id : combo.groups()) {
                        if (Tile.standard(groups.get(id).tile).isDragon()) {
                            acc.exclude(Fan.JIANKE, evidence(id));
                            acc.exclude(Fan.YAOJIUKE, evidence(id));
                        }
                    }
                }
                case XIAOSIXI, SANFENGKE -> {
                    for (int id : combo.groups()) {
                        if (Tile.standard(groups.get(id).tile).isWind()) acc.exclude(Fan.YAOJIUKE, evidence(id));
                    }
                }
                case YISESHUANGLONGHUI -> {
                    acc.exclude(Fan.QINGYISE);
                    acc.exclude(Fan.PINGHU);
                    acc.exclude(Fan.WUZI);
                }
                case YISESITONGSHUN -> {
                    acc.exclude(Fan.SIGUIYI); acc.exclude(Fan.SIGUIYI); acc.exclude(Fan.SIGUIYI);
                }
                case YISESIJIEGAO -> acc.exclude(Fan.PENGPENGHU);
                case SANSESHUANGLONGHUI -> acc.exclude(Fan.PINGHU);
                default -> { }
            }
        }
    }

    private static ComboStatus selectAssociated(int groupCount, List<Combo> candidates) {
        ComboStatus best = new ComboStatus(groupCount);
        ArrayDeque<ComboStatus> queue = new ArrayDeque<>();
        queue.add(best);
        int[] seen = new int[3_125];
        while (!queue.isEmpty()) {
            ComboStatus front = queue.removeFirst();
            for (int i = 0; i < candidates.size(); i++) {
                ComboStatus next = front.add(i, candidates.get(i));
                if (next == null) continue;
                int hash = next.hash();
                if (next.points > seen[hash]) {
                    seen[hash] = next.points;
                    queue.addLast(next);
                    if (next.points > best.points) best = next;
                }
            }
        }
        return best;
    }

    private static boolean sameGroup(Group first, Group second) {
        return first.kind == second.kind && first.tile == second.tile;
    }

    private static boolean allDifferent(Object a, Object b, Object c) {
        return a != b && a != c && b != c;
    }

    private static List<Integer> sortedByRank(List<Group> groups, List<Integer> ids, boolean numberedOnly) {
        List<Integer> result = new ArrayList<>();
        for (int id : ids) {
            if (!numberedOnly || Tile.standard(groups.get(id).tile).isNumbered()) result.add(id);
        }
        result.sort((left, right) -> {
            Tile a = Tile.standard(groups.get(left).tile);
            Tile b = Tile.standard(groups.get(right).tile);
            int rank = Integer.compare(a.rank(), b.rank());
            return rank != 0 ? rank : Integer.compare(a.suit().ordinal(), b.suit().ordinal());
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer>[] rankBuckets(List<Group> groups, List<Integer> ids) {
        List<Integer>[] result = (List<Integer>[]) new List<?>[10];
        for (int i = 0; i < result.length; i++) result[i] = new ArrayList<>();
        for (int id : ids) result[Tile.standard(groups.get(id).tile).rank()].add(id);
        return result;
    }
    private static void countSingleGroup(HandData hand, List<Group> groups, Accumulator acc) {
        Tile roundWind = hand.context.roundWind().tile();
        Tile seatWind = hand.context.seatWind().tile();
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            if (!group.isTripletOrKong()) continue;
            Tile tile = Tile.standard(group.tile);
            if (tile.isDragon()) {
                acc.add(Fan.JIANKE, evidence(i));
                acc.exclude(Fan.YAOJIUKE, evidence(i));
            }
            if (tile == roundWind) {
                acc.add(Fan.QUANFENGKE, evidence(i));
                acc.exclude(Fan.YAOJIUKE, evidence(i));
            }
            if (tile == seatWind) {
                acc.add(Fan.MENFENGKE, evidence(i));
                acc.exclude(Fan.YAOJIUKE, evidence(i));
            }
            if (tile.isTerminalOrHonor()) acc.add(Fan.YAOJIUKE, evidence(i));
        }
    }

    private static void countWinMode(HandData hand, List<Group> groups, int knittedPattern,
                                     boolean winningInKnitted, int waitCount, Accumulator acc) {
        boolean selfDraw = hand.context.method() == WinMethod.SELF_DRAW;
        if (hand.context.has(WinFlag.LAST_TILE) && selfDraw) {
            acc.add(Fan.MIAOSHOUHUICHUN);
            acc.exclude(Fan.ZIMO);
        }
        if (hand.context.has(WinFlag.LAST_TILE) && !selfDraw) acc.add(Fan.HAIDILAOYUE);
        if (hand.context.has(WinFlag.AFTER_KONG)) {
            acc.add(Fan.GANGSHANGKAIHUA);
            acc.exclude(Fan.ZIMO);
        }
        if (hand.context.has(WinFlag.ROBBING_KONG)) {
            acc.add(Fan.QIANGGANGHU);
            acc.exclude(Fan.HUJUEZHANG);
        }
        if (hand.totallyExposed() && !selfDraw) {
            acc.add(Fan.QUANQIUREN);
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).isPair()) { acc.exclude(Fan.DANDIAOJIANG, evidence(i)); break; }
            }
        }
        if (hand.menqing() && selfDraw) {
            acc.add(Fan.BUQIUREN);
            acc.exclude(Fan.MENQIANQING);
            acc.exclude(Fan.ZIMO);
        }
        if (hand.context.has(WinFlag.LAST_OF_KIND)) {
            acc.add(Fan.HUJUEZHANG);
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).isPair()) { acc.exclude(Fan.DANDIAOJIANG, evidence(i)); break; }
            }
        }
        if (hand.menqing()) acc.add(Fan.MENQIANQING);

        boolean tileBelongsToKnitted = knittedPattern >= 0
                && contains(TilePatterns.KNITTED[knittedPattern], hand.input.winningTile().index());
        if (waitCount == 1 && !tileBelongsToKnitted) {
            for (int i = 0; i < groups.size(); i++) {
                Group group = groups.get(i);
                if (!group.containsWinning) continue;
                Tile middle = Tile.standard(group.tile);
                Tile winning = hand.input.winningTile();
                if (group.isPair()) {
                    acc.add(Fan.DANDIAOJIANG, evidence(i));
                } else if (group.isSequence()) {
                    if ((middle.rank() == 2 && winning.rank() == 3)
                            || (middle.rank() == 8 && winning.rank() == 7)) {
                        acc.add(Fan.BIANZHANG, evidence(i));
                    } else if (middle == winning) {
                        acc.add(Fan.KANZHANG, evidence(i));
                    }
                }
                break;
            }
        }
        if (selfDraw) {
            acc.add(Fan.ZIMO);
            if ((acc.has(Fan.JIULIANBAODENG) || acc.has(Fan.SIANKE))
                    && !acc.has(Fan.MIAOSHOUHUICHUN) && !acc.has(Fan.GANGSHANGKAIHUA)) {
                acc.clearExclusions(Fan.ZIMO);
            }
        }
    }

    private static boolean contains(int[] values, int target) {
        for (int value : values) if (value == target) return true;
        return false;
    }
}
