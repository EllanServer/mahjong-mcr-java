package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential corpus migrated from GB-Mahjong's MIT-licensed unit_test.cpp.
 * The old C++ result is an oracle for migration regressions, not a normative
 * ruling: deliberate divergences are documented in RULE_COVERAGE.md.
 */
class LegacyCppDifferentialRegressionTest {
    private final McrRulesEngine engine = new StandardMcrRulesEngine();

    @TestFactory
    Stream<DynamicTest> migratedScoringCorpus() {
        return scoreCases().stream().map(test -> DynamicTest.dynamicTest(test.fixture(), () -> {
            WinEvaluation result = engine.evaluate(McrGoldNotation.win(test.fixture()));
            assertTrue(result.winningShape(), result.violations().toString());
            assertEquals(expectedMultiset(test.fans()), awardMultiset(result.awards()),
                    () -> "C++ differential mismatch: " + result.awards());
        }));
    }

    @TestFactory
    Stream<DynamicTest> migratedFormalWaitCorpus() {
        return waitCases().stream().map(test -> DynamicTest.dynamicTest(test.fixture(), () -> {
            McrGoldNotation.ParsedWait input = McrGoldNotation.wait(test.fixture());
            assertEquals(Set.copyOf(test.tiles()), Set.copyOf(formalPhysicalWaits(input)),
                    "formal wait mismatch");
        }));
    }

    @Test
    void oldFlowerOnlyMinimumBehaviorIsDeliberatelyRejected() {
        WinEvaluation result = engine.evaluate(McrGoldNotation.win(
                "[123m,1][345m,1]678p56sWW7s|EE0000|2"));
        assertTrue(result.winningShape());
        assertEquals(2, result.flowerFan());
        assertEquals(8, result.qualifyingFan());
        assertTrue(result.legalWin(), "this particular fixture already has WUFANHU; flowers remain separate");

        WinEvaluation flowersCannotQualify = engine.evaluate(new WinInput(
                TileCounts.parse("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6"),
                List.of(Meld.chow(Tile.S5)), Tile.P6,
                new WinContext(Wind.EAST, Wind.EAST, WinMethod.DISCARD, Set.of(),
                        List.of(Tile.PLUM, Tile.ORCHID, Tile.BAMBOO_FLOWER, Tile.CHRYSANTHEMUM,
                                Tile.SPRING, Tile.SUMMER, Tile.AUTUMN, Tile.WINTER))));
        assertFalse(flowersCannotQualify.legalWin());
    }

    private List<Tile> formalPhysicalWaits(McrGoldNotation.ParsedWait input) {
        int[] physical = new int[Tile.STANDARD_KIND_COUNT];
        for (int i = 0; i < physical.length; i++) physical[i] = input.concealed().count(Tile.standard(i));
        for (Meld meld : input.melds()) {
            if (meld.type() == MeldType.CHOW) {
                physical[meld.tile().index() - 1]++;
                physical[meld.tile().index()]++;
                physical[meld.tile().index() + 1]++;
            } else {
                physical[meld.tile().index()] += meld.type() == MeldType.KONG ? 4 : 3;
            }
        }
        List<Tile> result = new ArrayList<>();
        for (int i = 0; i < physical.length; i++) {
            Tile tile = Tile.standard(i);
            if (physical[i] < 4 && engine.isWinningShape(input.concealed(), input.melds(), tile)) result.add(tile);
        }
        return result;
    }

    private static Map<Fan, Integer> expectedMultiset(List<Fan> fans) {
        EnumMap<Fan, Integer> result = new EnumMap<>(Fan.class);
        for (Fan fan : fans) result.merge(fan, 1, Integer::sum);
        return result;
    }

    private static Map<Fan, Integer> awardMultiset(List<FanAward> awards) {
        EnumMap<Fan, Integer> result = new EnumMap<>(Fan.class);
        for (FanAward award : awards) result.merge(award.fan(), award.count(), Integer::sum);
        return result;
    }

    private static List<ScoreCase> scoreCases() {
        return List.of(
                score("[EEE,2][SSSS,1]WWWNN55pN|EE1000", Fan.DASIXI, Fan.HUNYISE, Fan.SHUANGANKE, Fan.MINGGANG, Fan.ZIMO),
                score("[EEE,2][SSSS,1]WWWNN555p|SN1000", Fan.XIAOSIXI, Fan.HUNYISE, Fan.PENGPENGHU, Fan.QUANFENGKE, Fan.SHUANGANKE, Fan.MINGGANG, Fan.ZIMO),
                score("[EEE,2][SSS,1]WWW78m55p9m|NN1000", Fan.SANFENGKE, Fan.QUEYIMEN, Fan.ZIMO),
                score("[PPP,2][FFF,3]CC66999sC|EE1000", Fan.DASANYUAN, Fan.HUNYISE, Fan.PENGPENGHU, Fan.SHUANGANKE, Fan.YAOJIUKE, Fan.ZIMO),
                score("[PPP,2][FFF,3]345p666sCC|EE1000", Fan.XIAOSANYUAN, Fan.QUEYIMEN, Fan.DANDIAOJIANG, Fan.ZIMO),
                score("EESSWWNNPPFFCC|EE1000", Fan.ZIYISE, Fan.QIDUI, Fan.ZIMO),
                score("[111m,2]111999p11991s|EE1000", Fan.QINGYAOJIU, Fan.SANANKE, Fan.SANTONGKE, Fan.ZIMO),
                score("[NNN,3][999s,1][CCC,2]999m11p|ES1000", Fan.HUNYAOJIU, Fan.WUMENQI, Fan.SHUANGTONGKE, Fan.JIANKE, Fan.DANDIAOJIANG, Fan.ZIMO),
                score("[123p,1][CCC,1]999m79998p|EE1000", Fan.QUANDAIYAO, Fan.JIANKE, Fan.QUEYIMEN, Fan.LAOSHAOFU, Fan.YAOJIUKE, Fan.ZIMO),
                score("[345m,1]22456p222567s", Fan.SANSESANBUGAO, Fan.DUANYAO),
                score("[123s,1][333s,2]45678996s|EE1000", Fan.QINGYISE, Fan.SIGUIYI, Fan.LIANLIU, Fan.ZIMO),
                score("[CCC,2][789m,1]12345mNN6m|EE1000", Fan.HUNYISE, Fan.QINGLONG, Fan.JIANKE, Fan.ZIMO),
                score("12345789p55678m6p|EE1000", Fan.QINGLONG, Fan.BUQIUREN, Fan.PINGHU, Fan.QUEYIMEN),
                score("[FFF,2]147m258p39sWW6s", Fan.ZUHELONG, Fan.WUMENQI, Fan.JIANKE),
                score("[789m,1][999s,2]7899p789s9p|EE1000", Fan.QUANDA, Fan.SANSESANTONGSHUN, Fan.QUANDAIYAO, Fan.SIGUIYI, Fan.YAOJIUKE, Fan.ZIMO),
                score("[444m,2][666m,3]444p44664s", Fan.QUANZHONG, Fan.QUANSHUANGKE, Fan.SANTONGKE),
                score("11223333p33s1122m|EE1000", Fan.QUANXIAO, Fan.QIDUI, Fan.SIGUIYI, Fan.ZIMO),
                score("[666m,2][777m,2][999m,2]78886m|EE0100", Fan.QINGYISE, Fan.DAYUWU, Fan.HUJUEZHANG, Fan.SIGUIYI, Fan.SIGUIYI, Fan.YAOJIUKE),
                score("[123p,3]23334p222444s|EE1000", Fan.XIAOYUWU, Fan.TUIBUDAO, Fan.SHUANGANKE, Fan.SIGUIYI, Fan.ZIMO),
                score("[345m,1]567m45556p345s", Fan.QUANDAIWU, Fan.SANSESANBUGAO, Fan.PINGHU, Fan.XIXIANGFENG),
                score("[111s,3][222s,3][444s,2]333s22p", Fan.YISESIJIEGAO, Fan.XIAOYUWU, Fan.QUEYIMEN, Fan.YAOJIUKE, Fan.DANDIAOJIANG),
                score("[666p,2]77888p678sWW7p", Fan.YISESANJIEGAO, Fan.QUEYIMEN),
                score("[444s,2]333m55pWWCCC5p|EE1000", Fan.SANANKE, Fan.SANSESANJIEGAO, Fan.WUMENQI, Fan.PENGPENGHU, Fan.JIANKE, Fan.ZIMO),
                score("[456s,1][456s,1][456s,3]45s55m6s|EE1100", Fan.YISESITONGSHUN, Fan.QUANZHONG, Fan.QUANDAIWU, Fan.HUJUEZHANG, Fan.PINGHU, Fan.QUEYIMEN, Fan.ZIMO),
                score("[234s,1]22333444sFF2s|EE1000", Fan.LVYISE, Fan.YISESITONGSHUN, Fan.ZIMO),
                score("[123m,1][345m,1]67789mCC5m|EE1000", Fan.YISESIBUGAO, Fan.HUNYISE, Fan.ZIMO),
                score("[123m,1][345m,1]567m34sCC5s|EE1000", Fan.YISESANBUGAO, Fan.XIXIANGFENG, Fan.QUEYIMEN, Fan.ZIMO),
                score("12345566789s22p4s|EE1000", Fan.QINGLONG, Fan.BUQIUREN, Fan.PINGHU, Fan.YIBANGAO, Fan.QUEYIMEN),
                // Upstream accepts either Mixed Double Chow or Short Straight here; both score one.
                score("123456m456p11789s", Fan.HUALONG, Fan.MENQIANQING, Fan.PINGHU, Fan.LIANLIU),
                score("19m19p119sESWNPFC|EE1000", Fan.SHISANYAO, Fan.ZIMO),
                score("147m28p69sESWNPF3s|EE1000", Fan.QUANBUKAO, Fan.ZIMO),
                score("14m369p25sESWNPFC|EE1000", Fan.QIXINGBUKAO, Fan.ZIMO),
                score("369m147p25sSWNPF8s", Fan.QUANBUKAO, Fan.ZUHELONG),
                score("22334556677884s", Fan.LIANQIDUI, Fan.DUANYAO),
                score("[123p,1]12355778998p|EE1000", Fan.YISESHUANGLONGHUI, Fan.KANZHANG, Fan.ZIMO),
                score("[123m,1][789m,1][123p,1]89p55s7p|EE1000", Fan.SANSESHUANGLONGHUI, Fan.BIANZHANG, Fan.ZIMO),
                score("11123456789999m|EE1000", Fan.JIULIANBAODENG, Fan.QINGLONG, Fan.SIGUIYI, Fan.ZIMO),
                score("[123m,1][345m,1]678p56sWW7s", Fan.WUFANHU),
                score("55s33344455566m5s", Fan.YISESANJIEGAO, Fan.SANANKE, Fan.PENGPENGHU, Fan.MENQIANQING, Fan.SHUANGTONGKE, Fan.DUANYAO, Fan.QUEYIMEN),
                score("123789s123789p33m", Fan.MENQIANQING, Fan.PINGHU, Fan.XIXIANGFENG, Fan.XIXIANGFENG, Fan.LAOSHAOFU, Fan.DANDIAOJIANG),
                score("12334556778911s", Fan.YISESIBUGAO, Fan.QINGYISE, Fan.MENQIANQING, Fan.PINGHU),
                score("[1111s][2222m][3333p][4444p]88s", Fan.SIGANG, Fan.SIANKE, Fan.SANSESANJIEGAO, Fan.YAOJIUKE, Fan.WUZI),
                score("[1111s][2222m][4444p]333p88s", Fan.SANGANG, Fan.SIANKE, Fan.SANSESANJIEGAO, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s][2222m]333444p88s", Fan.SIANKE, Fan.SANSESANJIEGAO, Fan.SHUANGANGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s][2222m,1]333444p88s", Fan.SANANKE, Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s][2222m,1][333p,1]444p88s", Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.SHUANGANKE, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s,1][2222m,1]333444p88s", Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.SHUANGMINGGANG, Fan.SHUANGANKE, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s]222m333444p88s", Fan.SIANKE, Fan.SANSESANJIEGAO, Fan.ANGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s,1]222m333444p88s", Fan.SANANKE, Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.MINGGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s][2222m][333p,1][444p,1]88s", Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.SHUANGANGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s][222m,1][333p,1][444p,1]88s", Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.ANGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111s,1][222m,1][333p,1]444p88s", Fan.SANSESANJIEGAO, Fan.PENGPENGHU, Fan.MINGGANG, Fan.YAOJIUKE, Fan.WUZI, Fan.DANDIAOJIANG),
                score("[1111m][222s,1]78999s44p9s", Fan.SIGUIYI, Fan.SHUANGANKE, Fan.ANGANG, Fan.YAOJIUKE, Fan.YAOJIUKE, Fan.WUZI),
                score("[123p,3]55m12379s789p8s", Fan.SANSESHUANGLONGHUI, Fan.KANZHANG),
                score("[678s,3]147m369s25pSS8p", Fan.ZUHELONG)
        );
    }

    private static List<WaitCase> waitCases() {
        return List.of(
                wait("[CCCC][FFFF][PPPP][NNNN]E", Tile.EAST),
                wait("19m19s19pESWNCFP", Tile.M1, Tile.M9, Tile.S1, Tile.S9, Tile.P1, Tile.P9,
                        Tile.EAST, Tile.SOUTH, Tile.WEST, Tile.NORTH,
                        Tile.RED_DRAGON, Tile.GREEN_DRAGON, Tile.WHITE_DRAGON),
                wait("19m19s19pESWNNFP", Tile.RED_DRAGON),
                wait("22559m11sEESSPP", Tile.M9),
                wait("47m28s369pESWCFP", Tile.M1, Tile.S5, Tile.NORTH),
                wait("28m47s369pESWCFP", Tile.S1, Tile.M5, Tile.NORTH),
                wait("28m47s369pESWCCP"),
                wait("1112345678999s", Tile.S1, Tile.S2, Tile.S3, Tile.S4, Tile.S5, Tile.S6, Tile.S7, Tile.S8, Tile.S9),
                wait("[111s,1]2345678999s", Tile.S1, Tile.S2, Tile.S4, Tile.S5, Tile.S7, Tile.S8),
                wait("1112345689999s", Tile.S7),
                wait("1112346778999s", Tile.S5, Tile.S7, Tile.S8),
                wait("3344455566667m", Tile.M2, Tile.M3, Tile.M4, Tile.M5, Tile.M7, Tile.M8),
                wait("234m45s88899pEEE", Tile.S3, Tile.S6),
                wait("234m35s88899pEEE", Tile.S4),
                wait("234m345s8889pEEE", Tile.P7, Tile.P9),
                wait("234m345s3399pEEE", Tile.P3, Tile.P9),
                wait("34444556789pPP", Tile.P7, Tile.WHITE_DRAGON),
                wait("23344445m888pWW", Tile.M1, Tile.WEST),
                wait("234m345s333pEEEE")
        );
    }

    private static ScoreCase score(String fixture, Fan... fans) {
        return new ScoreCase(fixture, List.of(fans));
    }

    private static WaitCase wait(String fixture, Tile... tiles) {
        return new WaitCase(fixture, List.of(tiles));
    }

    private record ScoreCase(String fixture, List<Fan> fans) {}
    private record WaitCase(String fixture, List<Tile> tiles) {}
}
