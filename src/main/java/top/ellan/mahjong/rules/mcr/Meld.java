package top.ellan.mahjong.rules.mcr;

/** A declared set. For a chow, {@code tile} is its middle tile. */
public record Meld(MeldType type, Tile tile, boolean concealed) {
    public Meld {
        if (type == null || tile == null || !tile.isStandard()) {
            throw new IllegalArgumentException("meld type and standard tile are required");
        }
        if (type == MeldType.CHOW && (!tile.isNumbered() || tile.rank() < 2 || tile.rank() > 8)) {
            throw new IllegalArgumentException("chow tile must be its numbered middle tile (2..8)");
        }
        if (concealed && type != MeldType.KONG) {
            throw new IllegalArgumentException("only a declared kong can be concealed");
        }
    }

    public static Meld chow(Tile middle) { return new Meld(MeldType.CHOW, middle, false); }
    public static Meld pung(Tile tile) { return new Meld(MeldType.PUNG, tile, false); }
    public static Meld openKong(Tile tile) { return new Meld(MeldType.KONG, tile, false); }
    public static Meld concealedKong(Tile tile) { return new Meld(MeldType.KONG, tile, true); }
}
