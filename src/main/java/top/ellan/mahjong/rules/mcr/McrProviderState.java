package top.ellan.mahjong.rules.mcr;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RuleState;

/** SPI identity envelope around a platform-independent MCR match. */
public final class McrProviderState implements RuleState {
    private final McrMatchState match;
    private final PlayerId[] playersByInitialSeat;
    private final McrProjectionIds projectionIds;
    private final short[] wallSlotsByProjection;
    private final McrOpeningLayout openingLayout;

    McrProviderState(McrMatchState match, List<PlayerId> playersByInitialSeat) {
        this(
                match,
                validatedPlayers(playersByInitialSeat),
                McrProjectionIds.forHand(Objects.requireNonNull(match, "match").currentHandSeed()),
                null,
                null);
    }

    private McrProviderState(
            McrMatchState match,
            PlayerId[] playersByInitialSeat,
            McrProjectionIds projectionIds,
            short[] wallSlotsByProjection,
            McrOpeningLayout openingLayout) {
        this.match = Objects.requireNonNull(match, "match");
        this.playersByInitialSeat = playersByInitialSeat;
        this.projectionIds = Objects.requireNonNull(projectionIds, "projectionIds");
        this.wallSlotsByProjection = wallSlotsByProjection == null
                ? createWallSlots(match.currentHandSeed(), projectionIds)
                : wallSlotsByProjection;
        this.openingLayout = openingLayout == null
                ? McrOpeningLayout.forMatch(match)
                : openingLayout;
    }

    public McrMatchState match() {
        return match;
    }

    public PlayerId player(Wind initialSeat) {
        return playersByInitialSeat[Objects.requireNonNull(initialSeat, "initialSeat").ordinal()];
    }

    public Wind initialSeat(PlayerId player) {
        Objects.requireNonNull(player, "player");
        for (Wind seat : Wind.values()) {
            if (playersByInitialSeat[seat.ordinal()].equals(player)) return seat;
        }
        return null;
    }

    List<PlayerId> playersByInitialSeat() {
        return List.copyOf(Arrays.asList(playersByInitialSeat.clone()));
    }

    McrProjectionIds projectionIds() {
        return projectionIds;
    }

    int wallSlot(long projectionId) {
        if (projectionId < 0 || projectionId >= wallSlotsByProjection.length) {
            throw new IllegalArgumentException("invalid projected MCR tile");
        }
        return Short.toUnsignedInt(wallSlotsByProjection[(int) projectionId]);
    }

    McrOpeningLayout openingLayout() {
        return openingLayout;
    }

    McrProviderState withMatch(McrMatchState nextMatch) {
        if (match.currentHandSeed() == nextMatch.currentHandSeed()) {
            return new McrProviderState(
                    nextMatch,
                    playersByInitialSeat,
                    projectionIds,
                    wallSlotsByProjection,
                    openingLayout);
        }
        McrProjectionIds nextProjectionIds =
                McrProjectionIds.forHand(nextMatch.currentHandSeed());
        return new McrProviderState(
                nextMatch, playersByInitialSeat, nextProjectionIds, null, null);
    }

    private static PlayerId[] validatedPlayers(List<PlayerId> players) {
        Objects.requireNonNull(players, "playersByInitialSeat");
        if (players.size() != Wind.values().length
                || new HashSet<>(players).size() != Wind.values().length) {
            throw new IllegalArgumentException("exactly four distinct MCR players are required");
        }
        PlayerId[] result = players.toArray(PlayerId[]::new);
        for (PlayerId player : result) {
            Objects.requireNonNull(player, "seated player");
        }
        return result;
    }

    private static short[] createWallSlots(long handSeed, McrProjectionIds projectionIds) {
        short[] result = new short[McrTileInstance.PHYSICAL_TILE_COUNT];
        McrWall wall = McrWall.shuffled(handSeed);
        for (int slot = 0; slot < result.length; slot++) {
            int projection = Math.toIntExact(projectionIds.project(wall.peekAt(slot)));
            result[projection] = (short) slot;
        }
        return result;
    }
}
