package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Random;
import top.ellan.mahjong.spi.RuleDiceRoll;
import top.ellan.mahjong.spi.RuleOpeningPresentation;
import top.ellan.mahjong.spi.RuleWallDirection;
import top.ellan.mahjong.spi.RuleWallPresentation;
import top.ellan.mahjong.spi.SeatId;

/** Green Book two-roll opening, cached once for each immutable hand seed. */
record McrOpeningLayout(
        RuleWallPresentation wall,
        RuleOpeningPresentation opening) {
    private static final long DICE_SALT = 0x4d43_522d_4449_4345L;
    private static final int STACKS_PER_SIDE = 18;
    private static final int TOTAL_STACKS = STACKS_PER_SIDE * 4;

    static McrOpeningLayout forMatch(McrMatchState match) {
        Random random = new Random(match.currentHandSeed() ^ DICE_SALT);
        RuleDiceRoll first = roll(random);
        RuleDiceRoll second = roll(random);
        int dealer = match.playerAt(Wind.EAST).ordinal();
        int openDoor = Math.floorMod(dealer + first.total() - 1, 4);
        int breakOffset = first.total() + second.total();
        int drawStart = Math.floorMod(
                openDoor * STACKS_PER_SIDE + breakOffset, TOTAL_STACKS);
        return new McrOpeningLayout(
                new RuleWallPresentation(
                        List.of(18, 18, 18, 18),
                        drawStart,
                        RuleWallDirection.CLOCKWISE),
                new RuleOpeningPresentation(
                        match.currentHandNumber(),
                        List.of(first, second),
                        new SeatId(openDoor),
                        breakOffset));
    }

    private static RuleDiceRoll roll(Random random) {
        return new RuleDiceRoll(List.of(random.nextInt(6) + 1, random.nextInt(6) + 1));
    }
}
