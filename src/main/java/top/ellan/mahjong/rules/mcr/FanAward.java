package top.ellan.mahjong.rules.mcr;

public record FanAward(Fan fan, int count) {
    public FanAward {
        if (fan == null || count <= 0) throw new IllegalArgumentException("fan and positive count are required");
    }

    public int points() { return Math.multiplyExact(fan.points(), count); }
}
