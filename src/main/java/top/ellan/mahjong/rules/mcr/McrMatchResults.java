package top.ellan.mahjong.rules.mcr;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.spi.RuleMatchResult;
import top.ellan.mahjong.spi.RulePlayerResult;
import top.ellan.mahjong.spi.SeatId;

/** Canonical Green Book terminal result projection for durable core history. */
final class McrMatchResults {
    private static final String RANK_SYSTEM = "mcr.green-book.raw-score.v1";
    private static final int PAYLOAD_VERSION = 1;

    private McrMatchResults() {}

    static Optional<RuleMatchResult> from(McrProviderState state) {
        McrMatchState match = state.match();
        if (match.phase() != McrMatchPhase.ENDED) {
            return Optional.empty();
        }
        ArrayList<RulePlayerResult> players = new ArrayList<>(Wind.values().length);
        for (Wind seat : Wind.values()) {
            int score = match.cumulativeScore().get(seat);
            int placement = 1;
            for (Wind other : Wind.values()) {
                if (match.cumulativeScore().get(other) > score) {
                    placement++;
                }
            }
            long rankingPointsMilli = Math.multiplyExact((long) score, 1_000L);
            players.add(new RulePlayerResult(
                    state.player(seat),
                    new SeatId(seat.ordinal()),
                    placement,
                    score,
                    rankingPointsMilli,
                    payload(seat.ordinal(), placement, score, rankingPointsMilli)));
        }
        return Optional.of(new RuleMatchResult(RANK_SYSTEM, List.copyOf(players)));
    }

    private static byte[] payload(
            int seat, int placement, int score, long rankingPointsMilli) {
        return ByteBuffer.allocate(Integer.BYTES * 4 + Long.BYTES)
                .putInt(PAYLOAD_VERSION)
                .putInt(seat)
                .putInt(placement)
                .putInt(score)
                .putLong(rankingPointsMilli)
                .array();
    }
}
