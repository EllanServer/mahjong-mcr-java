package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure public/private projection with per-hand opaque IDs and no hidden faces. */
public final class McrViewProjector {
    public McrPublicView publicView(McrRoundState state, long handSalt) {
        if (state == null) throw new IllegalArgumentException("state is required");
        McrProjectionIds ids = McrProjectionIds.forHand(handSalt);
        ArrayList<McrViewTile> tiles = new ArrayList<>(McrTileInstance.PHYSICAL_TILE_COUNT);
        List<McrTileInstance> wall = state.wall().remainingTiles();
        for (int index = 0; index < wall.size(); index++) {
            tiles.add(project(ids, wall.get(index), null, McrViewZone.WALL, index, false));
        }

        Wind winner = state.outcome().filter(McrRoundOutcome.Win.class::isInstance)
                .map(McrRoundOutcome.Win.class::cast)
                .map(McrRoundOutcome.Win::winner)
                .orElse(null);
        boolean ended = state.phase() == McrRoundPhase.ENDED;
        for (Wind seat : Wind.values()) {
            List<McrTileInstance> hand = state.hand(seat);
            for (int index = 0; index < hand.size(); index++) {
                tiles.add(project(
                        ids,
                        hand.get(index),
                        seat,
                        McrViewZone.CONCEALED_HAND,
                        index,
                        ended && seat == winner));
            }
            List<McrTileInstance> flowers = state.flowers(seat);
            for (int index = 0; index < flowers.size(); index++) {
                tiles.add(project(ids, flowers.get(index), seat, McrViewZone.FLOWER, index, true));
            }
            List<McrPhysicalDiscard> river = state.river(seat);
            for (int index = 0; index < river.size(); index++) {
                McrPhysicalDiscard discard = river.get(index);
                if (discard.status() != McrDiscardStatus.MELD_CLAIMED) {
                    tiles.add(project(
                            ids, discard.tile(), seat, McrViewZone.RIVER, index, true));
                }
            }
            List<McrPhysicalMeld> melds = state.melds(seat);
            for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
                McrPhysicalMeld meld = melds.get(meldIndex);
                boolean visible = meld.origin() != McrMeldOrigin.CONCEALED_KONG || ended;
                for (int tileIndex = 0; tileIndex < meld.tiles().size(); tileIndex++) {
                    tiles.add(project(
                            ids,
                            meld.tiles().get(tileIndex),
                            seat,
                            McrViewZone.MELD,
                            meldIndex * 4 + tileIndex,
                            visible));
                }
            }
        }
        McrRobbedKongClaim robbed = state.robbedKongClaim().orElse(null);
        if (robbed != null) {
            tiles.add(project(
                    ids, robbed.tile(), robbed.winner(), McrViewZone.WIN_CLAIM, 0, true));
        }
        return new McrPublicView(
                state.revision(),
                state.roundWind(),
                state.currentSeat(),
                state.phase(),
                state.wall().remaining(),
                Optional.ofNullable(winner),
                tiles);
    }

    public McrPrivateView privateView(McrRoundState state, Wind viewer, long handSalt) {
        if (state == null || viewer == null) {
            throw new IllegalArgumentException("state and viewer are required");
        }
        McrProjectionIds ids = McrProjectionIds.forHand(handSalt);
        List<McrTileInstance> hand = state.hand(viewer);
        ArrayList<McrViewTile> tiles = new ArrayList<>(hand.size());
        for (int index = 0; index < hand.size(); index++) {
            tiles.add(project(
                    ids,
                    hand.get(index),
                    viewer,
                    McrViewZone.CONCEALED_HAND,
                    index,
                    true));
        }
        return new McrPrivateView(state.revision(), viewer, tiles);
    }

    private static McrViewTile project(
            McrProjectionIds ids,
            McrTileInstance tile,
            Wind owner,
            McrViewZone zone,
            int index,
            boolean faceUp) {
        return new McrViewTile(
                ids.project(tile),
                faceUp ? Optional.of(tile.kind()) : Optional.empty(),
                Optional.ofNullable(owner),
                zone,
                index,
                faceUp);
    }
}
