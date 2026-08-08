package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * An exact physical response to a discard. Claimed melds name the concealed
 * instances that will leave the claimant's hand; the discarded instance is
 * carried separately and can never be smuggled into that list.
 */
public record McrReaction(
        Wind claimant,
        McrReactionType type,
        McrTileInstance discard,
        List<McrTileInstance> concealedTiles) {

    public McrReaction {
        if (claimant == null || type == null || discard == null || discard.kind().isFlower()) {
            throw new IllegalArgumentException("claimant, reaction type and a standard discard are required");
        }
        ArrayList<McrTileInstance> sortedTiles = new ArrayList<>(
                concealedTiles == null ? List.of() : concealedTiles);
        sortedTiles.sort(null);
        concealedTiles = List.copyOf(sortedTiles);
        if (new HashSet<>(concealedTiles).size() != concealedTiles.size()) {
            throw new IllegalArgumentException("a reaction cannot consume one physical tile twice");
        }
        for (McrTileInstance tile : concealedTiles) {
            if (tile.kind().isFlower() || tile.equals(discard)) {
                throw new IllegalArgumentException("reaction concealed tiles must be distinct standard instances");
            }
        }

        switch (type) {
            case PASS, HU -> requireSize(concealedTiles, 0, type);
            case PUNG -> {
                requireSize(concealedTiles, 2, type);
                requireSameKind(concealedTiles, discard);
            }
            case KONG -> {
                requireSize(concealedTiles, 3, type);
                requireSameKind(concealedTiles, discard);
            }
            case CHOW -> validateChow(concealedTiles, discard);
        }
    }

    public static McrReaction pass(Wind claimant, McrTileInstance discard) {
        return new McrReaction(claimant, McrReactionType.PASS, discard, List.of());
    }

    public static McrReaction hu(Wind claimant, McrTileInstance discard) {
        return new McrReaction(claimant, McrReactionType.HU, discard, List.of());
    }

    public static McrReaction chow(
            Wind claimant, McrTileInstance discard, McrTileInstance first, McrTileInstance second) {
        return new McrReaction(claimant, McrReactionType.CHOW, discard, List.of(first, second));
    }

    public static McrReaction pung(
            Wind claimant, McrTileInstance discard, McrTileInstance first, McrTileInstance second) {
        return new McrReaction(claimant, McrReactionType.PUNG, discard, List.of(first, second));
    }

    public static McrReaction kong(
            Wind claimant,
            McrTileInstance discard,
            McrTileInstance first,
            McrTileInstance second,
            McrTileInstance third) {
        return new McrReaction(
                claimant, McrReactionType.KONG, discard, List.of(first, second, third));
    }

    private static void requireSize(
            List<McrTileInstance> concealedTiles, int expected, McrReactionType type) {
        if (concealedTiles.size() != expected) {
            throw new IllegalArgumentException(type + " must consume " + expected + " concealed tiles");
        }
    }

    private static void requireSameKind(
            List<McrTileInstance> concealedTiles, McrTileInstance discard) {
        for (McrTileInstance tile : concealedTiles) {
            if (tile.kind() != discard.kind()) {
                throw new IllegalArgumentException("pung/kong tiles must match the discard kind");
            }
        }
    }

    private static void validateChow(
            List<McrTileInstance> concealedTiles, McrTileInstance discard) {
        requireSize(concealedTiles, 2, McrReactionType.CHOW);
        Tile discardedKind = discard.kind();
        if (!discardedKind.isNumbered()) {
            throw new IllegalArgumentException("honor tiles cannot form a chow");
        }
        int[] ranks = {discardedKind.rank(), concealedTiles.get(0).kind().rank(),
                concealedTiles.get(1).kind().rank()};
        for (McrTileInstance tile : concealedTiles) {
            if (tile.kind().suit() != discardedKind.suit()) {
                throw new IllegalArgumentException("a chow must use one numbered suit");
            }
        }
        java.util.Arrays.sort(ranks);
        if (ranks[1] != ranks[0] + 1 || ranks[2] != ranks[1] + 1) {
            throw new IllegalArgumentException("chow ranks must be consecutive");
        }
    }
}
