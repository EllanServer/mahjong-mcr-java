package top.ellan.mahjong.rules.mcr;

public enum Wind {
    EAST(Tile.EAST), SOUTH(Tile.SOUTH), WEST(Tile.WEST), NORTH(Tile.NORTH);

    private final Tile tile;

    Wind(Tile tile) { this.tile = tile; }

    public Tile tile() { return tile; }
}
