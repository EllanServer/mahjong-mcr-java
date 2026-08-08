package top.ellan.mahjong.rules.mcr;

public record InternalAward(InternalCombination combination, int count) {
    public InternalAward {
        if (combination == null || count <= 0) {
            throw new IllegalArgumentException("combination and positive count are required");
        }
    }

    public int points() { return Math.multiplyExact(combination.points(), count); }
}
