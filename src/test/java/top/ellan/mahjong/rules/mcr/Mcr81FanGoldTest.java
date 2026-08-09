package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository-authored certification corpus for all 81 MCR scoring elements.
 *
 * <p>The normative source is the WMO Green Book, not the legacy native engine.
 * Every scoring element has a positive hand, a deterministic near miss, and an
 * explicit non-duplication assertion where the Green Book suppresses a lower
 * scoring element.</p>
 */
class Mcr81FanGoldTest {
    private static final String CORPUS = "/mcr-81-green-book-gold.tsv";
    private static final String GREEN_BOOK =
            "https://mahjong-europe.org/portal/images/docs/mcr_EN.pdf";
    private static final LocalDate SOURCE_READ_DATE = LocalDate.of(2026, 8, 8);
    private static final int OFFICIAL_FAN_COUNT = 81;

    private final McrRulesEngine engine = new StandardMcrRulesEngine();

    @Test
    void corpusIsCompleteUniqueAndInGreenBookOrder() throws IOException {
        List<GoldCase> cases = loadCases();
        assertEquals(OFFICIAL_FAN_COUNT, cases.size());
        assertEquals(OFFICIAL_FAN_COUNT, Fan.values().length);
        assertEquals(OFFICIAL_FAN_COUNT,
                cases.stream().map(GoldCase::fan).collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(List.of(Fan.values()), cases.stream().map(GoldCase::fan).toList());
        assertEquals(Set.copyOf(List.of(Fan.values())), exclusions().keySet(),
                "every official fan must have explicit non-duplication metadata");
        assertTrue(GREEN_BOOK.startsWith("https://"));
        assertEquals(LocalDate.of(2026, 8, 8), SOURCE_READ_DATE);
    }

    @TestFactory
    Stream<DynamicTest> everyOfficialFanHasPositiveNearMissAndExclusionGold() throws IOException {
        Map<Fan, Set<Fan>> exclusions = exclusions();
        return loadCases().stream().map(gold -> DynamicTest.dynamicTest(
                "%02d %s (%d fan)".formatted(gold.greenBookNumber(), gold.fan(), gold.fan().points()),
                () -> verifyGoldCase(gold, exclusions.get(gold.fan()))));
    }

    private void verifyGoldCase(GoldCase gold, Set<Fan> excludedFans) {
        WinInput positive = McrGoldNotation.win(gold.positiveFixture());
        WinEvaluation result = engine.evaluate(positive);

        assertTrue(result.winningShape(), () -> gold.citation() + ": " + result.violations());
        assertTrue(result.hasFan(gold.fan()),
                () -> gold.citation() + " did not award " + gold.fan() + ": " + result.awards());
        for (Fan excluded : excludedFans) {
            assertFalse(result.hasFan(excluded),
                    () -> gold.citation() + " must not additionally award " + excluded
                            + ": " + result.awards());
        }

        WinInput nearMiss = nearMiss(gold.fan(), positive);
        assertNotNull(nearMiss, () -> "no one-step winning near miss found for " + gold.fan());
        WinEvaluation negative = engine.evaluate(nearMiss);
        assertTrue(negative.winningShape(),
                () -> "near miss must remain a winning shape for " + gold.fan());
        assertFalse(negative.hasFan(gold.fan()),
                () -> "near miss still awards " + gold.fan() + ": " + negative.awards());
    }

    private WinInput nearMiss(Fan fan, WinInput positive) {
        return switch (fan) {
            case ZIMO, BUQIUREN -> withMethod(positive, WinMethod.DISCARD);
            case MENQIANQING, QUANQIUREN -> withMethod(positive, WinMethod.SELF_DRAW);
            case MIAOSHOUHUICHUN, HAIDILAOYUE -> withoutFlag(positive, WinFlag.LAST_TILE);
            case GANGSHANGKAIHUA -> withoutFlag(positive, WinFlag.AFTER_KONG);
            case QIANGGANGHU -> withoutFlag(positive, WinFlag.ROBBING_KONG);
            case HUJUEZHANG -> withoutFlag(positive, WinFlag.LAST_OF_KIND);
            case QUANFENGKE -> withRoundWind(positive, Wind.NORTH);
            case MENFENGKE -> withSeatWind(positive, Wind.SOUTH);
            case HUAPAI -> withoutFlowers(positive);
            default -> oneTileWinningNearMiss(fan, positive);
        };
    }

    private WinInput oneTileWinningNearMiss(Fan target, WinInput positive) {
        List<Tile> original = allConcealedTiles(positive);
        for (int removeAt = 0; removeAt < original.size(); removeAt++) {
            Tile removed = original.get(removeAt);
            for (int replacementIndex = 0; replacementIndex < Tile.STANDARD_KIND_COUNT;
                    replacementIndex++) {
                Tile replacement = Tile.standard(replacementIndex);
                if (replacement == removed) continue;
                List<Tile> mutated = new ArrayList<>(original);
                mutated.set(removeAt, replacement);
                WinInput candidate = winningDesignationWithout(target, positive, mutated);
                if (candidate != null) return candidate;
            }
        }
        return null;
    }

    private WinInput winningDesignationWithout(Fan target, WinInput template, List<Tile> tiles) {
        Set<TileCounts> visited = new HashSet<>();
        for (int winningAt = 0; winningAt < tiles.size(); winningAt++) {
            List<Tile> concealed = new ArrayList<>(tiles);
            Tile winning = concealed.remove(winningAt);
            TileCounts counts;
            try {
                counts = TileCounts.of(concealed);
            } catch (IllegalArgumentException impossibleCounts) {
                continue;
            }
            if (!visited.add(counts)) continue;
            try {
                WinInput candidate = new WinInput(counts, template.melds(), winning, template.context());
                WinEvaluation result = engine.evaluate(candidate);
                if (result.winningShape() && !result.hasFan(target)) return candidate;
            } catch (IllegalArgumentException impossiblePhysicalHand) {
                // Try the next deterministic mutation/designation.
            }
        }
        return null;
    }

    private static List<Tile> allConcealedTiles(WinInput input) {
        List<Tile> result = new ArrayList<>(input.concealed().total() + 1);
        for (int index = 0; index < Tile.STANDARD_KIND_COUNT; index++) {
            Tile tile = Tile.standard(index);
            for (int count = 0; count < input.concealed().count(tile); count++) result.add(tile);
        }
        result.add(input.winningTile());
        return result;
    }

    private static WinInput withMethod(WinInput input, WinMethod method) {
        WinContext old = input.context();
        return withContext(input, new WinContext(old.seatWind(), old.roundWind(), method,
                old.flags(), old.flowers()));
    }

    private static WinInput withoutFlag(WinInput input, WinFlag removed) {
        WinContext old = input.context();
        EnumSet<WinFlag> flags = EnumSet.noneOf(WinFlag.class);
        flags.addAll(old.flags());
        flags.remove(removed);
        return withContext(input, new WinContext(old.seatWind(), old.roundWind(), old.method(),
                flags, old.flowers()));
    }

    private static WinInput withRoundWind(WinInput input, Wind wind) {
        WinContext old = input.context();
        return withContext(input, new WinContext(old.seatWind(), wind, old.method(),
                old.flags(), old.flowers()));
    }

    private static WinInput withSeatWind(WinInput input, Wind wind) {
        WinContext old = input.context();
        return withContext(input, new WinContext(wind, old.roundWind(), old.method(),
                old.flags(), old.flowers()));
    }

    private static WinInput withoutFlowers(WinInput input) {
        WinContext old = input.context();
        return withContext(input, new WinContext(old.seatWind(), old.roundWind(), old.method(),
                old.flags(), List.of()));
    }

    private static WinInput withContext(WinInput input, WinContext context) {
        return new WinInput(input.concealed(), input.melds(), input.winningTile(), context);
    }

    private static List<GoldCase> loadCases() throws IOException {
        InputStream stream = Objects.requireNonNull(
                Mcr81FanGoldTest.class.getResourceAsStream(CORPUS), "missing " + CORPUS);
        List<GoldCase> result = new ArrayList<>(OFFICIAL_FAN_COUNT);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            int greenBookNumber = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] columns = line.split("\\t", -1);
                if (columns.length != 2 || columns[0].isBlank() || columns[1].isBlank()) {
                    throw new IOException("invalid MCR gold line: " + line);
                }
                greenBookNumber++;
                result.add(new GoldCase(Fan.valueOf(columns[0]), columns[1], greenBookNumber));
            }
        }
        return List.copyOf(result);
    }

    private static Map<Fan, Set<Fan>> exclusions() {
        EnumMap<Fan, Set<Fan>> result = new EnumMap<>(Fan.class);
        for (Fan fan : Fan.values()) result.put(fan, Set.of());

        exclude(result, Fan.DASIXI, Fan.PENGPENGHU, Fan.QUANFENGKE, Fan.MENFENGKE, Fan.YAOJIUKE);
        exclude(result, Fan.DASANYUAN, Fan.JIANKE, Fan.YAOJIUKE);
        exclude(result, Fan.LVYISE, Fan.HUNYISE);
        exclude(result, Fan.JIULIANBAODENG,
                Fan.QINGYISE, Fan.BUQIUREN, Fan.MENQIANQING, Fan.WUZI, Fan.YAOJIUKE);
        exclude(result, Fan.SIGANG, Fan.PENGPENGHU, Fan.DANDIAOJIANG);
        exclude(result, Fan.LIANQIDUI, Fan.QINGYISE, Fan.WUZI, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.SHISANYAO, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.QINGYAOJIU,
                Fan.PENGPENGHU, Fan.QUANDAIYAO, Fan.WUZI, Fan.SHUANGTONGKE, Fan.YAOJIUKE);
        exclude(result, Fan.XIAOSIXI, Fan.YAOJIUKE);
        exclude(result, Fan.XIAOSANYUAN, Fan.JIANKE, Fan.YAOJIUKE);
        exclude(result, Fan.ZIYISE, Fan.PENGPENGHU, Fan.QUANDAIYAO, Fan.YAOJIUKE);
        exclude(result, Fan.SIANKE, Fan.PENGPENGHU, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.YISESHUANGLONGHUI, Fan.QINGYISE, Fan.PINGHU, Fan.WUZI);
        exclude(result, Fan.YISESITONGSHUN, Fan.SIGUIYI);
        exclude(result, Fan.YISESIJIEGAO, Fan.PENGPENGHU);
        exclude(result, Fan.HUNYAOJIU, Fan.PENGPENGHU, Fan.QUANDAIYAO, Fan.YAOJIUKE);
        exclude(result, Fan.QIDUI, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.QIXINGBUKAO, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.QUANSHUANGKE, Fan.PENGPENGHU, Fan.DUANYAO, Fan.WUZI);
        exclude(result, Fan.QINGYISE, Fan.WUZI);
        exclude(result, Fan.QUANDA, Fan.DAYUWU, Fan.WUZI);
        exclude(result, Fan.QUANZHONG, Fan.DUANYAO, Fan.WUZI);
        exclude(result, Fan.QUANXIAO, Fan.XIAOYUWU, Fan.WUZI);
        exclude(result, Fan.SANSESHUANGLONGHUI, Fan.PINGHU);
        exclude(result, Fan.QUANDAIWU, Fan.DUANYAO, Fan.WUZI);
        exclude(result, Fan.QUANBUKAO, Fan.BUQIUREN, Fan.MENQIANQING);
        exclude(result, Fan.DAYUWU, Fan.WUZI);
        exclude(result, Fan.XIAOYUWU, Fan.WUZI);
        exclude(result, Fan.TUIBUDAO, Fan.QUEYIMEN);
        exclude(result, Fan.MIAOSHOUHUICHUN, Fan.ZIMO);
        exclude(result, Fan.GANGSHANGKAIHUA, Fan.ZIMO);
        exclude(result, Fan.QIANGGANGHU, Fan.HUJUEZHANG);
        exclude(result, Fan.QUANQIUREN, Fan.DANDIAOJIANG);
        exclude(result, Fan.SHUANGANGANG, Fan.SHUANGANKE);
        exclude(result, Fan.BUQIUREN, Fan.MENQIANQING, Fan.ZIMO);
        exclude(result, Fan.HUJUEZHANG, Fan.DANDIAOJIANG);
        exclude(result, Fan.JIANKE, Fan.YAOJIUKE);
        exclude(result, Fan.QUANFENGKE, Fan.YAOJIUKE);
        exclude(result, Fan.MENFENGKE, Fan.YAOJIUKE);
        exclude(result, Fan.PINGHU, Fan.WUZI);
        exclude(result, Fan.DUANYAO, Fan.WUZI);
        return Map.copyOf(result);
    }

    private static void exclude(EnumMap<Fan, Set<Fan>> exclusions, Fan fan, Fan... suppressed) {
        exclusions.put(fan, Set.of(suppressed));
    }

    private record GoldCase(Fan fan, String positiveFixture, int greenBookNumber) {
        private String citation() {
            return "WMO Green Book scoring element " + greenBookNumber + " (" + GREEN_BOOK
                    + ", read " + SOURCE_READ_DATE + ')';
        }
    }
}
