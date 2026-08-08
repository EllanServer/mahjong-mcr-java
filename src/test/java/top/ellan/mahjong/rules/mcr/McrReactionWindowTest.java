package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrReactionWindowTest {
    private static final McrTileInstance DISCARD = McrTileInstance.of(Tile.M5, 0);

    @Test
    void huHasPriorityAndNearestPlayerAfterDiscarderIsTheOnlyWinner() {
        McrReaction southHu = McrReaction.hu(Wind.SOUTH, DISCARD);
        McrReaction westHu = McrReaction.hu(Wind.WEST, DISCARD);
        McrReaction northHu = McrReaction.hu(Wind.NORTH, DISCARD);
        McrReaction northPung = McrReaction.pung(Wind.NORTH, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M5, 2));
        McrReactionWindow window = McrReactionWindow.open(
                Wind.EAST, DISCARD, List.of(southHu, westHu, northHu, northPung));

        window = window.respond(northPung).respond(westHu).respond(southHu);

        McrReactionResolution.Win win = assertInstanceOf(
                McrReactionResolution.Win.class, window.resolution());
        assertEquals(Wind.SOUTH, win.reaction().claimant());
    }

    @Test
    void aFartherHuBeatsPungAndChow() {
        McrReaction southChow = McrReaction.chow(Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.M4, 0), McrTileInstance.of(Tile.M6, 0));
        McrReaction westPung = McrReaction.pung(Wind.WEST, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M5, 2));
        McrReaction northHu = McrReaction.hu(Wind.NORTH, DISCARD);
        McrReactionWindow window = McrReactionWindow.open(
                Wind.EAST, DISCARD, List.of(southChow, westPung, northHu));

        window = window.respond(southChow).respond(westPung).respond(northHu);

        McrReactionResolution.Win result = assertInstanceOf(
                McrReactionResolution.Win.class, window.resolution());
        assertEquals(northHu, result.reaction());
    }

    @Test
    void pungAndKongHavePriorityOverChow() {
        McrReaction southChow = McrReaction.chow(Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.M4, 0), McrTileInstance.of(Tile.M6, 0));
        McrReaction northKong = McrReaction.kong(Wind.NORTH, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M5, 2),
                McrTileInstance.of(Tile.M5, 3));
        McrReactionWindow window = McrReactionWindow.open(
                Wind.EAST, DISCARD, List.of(southChow, northKong));

        window = window.respond(southChow).pass(Wind.WEST).respond(northKong);

        McrReactionResolution.MeldClaim result = assertInstanceOf(
                McrReactionResolution.MeldClaim.class, window.resolution());
        assertEquals(northKong, result.reaction());
    }

    @Test
    void chowIsOnlyAvailableToTheNextSeat() {
        McrReaction illegalWestChow = McrReaction.chow(Wind.WEST, DISCARD,
                McrTileInstance.of(Tile.M4, 0), McrTileInstance.of(Tile.M6, 0));
        assertThrows(IllegalArgumentException.class,
                () -> McrReactionWindow.open(Wind.EAST, DISCARD, List.of(illegalWestChow)));

        McrReaction legalNorthChow = McrReaction.chow(Wind.NORTH, DISCARD,
                McrTileInstance.of(Tile.M4, 0), McrTileInstance.of(Tile.M6, 0));
        assertTrue(McrReactionWindow.open(Wind.WEST, DISCARD, List.of(legalNorthChow))
                .legalOptions(Wind.NORTH).contains(legalNorthChow));
    }

    @Test
    void timeoutPassesContinueWithTheNextDrawer() {
        McrReactionWindow closed = McrReactionWindow.open(Wind.SOUTH, DISCARD, List.of())
                .pass(Wind.WEST)
                .closeWithPasses();

        assertTrue(closed.isClosed());
        assertEquals(3, closed.decisions().size());
        McrReactionResolution.NoClaim result = assertInstanceOf(
                McrReactionResolution.NoClaim.class, closed.resolution());
        assertEquals(Wind.WEST, result.nextDrawer());
    }

    @Test
    void onlyIssuedExactOptionsCanBeSelectedAndSeatsRespondOnce() {
        McrReaction issued = McrReaction.pung(Wind.WEST, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M5, 2));
        McrReaction forgedPhysicalChoice = McrReaction.pung(Wind.WEST, DISCARD,
                McrTileInstance.of(Tile.M5, 2), McrTileInstance.of(Tile.M5, 3));
        ArrayList<McrReaction> mutableSource = new ArrayList<>(List.of(issued));
        McrReactionWindow window = McrReactionWindow.open(Wind.EAST, DISCARD, mutableSource);
        mutableSource.clear();

        assertEquals(List.of(issued), window.legalOptions(Wind.WEST));
        assertThrows(IllegalArgumentException.class, () -> window.respond(forgedPhysicalChoice));

        McrReactionWindow responded = window.respond(issued);
        assertThrows(IllegalStateException.class, () -> responded.pass(Wind.WEST));
        assertThrows(IllegalStateException.class, responded::resolution);
    }

    @Test
    void malformedPhysicalMeldClaimsAreRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> McrReaction.chow(
                Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.M3, 0), McrTileInstance.of(Tile.M7, 0)));
        assertThrows(IllegalArgumentException.class, () -> McrReaction.chow(
                Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.S4, 0), McrTileInstance.of(Tile.M6, 0)));
        assertThrows(IllegalArgumentException.class, () -> McrReaction.pung(
                Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M6, 0)));
        assertThrows(IllegalArgumentException.class, () -> McrReaction.pung(
                Wind.SOUTH, DISCARD,
                McrTileInstance.of(Tile.M5, 1), McrTileInstance.of(Tile.M5, 1)));
    }
}
