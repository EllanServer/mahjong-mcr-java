package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * A scoring meld paired with every physical tile needed by replay and scene
 * projection. Open melds retain both the claimed discard and its source seat.
 */
public record McrPhysicalMeld(
        McrMeldOrigin origin,
        List<McrTileInstance> tiles,
        McrTileInstance claimedDiscard,
        Wind sourceSeat) {

    public McrPhysicalMeld {
        if (origin == null || tiles == null) {
            throw new IllegalArgumentException("meld origin and tiles are required");
        }
        ArrayList<McrTileInstance> sorted = new ArrayList<>(tiles);
        sorted.sort(null);
        tiles = List.copyOf(sorted);
        if (new HashSet<>(tiles).size() != tiles.size()) {
            throw new IllegalArgumentException("physical meld tiles must be unique");
        }
        for (McrTileInstance tile : tiles) {
            if (tile == null || tile.kind().isFlower()) {
                throw new IllegalArgumentException("a meld may contain only standard tiles");
            }
        }

        boolean concealed = origin == McrMeldOrigin.CONCEALED_KONG;
        if (concealed) {
            if (claimedDiscard != null || sourceSeat != null) {
                throw new IllegalArgumentException("a concealed kong has no claimed discard or source seat");
            }
        } else if (claimedDiscard == null || sourceSeat == null || !tiles.contains(claimedDiscard)) {
            throw new IllegalArgumentException("an open meld must retain its claimed discard and source seat");
        }

        switch (origin) {
            case CHOW -> validateChow(tiles);
            case PUNG -> validateIdentical(tiles, 3);
            case DIRECT_KONG, CONCEALED_KONG, ADDED_KONG -> validateIdentical(tiles, 4);
        }
    }

    public static McrPhysicalMeld chow(
            List<McrTileInstance> tiles, McrTileInstance claimedDiscard, Wind sourceSeat) {
        return new McrPhysicalMeld(McrMeldOrigin.CHOW, tiles, claimedDiscard, sourceSeat);
    }

    public static McrPhysicalMeld pung(
            List<McrTileInstance> tiles, McrTileInstance claimedDiscard, Wind sourceSeat) {
        return new McrPhysicalMeld(McrMeldOrigin.PUNG, tiles, claimedDiscard, sourceSeat);
    }

    public static McrPhysicalMeld directKong(
            List<McrTileInstance> tiles, McrTileInstance claimedDiscard, Wind sourceSeat) {
        return new McrPhysicalMeld(McrMeldOrigin.DIRECT_KONG, tiles, claimedDiscard, sourceSeat);
    }

    public static McrPhysicalMeld concealedKong(List<McrTileInstance> tiles) {
        return new McrPhysicalMeld(McrMeldOrigin.CONCEALED_KONG, tiles, null, null);
    }

    public McrPhysicalMeld addFourth(McrTileInstance fourthTile) {
        if (origin != McrMeldOrigin.PUNG || fourthTile == null
                || fourthTile.kind() != tiles.getFirst().kind() || tiles.contains(fourthTile)) {
            throw new IllegalArgumentException("only the fourth physical copy can upgrade a pung");
        }
        ArrayList<McrTileInstance> upgraded = new ArrayList<>(tiles);
        upgraded.add(fourthTile);
        return new McrPhysicalMeld(
                McrMeldOrigin.ADDED_KONG, upgraded, claimedDiscard, sourceSeat);
    }

    public Meld scoringMeld() {
        return switch (origin) {
            case CHOW -> Meld.chow(tiles.get(1).kind());
            case PUNG -> Meld.pung(tiles.getFirst().kind());
            case DIRECT_KONG, ADDED_KONG -> Meld.openKong(tiles.getFirst().kind());
            case CONCEALED_KONG -> Meld.concealedKong(tiles.getFirst().kind());
        };
    }

    private static void validateChow(List<McrTileInstance> tiles) {
        if (tiles.size() != 3) throw new IllegalArgumentException("a chow contains three tiles");
        Tile first = tiles.get(0).kind();
        Tile middle = tiles.get(1).kind();
        Tile last = tiles.get(2).kind();
        if (!first.isNumbered() || first.suit() != middle.suit() || first.suit() != last.suit()
                || middle.rank() != first.rank() + 1 || last.rank() != middle.rank() + 1) {
            throw new IllegalArgumentException("physical chow must be one three-tile numbered sequence");
        }
    }

    private static void validateIdentical(List<McrTileInstance> tiles, int expected) {
        if (tiles.size() != expected) {
            throw new IllegalArgumentException("meld has the wrong physical tile count");
        }
        Tile kind = tiles.getFirst().kind();
        for (McrTileInstance tile : tiles) {
            if (tile.kind() != kind) throw new IllegalArgumentException("pung/kong tiles must be identical");
        }
    }
}
