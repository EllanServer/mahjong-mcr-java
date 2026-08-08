package top.ellan.mahjong.rules.mcr;

import java.util.List;

/** Additional face information authorized for one seated viewer. */
public record McrPrivateView(long revision, Wind viewer, List<McrViewTile> concealedTiles) {
    public McrPrivateView {
        if (revision < 0 || viewer == null || concealedTiles == null) {
            throw new IllegalArgumentException("invalid MCR private view");
        }
        concealedTiles = List.copyOf(concealedTiles);
        boolean[] projected = new boolean[McrTileInstance.PHYSICAL_TILE_COUNT];
        for (McrViewTile tile : concealedTiles) {
            if (tile.zone() != McrViewZone.CONCEALED_HAND
                    || tile.owner().orElse(null) != viewer || !tile.faceUp()) {
                throw new IllegalArgumentException("private view contains an unauthorized node");
            }
            int id = Math.toIntExact(tile.projectionId());
            if (projected[id]) throw new IllegalArgumentException("duplicate private projection id");
            projected[id] = true;
        }
    }
}
