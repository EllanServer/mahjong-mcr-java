package top.ellan.mahjong.rules.mcr;

import java.util.Optional;

/** One public or authorized-private scene node with no physical tile identifier. */
public record McrViewTile(
        long projectionId,
        Optional<Tile> face,
        Optional<Wind> owner,
        McrViewZone zone,
        int index,
        boolean faceUp) {

    public McrViewTile {
        if (projectionId < 0 || projectionId >= McrTileInstance.PHYSICAL_TILE_COUNT
                || face == null || owner == null || zone == null || index < 0) {
            throw new IllegalArgumentException("invalid MCR projected tile");
        }
        if (faceUp != face.isPresent()) {
            throw new IllegalArgumentException("face presence must match visibility");
        }
        if (zone == McrViewZone.WALL) {
            if (owner.isPresent() || faceUp) {
                throw new IllegalArgumentException("wall node must be ownerless and hidden");
            }
        } else if (owner.isEmpty()) {
            throw new IllegalArgumentException("placed scene tile requires an owner");
        }
        if ((zone == McrViewZone.FLOWER || zone == McrViewZone.RIVER
                || zone == McrViewZone.WIN_CLAIM) && !faceUp) {
            throw new IllegalArgumentException("public flower, river and win claim must be visible");
        }
    }
}
