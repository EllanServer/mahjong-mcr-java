package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrPhysicalDealTest {
    @Test
    void physicalSetHasFourStandardCopiesAndEightUniqueFlowers() {
        List<McrTileInstance> tiles = McrTileInstance.fullSet();
        assertEquals(144, tiles.size());
        assertEquals(144, new HashSet<>(tiles).size());

        for (Tile kind : Tile.values()) {
            long expected = kind.isStandard() ? 4 : 1;
            assertEquals(expected, tiles.stream().filter(tile -> tile.kind() == kind).count(), kind.name());
        }
        for (McrTileInstance tile : tiles) {
            assertEquals(tile, McrTileInstance.fromId(tile.id()));
            assertEquals(tile, McrTileInstance.of(tile.kind(), tile.copyIndex()));
        }
        assertThrows(IllegalArgumentException.class, () -> McrTileInstance.fromId(144));
        assertThrows(IllegalArgumentException.class, () -> McrTileInstance.of(Tile.PLUM, 1));
    }

    @Test
    void seededWallIsRepeatableAndDrawsFromOppositeEndsWithoutMutation() {
        McrWall first = McrWall.shuffled(0x4d435232303230L);
        McrWall again = McrWall.shuffled(0x4d435232303230L);
        McrWall different = McrWall.shuffled(0x4d435232303231L);

        assertEquals(first.remainingTiles(), again.remainingTiles());
        assertNotEquals(first.remainingTiles(), different.remainingTiles());
        assertEquals(List.of(119, 107, 59, 5, 89, 40, 45, 102),
                ids(first.remainingTiles().subList(0, 8)),
                "the shuffle is a replay compatibility contract, not an implementation detail");
        assertEquals(List.of(3, 110, 127, 24, 56, 105, 13, 33),
                ids(first.remainingTiles().subList(136, 144)));

        McrWall.Draw front = first.drawFront();
        McrWall.Draw back = front.remainingWall().drawBack();
        assertEquals(first.peekAt(0), front.tile());
        assertEquals(first.peekBack(), back.tile());
        assertNotEquals(front.tile(), back.tile());
        assertEquals(144, first.remaining());
        assertEquals(142, back.remainingWall().remaining());
    }

    @Test
    void dealUsesThreeFourTileBlocksThenTheOneAndThreeDistribution() {
        McrInitialDeal deal = McrInitialDealer.deal(McrWall.fromOrder(McrTileInstance.fullSet()));

        assertEquals(List.of(0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48, 52),
                ids(deal.concealed(Wind.EAST)));
        assertEquals(List.of(4, 5, 6, 7, 20, 21, 22, 23, 36, 37, 38, 39, 49),
                ids(deal.concealed(Wind.SOUTH)));
        assertEquals(List.of(8, 9, 10, 11, 24, 25, 26, 27, 40, 41, 42, 43, 50),
                ids(deal.concealed(Wind.WEST)));
        assertEquals(List.of(12, 13, 14, 15, 28, 29, 30, 31, 44, 45, 46, 47, 51),
                ids(deal.concealed(Wind.NORTH)));
        assertEquals(91, deal.wall().remaining());
        assertTrue(deal.flowers().values().stream().allMatch(List::isEmpty));
        assertEquals(McrTileInstance.fromId(52), deal.dealerLastTile());
        assertEquals(McrDrawSource.DEAL, deal.dealerLastTileSource());
    }

    @Test
    void initialFlowersAndFlowerReplacementsAreRecursivelyDrawnFromTheBack() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        McrTileInstance firstStandard = order.get(0);
        McrTileInstance plum = order.get(136);
        order.set(0, plum);
        order.set(136, firstStandard);

        McrInitialDeal deal = McrInitialDealer.deal(McrWall.fromOrder(order));

        assertEquals(8, deal.flowers(Wind.EAST).size());
        assertTrue(deal.flowers(Wind.EAST).stream().allMatch(tile -> tile.kind().isFlower()));
        assertTrue(deal.concealed(Wind.EAST).stream().noneMatch(tile -> tile.kind().isFlower()));
        assertTrue(deal.concealed(Wind.EAST).contains(firstStandard));
        assertEquals(83, deal.wall().remaining());
        assertEquals(firstStandard, deal.dealerLastTile());
        assertEquals(McrDrawSource.FLOWER_REPLACEMENT, deal.dealerLastTileSource());

        HashSet<McrTileInstance> all = new HashSet<>();
        for (Wind wind : Wind.values()) {
            all.addAll(deal.concealed(wind));
            all.addAll(deal.flowers(wind));
        }
        all.addAll(deal.wall().remainingTiles());
        assertEquals(144, all.size());
    }

    @Test
    void explicitWallRejectsMissingOrDuplicatePhysicalTiles() {
        ArrayList<McrTileInstance> missing = new ArrayList<>(McrTileInstance.fullSet());
        missing.removeLast();
        assertThrows(IllegalArgumentException.class, () -> McrWall.fromOrder(missing));

        ArrayList<McrTileInstance> duplicate = new ArrayList<>(McrTileInstance.fullSet());
        duplicate.set(143, duplicate.get(0));
        assertThrows(IllegalArgumentException.class, () -> McrWall.fromOrder(duplicate));
    }

    private static List<Integer> ids(List<McrTileInstance> tiles) {
        return tiles.stream().map(McrTileInstance::id).toList();
    }
}
