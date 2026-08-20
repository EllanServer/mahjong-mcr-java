package top.ellan.mahjong.rules.mcr;

import java.util.Locale;

/** A physical MCR tile kind. Standard tile indexes are stable in the range 0..33. */
public enum Tile {
    M1(0, Suit.CHARACTERS, 1, "W1"), M2(1, Suit.CHARACTERS, 2, "W2"),
    M3(2, Suit.CHARACTERS, 3, "W3"), M4(3, Suit.CHARACTERS, 4, "W4"),
    M5(4, Suit.CHARACTERS, 5, "W5"), M6(5, Suit.CHARACTERS, 6, "W6"),
    M7(6, Suit.CHARACTERS, 7, "W7"), M8(7, Suit.CHARACTERS, 8, "W8"),
    M9(8, Suit.CHARACTERS, 9, "W9"),
    S1(9, Suit.BAMBOO, 1, "T1"), S2(10, Suit.BAMBOO, 2, "T2"),
    S3(11, Suit.BAMBOO, 3, "T3"), S4(12, Suit.BAMBOO, 4, "T4"),
    S5(13, Suit.BAMBOO, 5, "T5"), S6(14, Suit.BAMBOO, 6, "T6"),
    S7(15, Suit.BAMBOO, 7, "T7"), S8(16, Suit.BAMBOO, 8, "T8"),
    S9(17, Suit.BAMBOO, 9, "T9"),
    P1(18, Suit.DOTS, 1, "B1"), P2(19, Suit.DOTS, 2, "B2"),
    P3(20, Suit.DOTS, 3, "B3"), P4(21, Suit.DOTS, 4, "B4"),
    P5(22, Suit.DOTS, 5, "B5"), P6(23, Suit.DOTS, 6, "B6"),
    P7(24, Suit.DOTS, 7, "B7"), P8(25, Suit.DOTS, 8, "B8"),
    P9(26, Suit.DOTS, 9, "B9"),
    EAST(27, Suit.WIND, 0, "F1"), SOUTH(28, Suit.WIND, 0, "F2"),
    WEST(29, Suit.WIND, 0, "F3"), NORTH(30, Suit.WIND, 0, "F4"),
    RED_DRAGON(31, Suit.DRAGON, 0, "J3"), GREEN_DRAGON(32, Suit.DRAGON, 0, "J2"),
    WHITE_DRAGON(33, Suit.DRAGON, 0, "J1"),
    PLUM(34, Suit.FLOWER, 0, "a"), ORCHID(35, Suit.FLOWER, 0, "b"),
    BAMBOO_FLOWER(36, Suit.FLOWER, 0, "c"), CHRYSANTHEMUM(37, Suit.FLOWER, 0, "d"),
    SPRING(38, Suit.FLOWER, 0, "e"), SUMMER(39, Suit.FLOWER, 0, "f"),
    AUTUMN(40, Suit.FLOWER, 0, "g"), WINTER(41, Suit.FLOWER, 0, "h");

    public static final int STANDARD_KIND_COUNT = 34;
    private static final Tile[] BY_INDEX = values();

    private final int index;
    private final Suit suit;
    private final int rank;
    private final String code;

    Tile(int index, Suit suit, int rank, String code) {
        this.index = index;
        this.suit = suit;
        this.rank = rank;
        this.code = code;
    }

    public int index() { return index; }
    public Suit suit() { return suit; }
    public int rank() { return rank; }

    /** Canonical Green Book text code (W/T/B suits, F winds, J dragons, a-h flowers). */
    public String code() { return code; }
    public boolean isStandard() { return index < STANDARD_KIND_COUNT; }
    public boolean isNumbered() { return suit.isNumbered(); }
    public boolean isHonor() { return suit == Suit.WIND || suit == Suit.DRAGON; }
    public boolean isWind() { return suit == Suit.WIND; }
    public boolean isDragon() { return suit == Suit.DRAGON; }
    public boolean isFlower() { return suit == Suit.FLOWER; }
    public boolean isTerminal() { return isNumbered() && (rank == 1 || rank == 9); }
    public boolean isTerminalOrHonor() { return isTerminal() || isHonor(); }

    public static Tile standard(int index) {
        if (index < 0 || index >= STANDARD_KIND_COUNT) {
            throw new IllegalArgumentException("standard tile index must be in 0..33: " + index);
        }
        return BY_INDEX[index];
    }

    /**
     * Resolves a tile from its Green Book text code or enum name.
     *
     * <p>Used by fixtures, gold corpora and diagnostics only; evaluation never parses strings.</p>
     */
    public static Tile parse(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("tile code cannot be blank");
        }
        String value = code.trim();
        for (Tile tile : values()) {
            if (tile.code.equalsIgnoreCase(value) || tile.name().equalsIgnoreCase(value)) {
                return tile;
            }
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.length() == 2 && Character.isDigit(normalized.charAt(0))) {
            int rank = normalized.charAt(0) - '0';
            int base = switch (normalized.charAt(1)) {
                case 'm' -> 0;
                case 's' -> 9;
                case 'p' -> 18;
                default -> -100;
            };
            if (base >= 0 && rank >= 1 && rank <= 9) {
                return standard(base + rank - 1);
            }
        }
        throw new IllegalArgumentException("unsupported tile code: " + code);
    }
}
