package top.ellan.mahjong.rules.mcr;

/** Documented scoring combinations which are not separate names in the official 81-fan list. */
public enum InternalCombination {
    MIXED_CONCEALED_AND_MELDED_KONG(6);

    private final int points;

    InternalCombination(int points) { this.points = points; }

    public int points() { return points; }
}
