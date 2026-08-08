package top.ellan.mahjong.rules.mcr.benchmark;

import top.ellan.mahjong.rules.mcr.McrRulesEngine;
import top.ellan.mahjong.rules.mcr.StandardMcrRulesEngine;
import top.ellan.mahjong.rules.mcr.Tile;
import top.ellan.mahjong.rules.mcr.TileCounts;
import top.ellan.mahjong.rules.mcr.WaitInput;
import top.ellan.mahjong.rules.mcr.WinContext;
import top.ellan.mahjong.rules.mcr.WinInput;
import top.ellan.mahjong.rules.mcr.WinMethod;
import top.ellan.mahjong.rules.mcr.Wind;

import java.util.List;
import java.util.Set;

/** Dependency-free smoke benchmark. Use JMH for publishable measurements. */
public final class McrMicroBenchmark {
    private static volatile long blackhole;

    private McrMicroBenchmark() {}

    public static void main(String[] args) {
        int warmup = integerArgument(args, 0, 100_000);
        int iterations = integerArgument(args, 1, 1_000_000);
        McrRulesEngine engine = new StandardMcrRulesEngine();
        WinInput hand = new WinInput(
                TileCounts.parse("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8",
                        "B1", "B1", "B1", "T2", "T2"),
                List.of(), Tile.parse("W9"),
                new WinContext(Wind.EAST, Wind.EAST, WinMethod.DISCARD, Set.of(), List.of()));

        for (int i = 0; i < warmup; i++) blackhole += engine.evaluate(hand).totalFan();
        long start = System.nanoTime();
        long checksum = 0;
        for (int i = 0; i < iterations; i++) checksum += engine.evaluate(hand).totalFan();
        long elapsed = System.nanoTime() - start;
        blackhole = checksum;
        double nanosPerOperation = (double) elapsed / iterations;
        double operationsPerSecond = 1_000_000_000.0 / nanosPerOperation;
        System.out.printf("evaluate: %,d ops, %.1f ns/op, %,.0f ops/s, checksum=%d%n",
                iterations, nanosPerOperation, operationsPerSecond, checksum);

        WaitInput waitInput = new WaitInput(hand.concealed(), List.of(), Wind.EAST, Wind.EAST, Set.of(), List.of());
        int waitIterations = Math.max(1, iterations / 100);
        start = System.nanoTime();
        checksum = 0;
        for (int i = 0; i < waitIterations; i++) checksum += engine.waits(waitInput).waits().size();
        elapsed = System.nanoTime() - start;
        blackhole = checksum;
        nanosPerOperation = (double) elapsed / waitIterations;
        operationsPerSecond = 1_000_000_000.0 / nanosPerOperation;
        System.out.printf("waits:    %,d ops, %.1f ns/op, %,.0f ops/s, checksum=%d%n",
                waitIterations, nanosPerOperation, operationsPerSecond, checksum);
    }

    private static int integerArgument(String[] args, int index, int fallback) {
        if (args.length <= index) return fallback;
        int value = Integer.parseInt(args[index]);
        if (value <= 0) throw new IllegalArgumentException("iteration counts must be positive");
        return value;
    }
}
