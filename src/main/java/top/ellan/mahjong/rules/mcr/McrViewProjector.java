package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure public/private projection with per-hand opaque IDs and no hidden faces. */
public final class McrViewProjector {
    private static final Wind[] SEATS = Wind.values();

    public McrPublicView publicView(McrRoundState state, long handSalt) {
        return publicView(state, McrProjectionIds.forHand(handSalt));
    }

    McrPublicView publicView(McrRoundState state, McrProjectionIds ids) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (ids == null) throw new IllegalArgumentException("projection ids are required");
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
        for (Wind seat : SEATS) {
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
                            ids,
                            discard.tile(),
                            seat,
                            McrViewZone.RIVER,
                            index,
                            true,
                            false,
                            discard.status() == McrDiscardStatus.PENDING));
                }
            }
            List<McrPhysicalMeld> melds = state.melds(seat);
            for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
                McrPhysicalMeld meld = melds.get(meldIndex);
                boolean visible = meld.origin() != McrMeldOrigin.CONCEALED_KONG || ended;
                int ordinaryOrdinal = 0;
                for (int tileIndex = 0; tileIndex < meld.tiles().size(); tileIndex++) {
                    McrTileInstance tile = meld.tiles().get(tileIndex);
                    boolean claimed = tile.equals(meld.claimedDiscard());
                    boolean added = tile.equals(meld.addedTile());
                    top.ellan.mahjong.spi.RuleTilePresentation presentation = meldPresentation(
                            meld, seat, meldIndex, tile, tileIndex, ordinaryOrdinal);
                    if (!claimed && !added) ordinaryOrdinal++;
                    tiles.add(project(
                            ids,
                            tile,
                            seat,
                            McrViewZone.MELD,
                            meldIndex * 4 + tileIndex,
                            visible,
                            presentation));
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
        return privateView(state, viewer, McrProjectionIds.forHand(handSalt));
    }

    McrPrivateView privateView(
            McrRoundState state, Wind viewer, McrProjectionIds ids) {
        if (state == null || viewer == null) {
            throw new IllegalArgumentException("state and viewer are required");
        }
        if (ids == null) throw new IllegalArgumentException("projection ids are required");
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

    static top.ellan.mahjong.spi.RuleTilePresentation meldPresentation(
            McrPhysicalMeld meld,
            Wind owner,
            int meldIndex,
            McrTileInstance tile,
            int tileIndex,
            int ordinaryOrdinal) {
        if (meld.origin() == McrMeldOrigin.CONCEALED_KONG) {
            return top.ellan.mahjong.spi.RuleTilePresentation.natural(
                    meldIndex * 4 + tileIndex);
        }
        boolean added = tile.equals(meld.addedTile());
        top.ellan.mahjong.spi.RuleMeldTileRole role = added
                ? top.ellan.mahjong.spi.RuleMeldTileRole.ADDED
                : tile.equals(meld.claimedDiscard())
                        ? top.ellan.mahjong.spi.RuleMeldTileRole.CLAIMED
                        : top.ellan.mahjong.spi.RuleMeldTileRole.ORDINARY;
        return top.ellan.mahjong.spi.RuleMeldPresentation.tile(
                meldIndex,
                meld.origin() == McrMeldOrigin.ADDED_KONG ? 3 : meld.tiles().size(),
                SEATS.length,
                owner.ordinal(),
                meld.sourceSeat().ordinal(),
                role,
                role == top.ellan.mahjong.spi.RuleMeldTileRole.ORDINARY
                        ? ordinaryOrdinal
                        : -1);
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
                faceUp,
                top.ellan.mahjong.spi.RuleTilePresentation.natural(index));
    }

    private static McrViewTile project(
            McrProjectionIds ids,
            McrTileInstance tile,
            Wind owner,
            McrViewZone zone,
            int index,
            boolean faceUp,
            boolean sideways,
            boolean emphasized) {
        top.ellan.mahjong.spi.RuleTilePresentation presentation = sideways
                ? top.ellan.mahjong.spi.RuleTilePresentation.clockwise(index)
                : top.ellan.mahjong.spi.RuleTilePresentation.natural(index);
        if (emphasized) {
            presentation = presentation.withEmphasis();
        }
        return project(ids, tile, owner, zone, index, faceUp, presentation);
    }

    private static McrViewTile project(
            McrProjectionIds ids,
            McrTileInstance tile,
            Wind owner,
            McrViewZone zone,
            int index,
            boolean faceUp,
            top.ellan.mahjong.spi.RuleTilePresentation presentation) {
        return new McrViewTile(
                ids.project(tile),
                faceUp ? Optional.of(tile.kind()) : Optional.empty(),
                Optional.ofNullable(owner),
                zone,
                index,
                faceUp,
                presentation);
    }
}
