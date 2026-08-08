package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McrInputValidationTest {
    private final McrRulesEngine engine = new StandardMcrRulesEngine();

    @Test
    void shapeApiRejectsStructurallyInvalidInputAndImpossibleFifthTile() {
        assertThrows(IllegalArgumentException.class,
                () -> engine.isWinningShape(TileCounts.parse("W1"), List.of(), Tile.M2));
        assertThrows(IllegalArgumentException.class,
                () -> engine.isWinningShape(tenTiles(), Arrays.asList((Meld) null), Tile.P6));

        TileCounts fourCopies = TileCounts.parse(
                "W1", "W1", "W1", "W1", "W2", "W3", "W4", "B2", "B3", "B4", "T2", "T2", "T2");
        assertFalse(engine.isWinningShape(fourCopies, List.of(), Tile.M1));
    }

    @Test
    void waitInputValidatesFlowersEvenWhenNoCandidateWins() {
        assertThrows(IllegalArgumentException.class, () -> new WaitInput(
                TileCounts.parse("W1", "W1", "W1", "W2", "W2", "W2", "W3", "W3", "W3",
                        "W4", "W4", "W5", "W7"),
                List.of(), Wind.EAST, Wind.EAST, Set.of(), List.of(Tile.M1)));
        assertThrows(IllegalArgumentException.class, () -> new WaitInput(
                TileCounts.parse("W1", "W1", "W1", "W2", "W2", "W2", "W3", "W3", "W3",
                        "W4", "W4", "W5", "W7"),
                List.of(), Wind.EAST, Wind.EAST, Set.of(), List.of(Tile.PLUM, Tile.PLUM)));
    }

    @Test
    void afterKongRequiresAnActualKong() {
        WinContext context = new WinContext(Wind.EAST, Wind.EAST, WinMethod.SELF_DRAW,
                Set.of(WinFlag.AFTER_KONG), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WinInput(tenTiles(), List.of(Meld.chow(Tile.S5)), Tile.P6, context));
        assertDoesNotThrow(
                () -> new WinInput(tenTiles(), List.of(Meld.openKong(Tile.S5)), Tile.P6, context));
    }

    @Test
    void lastOfKindAndRobbingKongRejectImpossibleOwnedCopies() {
        TileCounts withWinningCopy = TileCounts.parse(
                "W1", "W1", "W2", "W3", "W4", "W5", "W6", "W7", "B1", "B1", "B1", "T2", "T2");
        WinContext lastOfKind = new WinContext(Wind.EAST, Wind.EAST, WinMethod.DISCARD,
                Set.of(WinFlag.LAST_OF_KIND), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WinInput(withWinningCopy, List.of(), Tile.M1, lastOfKind));

        WinContext robbing = new WinContext(Wind.EAST, Wind.EAST, WinMethod.DISCARD,
                Set.of(WinFlag.ROBBING_KONG), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new WinInput(withWinningCopy, List.of(), Tile.M1, robbing));
    }

    @Test
    void lastWallTileCannotAlsoBeRobbedKong() {
        assertThrows(IllegalArgumentException.class, () -> new WinContext(
                Wind.EAST, Wind.EAST, WinMethod.DISCARD,
                Set.of(WinFlag.LAST_TILE, WinFlag.ROBBING_KONG), List.of()));
    }

    @Test
    void evaluationTotalsMustMatchTypedAwards() {
        assertEquals(0, WinEvaluation.class.getConstructors().length,
                "scores must be issued by the engine, not caller-constructed");
        assertThrows(IllegalArgumentException.class, () -> new WinEvaluation(
                EvaluationStatus.COMPLETE, Tile.M1, WinMethod.DISCARD,
                88, 0, 88, List.of(), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WinEvaluation(
                EvaluationStatus.UNSUPPORTED, Tile.M1, WinMethod.DISCARD,
                88, 0, 88, List.of(new FanAward(Fan.DASIXI, 1)), List.of(), List.of("unsupported")));
    }

    private static TileCounts tenTiles() {
        return TileCounts.parse("B2", "B2", "B2", "W2", "W2", "W2", "B4", "B5", "B6", "B6");
    }
}
