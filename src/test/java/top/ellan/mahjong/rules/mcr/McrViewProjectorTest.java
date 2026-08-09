package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrViewProjectorTest {
    private static final long HAND_SALT = 0x4d43522d76696577L;
    private final McrViewProjector projector = new McrViewProjector();

    @Test
    void publicViewPlacesAllTilesWithoutPhysicalIdsOrHiddenFaces() {
        McrRoundState state = McrRoundState.start(20260808L, Wind.EAST);
        McrPublicView view = projector.publicView(state, HAND_SALT);

        assertEquals(144, view.tiles().size());
        assertEquals(144, new HashSet<>(
                view.tiles().stream().map(McrViewTile::projectionId).toList()).size());
        assertTrue(view.tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.WALL
                        || tile.zone() == McrViewZone.CONCEALED_HAND)
                .allMatch(tile -> !tile.faceUp() && tile.face().isEmpty()));
        assertTrue(view.tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.FLOWER)
                .allMatch(tile -> tile.faceUp() && tile.face().isPresent()));
        assertTrue(Arrays.stream(McrViewTile.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("physical")));
    }

    @Test
    void privateViewRevealsOnlyTheAuthorizedSeatUsingTheSameOpaqueNodes() {
        McrRoundState state = McrRoundState.start(99L, Wind.EAST);
        McrPublicView publicView = projector.publicView(state, HAND_SALT);
        McrPrivateView south = projector.privateView(state, Wind.SOUTH, HAND_SALT);

        assertEquals(state.hand(Wind.SOUTH).size(), south.concealedTiles().size());
        assertTrue(south.concealedTiles().stream().allMatch(McrViewTile::faceUp));
        assertTrue(south.concealedTiles().stream().allMatch(
                tile -> tile.owner().orElseThrow() == Wind.SOUTH && tile.face().isPresent()));
        HashSet<Long> publicSouthNodes = new HashSet<>(publicView.tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.CONCEALED_HAND)
                .filter(tile -> tile.owner().orElse(null) == Wind.SOUTH)
                .map(McrViewTile::projectionId)
                .toList());
        assertEquals(publicSouthNodes,
                new HashSet<>(south.concealedTiles().stream().map(McrViewTile::projectionId).toList()));
    }

    @Test
    void projectionPermutationIsPerHandUniqueAndHasNoRawIdFixedPoint() {
        McrProjectionIds first = McrProjectionIds.forHand(HAND_SALT);
        McrProjectionIds again = McrProjectionIds.forHand(HAND_SALT);
        McrProjectionIds other = McrProjectionIds.forHand(HAND_SALT + 1);
        boolean differs = false;
        HashSet<Long> projected = new HashSet<>();
        for (McrTileInstance tile : McrTileInstance.fullSet()) {
            assertEquals(first.project(tile), again.project(tile));
            assertNotEquals(tile.id(), first.project(tile));
            projected.add(first.project(tile));
            differs |= first.project(tile) != other.project(tile);
        }
        assertEquals(144, projected.size());
        assertTrue(differs);
    }

    @Test
    void terminalWinRevealsOnlyWinnerHandWhileLiveConcealedKongStaysFacedown() {
        McrRoundEngine engine = new McrRoundEngine();
        McrRoundState winning = eastPureStraightInitialWin();
        McrRoundState ended = engine.transition(
                winning, new McrRoundAction.SelfDrawWin(Wind.EAST)).state();
        McrPublicView terminal = projector.publicView(ended, HAND_SALT);

        assertTrue(terminal.tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.CONCEALED_HAND)
                .filter(tile -> tile.owner().orElse(null) == Wind.EAST)
                .allMatch(McrViewTile::faceUp));
        assertTrue(terminal.tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.CONCEALED_HAND)
                .filter(tile -> tile.owner().orElse(null) != Wind.EAST)
                .noneMatch(McrViewTile::faceUp));

        McrRoundState ordered = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(McrTileInstance.fullSet())), Wind.EAST);
        List<McrTileInstance> kong = List.of(
                McrTileInstance.fromId(0),
                McrTileInstance.fromId(1),
                McrTileInstance.fromId(2),
                McrTileInstance.fromId(3));
        McrRoundState afterKong = engine.transition(
                ordered, new McrRoundAction.ConcealedKong(Wind.EAST, kong)).state();
        List<McrViewTile> concealedKong = projector.publicView(afterKong, HAND_SALT).tiles().stream()
                .filter(tile -> tile.zone() == McrViewZone.MELD)
                .filter(tile -> tile.owner().orElse(null) == Wind.EAST)
                .toList();
        assertEquals(4, concealedKong.size());
        assertTrue(concealedKong.stream().noneMatch(McrViewTile::faceUp));
    }

    @Test
    void hiddenTileCannotBeConstructedWithAResidualFace() {
        assertFalse(new McrViewTile(
                1,
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                McrViewZone.WALL,
                0,
                false,
                top.ellan.mahjong.spi.RuleTilePresentation.natural(0)).faceUp());
    }

    @Test
    void openMeldUsesSharedSourceMarkerAndAddedKongStack() {
        McrTileInstance p1 = McrTileInstance.fromId(Tile.P1.ordinal() * 4);
        McrTileInstance p2 = McrTileInstance.fromId(Tile.P1.ordinal() * 4 + 1);
        McrTileInstance p3 = McrTileInstance.fromId(Tile.P1.ordinal() * 4 + 2);
        McrTileInstance p4 = McrTileInstance.fromId(Tile.P1.ordinal() * 4 + 3);
        McrPhysicalMeld pung = McrPhysicalMeld.pung(List.of(p1, p2, p3), p1, Wind.NORTH);
        assertEquals(
                0,
                McrViewProjector.meldPresentation(pung, Wind.EAST, 0, p1, 0, 0)
                        .layoutIndex());
        assertEquals(
                1,
                McrViewProjector.meldPresentation(pung, Wind.EAST, 0, p2, 1, 0)
                        .layoutIndex());
        McrPhysicalMeld added = pung.addFourth(p4);
        top.ellan.mahjong.spi.RuleTilePresentation stacked =
                McrViewProjector.meldPresentation(added, Wind.EAST, 0, p4, 3, 2);
        assertEquals(0, stacked.layoutIndex());
        assertEquals(1, stacked.stackLevel());
        assertEquals(top.ellan.mahjong.spi.RuleTileRotation.CLOCKWISE, stacked.rotation());
    }

    private static McrRoundState eastPureStraightInitialWin() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        int[] eastPositions = {0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48};
        int[] concealedBeforeWin = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int index = 0; index < eastPositions.length; index++) {
            place(order, eastPositions[index], concealedBeforeWin[index]);
        }
        place(order, 52, 32);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static void place(ArrayList<McrTileInstance> order, int position, int tileId) {
        int current = -1;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id() == tileId) {
                current = index;
                break;
            }
        }
        McrTileInstance displaced = order.get(position);
        order.set(position, order.get(current));
        order.set(current, displaced);
    }
}
