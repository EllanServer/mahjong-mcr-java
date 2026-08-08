package top.ellan.mahjong.rules.mcr;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import top.ellan.mahjong.spi.RuleEvent;

/** Compact canonical event summaries using fixed player seats and opaque tile IDs. */
final class McrSpiEvents {
    private McrSpiEvents() {}

    static List<RuleEvent> encode(List<McrMatchEvent> events, McrMatchState source) {
        if (events == null || source == null) {
            throw new IllegalArgumentException("events and source match are required");
        }
        ArrayList<RuleEvent> result = new ArrayList<>(events.size());
        for (McrMatchEvent event : events) {
            if (event instanceof McrMatchEvent.RoundAdvanced advanced) {
                result.add(new RuleEvent("round_advanced", roundAdvanced(advanced, source)));
            } else if (event instanceof McrMatchEvent.HandCompleted completed) {
                result.add(new RuleEvent("hand_completed", handCompleted(completed.result())));
            } else if (event instanceof McrMatchEvent.HandStarted started) {
                result.add(new RuleEvent("hand_started", handStarted(started)));
            } else if (event instanceof McrMatchEvent.MatchEnded ended) {
                result.add(new RuleEvent("match_ended", scores(ended.finalScore())));
            } else {
                throw new IllegalArgumentException("unknown MCR match event");
            }
        }
        return List.copyOf(result);
    }

    private static byte[] roundAdvanced(
            McrMatchEvent.RoundAdvanced advanced, McrMatchState source) {
        return bytes(output -> {
            output.writeInt(advanced.handNumber());
            output.writeInt(advanced.events().size());
            McrProjectionIds ids = McrProjectionIds.forHand(source.currentHandSeed());
            for (McrRoundEvent event : advanced.events()) {
                writeRoundEvent(output, event, source, ids);
            }
        });
    }

    private static void writeRoundEvent(
            DataOutputStream output,
            McrRoundEvent event,
            McrMatchState source,
            McrProjectionIds ids) throws IOException {
        if (event instanceof McrRoundEvent.TileDiscarded discarded) {
            output.writeByte(0);
            writeSeat(output, source, discarded.seat());
            writeTile(output, ids, discarded.tile());
        } else if (event instanceof McrRoundEvent.ReactionRecorded reaction) {
            output.writeByte(1);
            writeSeat(output, source, reaction.seat());
            output.writeByte(reaction.type().ordinal());
        } else if (event instanceof McrRoundEvent.DiscardUnclaimed unclaimed) {
            output.writeByte(2);
            writeSeat(output, source, unclaimed.seat());
            writeTile(output, ids, unclaimed.tile());
        } else if (event instanceof McrRoundEvent.AddedKongProposed kong) {
            output.writeByte(3);
            writeSeat(output, source, kong.seat());
            writeTile(output, ids, kong.tile());
        } else if (event instanceof McrRoundEvent.MeldFormed formed) {
            output.writeByte(4);
            writeSeat(output, source, formed.seat());
            output.writeByte(formed.meld().origin().ordinal());
            writeTiles(output, ids, formed.meld().tiles());
        } else if (event instanceof McrRoundEvent.TileDrawn drawn) {
            output.writeByte(5);
            writeSeat(output, source, drawn.seat());
            writeTile(output, ids, drawn.tile());
            output.writeByte(drawn.source().ordinal());
        } else if (event instanceof McrRoundEvent.FlowerExposed flower) {
            output.writeByte(6);
            writeSeat(output, source, flower.seat());
            writeTile(output, ids, flower.tile());
        } else if (event instanceof McrRoundEvent.RoundWon won) {
            output.writeByte(7);
            writeSeat(output, source, won.outcome().winner());
            writeOptionalSeat(output, source, won.outcome().discarder());
            writeWinSummary(output, won.outcome().evaluation());
        } else if (event instanceof McrRoundEvent.ExhaustiveDraw) {
            output.writeByte(8);
        } else {
            throw new IllegalArgumentException("unknown MCR round event");
        }
    }

    private static byte[] handCompleted(McrHandResult result) {
        return bytes(output -> {
            output.writeInt(result.handNumber());
            output.writeLong(result.handSeed());
            output.writeByte(result.roundWind().ordinal());
            output.writeByte(result.dealerInitialSeat().ordinal());
            if (result.outcome() instanceof McrRoundOutcome.ExhaustiveDraw) {
                output.writeByte(0);
            } else {
                McrRoundOutcome.Win win = (McrRoundOutcome.Win) result.outcome();
                output.writeByte(1);
                output.writeByte(fixedSeat(result.handNumber(), win.winner()).ordinal());
                Wind discarder = win.discarder();
                output.writeByte(discarder == null
                        ? 0xff : fixedSeat(result.handNumber(), discarder).ordinal());
                writeWinSummary(output, win.evaluation());
            }
            writeScores(output, result.deltasByInitialSeat());
            writeScores(output, result.cumulativeAfter());
            output.writeLong(result.roundRevision());
        });
    }

    private static byte[] handStarted(McrMatchEvent.HandStarted started) {
        return bytes(output -> {
            output.writeInt(started.handNumber());
            output.writeLong(started.handSeed());
            output.writeByte(started.roundWind().ordinal());
            output.writeByte(started.dealerInitialSeat().ordinal());
        });
    }

    private static byte[] scores(Map<Wind, Integer> scores) {
        return bytes(output -> writeScores(output, scores));
    }

    private static void writeWinSummary(DataOutputStream output, WinEvaluation evaluation)
            throws IOException {
        output.writeByte(evaluation.winningTile().ordinal());
        output.writeByte(evaluation.winMethod().ordinal());
        output.writeInt(evaluation.qualifyingFan());
        output.writeInt(evaluation.flowerFan());
        output.writeInt(evaluation.totalFan());
    }

    private static void writeScores(DataOutputStream output, Map<Wind, Integer> scores)
            throws IOException {
        for (Wind seat : Wind.values()) output.writeInt(scores.get(seat));
    }

    private static void writeSeat(
            DataOutputStream output, McrMatchState source, Wind logicalSeat) throws IOException {
        output.writeByte(source.playerAt(logicalSeat).ordinal());
    }

    private static void writeOptionalSeat(
            DataOutputStream output, McrMatchState source, Wind logicalSeat) throws IOException {
        output.writeByte(logicalSeat == null ? 0xff : source.playerAt(logicalSeat).ordinal());
    }

    private static Wind fixedSeat(int handNumber, Wind logicalSeat) {
        int offset = (handNumber - 1) % Wind.values().length;
        return Wind.values()[(logicalSeat.ordinal() + offset) % Wind.values().length];
    }

    private static void writeTile(
            DataOutputStream output, McrProjectionIds ids, McrTileInstance tile) throws IOException {
        output.writeByte(Math.toIntExact(ids.project(tile)));
    }

    private static void writeTiles(
            DataOutputStream output,
            McrProjectionIds ids,
            List<McrTileInstance> tiles) throws IOException {
        int[] projected = new int[tiles.size()];
        for (int index = 0; index < tiles.size(); index++) {
            projected[index] = Math.toIntExact(ids.project(tiles.get(index)));
        }
        Arrays.sort(projected);
        output.writeByte(projected.length);
        for (int projection : projected) output.writeByte(projection);
    }

    private static byte[] bytes(Encoder encoder) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            encoder.write(output);
            output.flush();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory MCR event encoding failed", impossible);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface Encoder {
        void write(DataOutputStream output) throws IOException;
    }
}
