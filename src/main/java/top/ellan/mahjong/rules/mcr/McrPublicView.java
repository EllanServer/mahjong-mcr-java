package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Optional;

/** Information safe to publish identically to all table viewers. */
public record McrPublicView(
        long revision,
        Wind roundWind,
        Wind currentSeat,
        McrRoundPhase phase,
        int wallRemaining,
        Optional<Wind> winner,
        List<McrViewTile> tiles) {

    public McrPublicView {
        if (revision < 0 || roundWind == null || currentSeat == null || phase == null
                || wallRemaining < 0 || winner == null || tiles == null) {
            throw new IllegalArgumentException("invalid MCR public view metadata");
        }
        tiles = List.copyOf(tiles);
        if (tiles.size() != McrTileInstance.PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("public MCR view must place all 144 identities");
        }
        boolean[] projected = new boolean[McrTileInstance.PHYSICAL_TILE_COUNT];
        for (McrViewTile tile : tiles) {
            int id = Math.toIntExact(tile.projectionId());
            if (projected[id]) throw new IllegalArgumentException("duplicate scene projection id");
            projected[id] = true;
            if (tile.zone() == McrViewZone.CONCEALED_HAND && tile.faceUp()
                    && winner.orElse(null) != tile.owner().orElse(null)) {
                throw new IllegalArgumentException("public view exposed a non-winner concealed tile");
            }
        }
        if (winner.isPresent() && phase != McrRoundPhase.ENDED) {
            throw new IllegalArgumentException("winner requires an ended round");
        }
    }
}
