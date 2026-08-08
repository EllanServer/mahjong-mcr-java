package top.ellan.mahjong.rules.mcr;

public interface McrRulesEngine {
    WinEvaluation evaluate(WinInput input);
    WaitEvaluation waits(WaitInput input);
    boolean isWinningShape(TileCounts concealedBeforeWin, java.util.List<Meld> melds, Tile winningTile);
}
