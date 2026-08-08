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

    McrProviderState(McrMatchState match, List<PlayerId> playersByInitialSeat) {
        this.match = Objects.requireNonNull(match, "match");
        Objects.requireNonNull(playersByInitialSeat, "playersByInitialSeat");
        if (playersByInitialSeat.size() != Wind.values().length
                || new HashSet<>(playersByInitialSeat).size() != Wind.values().length) {
            throw new IllegalArgumentException("exactly four distinct MCR players are required");
        }
        this.playersByInitialSeat = playersByInitialSeat.toArray(PlayerId[]::new);
        for (PlayerId player : this.playersByInitialSeat) {
            Objects.requireNonNull(player, "seated player");
        }
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

    McrProviderState withMatch(McrMatchState nextMatch) {
        return new McrProviderState(nextMatch, playersByInitialSeat());
    }
}
