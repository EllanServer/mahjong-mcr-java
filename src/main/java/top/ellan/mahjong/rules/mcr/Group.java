package top.ellan.mahjong.rules.mcr;

import java.util.Arrays;

final class Group {
    enum Kind { CHOW, PUNG, KONG, PAIR }

    final Kind kind;
    final int tile;
    final boolean declared;
    final boolean concealed;
    final boolean containsWinning;

    Group(Kind kind, int tile, boolean declared, boolean concealed, boolean containsWinning) {
        this.kind = kind;
        this.tile = tile;
        this.declared = declared;
        this.concealed = concealed;
        this.containsWinning = containsWinning;
    }

    static Group fromMeld(Meld meld) {
        Kind kind = switch (meld.type()) {
            case CHOW -> Kind.CHOW;
            case PUNG -> Kind.PUNG;
            case KONG -> Kind.KONG;
        };
        return new Group(kind, meld.tile().index(), true, meld.concealed(), false);
    }

    Group markWinning(WinMethod method) {
        boolean remainsConcealed = concealed;
        if (kind == Kind.PUNG && method == WinMethod.DISCARD) remainsConcealed = false;
        return new Group(kind, tile, declared, remainsConcealed, true);
    }

    boolean isSequence() { return kind == Kind.CHOW; }
    boolean isTripletOrKong() { return kind == Kind.PUNG || kind == Kind.KONG; }
    boolean isPair() { return kind == Kind.PAIR; }
    boolean isKong() { return kind == Kind.KONG; }

    int[] tiles() {
        return switch (kind) {
            case CHOW -> new int[] {tile - 1, tile, tile + 1};
            case PUNG -> new int[] {tile, tile, tile};
            case KONG -> new int[] {tile, tile, tile, tile};
            case PAIR -> new int[] {tile, tile};
        };
    }

    boolean contains(int tileIndex) {
        if (kind == Kind.CHOW) return tileIndex >= tile - 1 && tileIndex <= tile + 1;
        return tile == tileIndex;
    }

    String key() {
        return kind + ":" + tile + ":" + declared + ":" + concealed + ":" + containsWinning;
    }

    @Override
    public String toString() { return key() + Arrays.toString(tiles()); }
}
