package top.ellan.mahjong.rules.mcr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical bounded complete-game codec; all nested rule invariants are rerun on restore. */
public final class McrMatchSnapshotCodec {
    private static final int MAGIC = 0x4d43524d; // MCRM
    private static final int PAYLOAD_VERSION = 1;

    private final McrRoundSnapshotCodec roundCodec = new McrRoundSnapshotCodec();

    public McrMatchSnapshot snapshot(McrMatchState state, long sequence) {
        if (state == null || sequence < 0) {
            throw new IllegalArgumentException("state and non-negative sequence are required");
        }
        byte[] payload = encode(state);
        return new McrMatchSnapshot(
                McrMatchSnapshot.SCHEMA_VERSION,
                sequence,
                payload,
                McrRoundSnapshotCodec.sha256(payload));
    }

    public String stateHash(McrMatchState state) {
        return snapshot(state, 0).sha256();
    }

    public McrMatchState restore(McrMatchSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (snapshot.schemaVersion() != McrMatchSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported MCR match snapshot schema");
        }
        byte[] payload = snapshot.payload();
        if (!snapshot.sha256().equals(McrRoundSnapshotCodec.sha256(payload))) {
            throw new IllegalArgumentException("MCR match snapshot SHA-256 mismatch");
        }
        McrMatchState state;
        try {
            state = decode(payload);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("invalid MCR match snapshot payload", invalid);
        }
        if (!Arrays.equals(payload, encode(state))) {
            throw new IllegalArgumentException("MCR match snapshot payload is not canonical");
        }
        return state;
    }

    private byte[] encode(McrMatchState state) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8192);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(PAYLOAD_VERSION);
            output.writeInt(state.config().handLimit());
            output.writeLong(state.matchSeed());
            output.writeLong(state.revision());
            writeEnum(output, state.phase());
            output.writeInt(state.handsCompleted());
            output.writeLong(state.currentHandSeed());
            writeScores(output, state.cumulativeScore());
            output.writeInt(state.handResults().size());
            for (McrHandResult result : state.handResults()) writeResult(output, result);
            byte[] roundPayload = roundCodec.snapshot(state.roundState(), 0).payload();
            output.writeInt(roundPayload.length);
            output.write(roundPayload);
            output.flush();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory MCR match encoding failed", impossible);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > McrMatchSnapshot.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("MCR match snapshot exceeds payload limit");
        }
        return payload;
    }

    private McrMatchState decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC || input.readInt() != PAYLOAD_VERSION) {
                throw new IllegalArgumentException("unknown MCR match snapshot header");
            }
            McrMatchConfig config = new McrMatchConfig(input.readInt());
            long matchSeed = input.readLong();
            long revision = input.readLong();
            if (revision < 0) throw new IllegalArgumentException("negative MCR match revision");
            McrMatchPhase phase = readEnum(input, McrMatchPhase.values(), "match phase");
            int handsCompleted = readCount(
                    input, config.handLimit(), "completed-hand count");
            long currentHandSeed = input.readLong();
            Map<Wind, Integer> cumulative = readScores(input);
            int resultCount = readCount(input, config.handLimit(), "hand-result count");
            ArrayList<McrHandResult> results = new ArrayList<>(resultCount);
            for (int index = 0; index < resultCount; index++) {
                results.add(readResult(input));
            }
            int roundLength = readCount(
                    input, McrRoundSnapshot.MAX_PAYLOAD_BYTES, "round payload length");
            if (roundLength == 0) throw new IllegalArgumentException("empty nested round snapshot");
            byte[] roundPayload = input.readNBytes(roundLength);
            if (roundPayload.length != roundLength) {
                throw new IllegalArgumentException("truncated nested round snapshot");
            }
            if (input.available() != 0) {
                throw new IllegalArgumentException("trailing MCR match snapshot bytes");
            }
            McrRoundState round = roundCodec.restore(new McrRoundSnapshot(
                    McrRoundSnapshot.SCHEMA_VERSION,
                    0,
                    roundPayload,
                    McrRoundSnapshotCodec.sha256(roundPayload)));
            return new McrMatchState(
                    config,
                    matchSeed,
                    revision,
                    phase,
                    handsCompleted,
                    currentHandSeed,
                    round,
                    cumulative,
                    List.copyOf(results));
        }
    }

    private static void writeResult(DataOutputStream output, McrHandResult result)
            throws IOException {
        output.writeInt(result.handNumber());
        output.writeLong(result.handSeed());
        writeEnum(output, result.roundWind());
        writeEnum(output, result.dealerInitialSeat());
        McrRoundSnapshotCodec.writeOutcome(output, result.outcome());
        writeScores(output, result.deltasByInitialSeat());
        writeScores(output, result.cumulativeAfter());
        output.writeLong(result.roundRevision());
    }

    private static McrHandResult readResult(DataInputStream input) throws IOException {
        int handNumber = readPositiveCount(
                input, McrMatchConfig.MAX_HAND_LIMIT, "hand number");
        long handSeed = input.readLong();
        Wind roundWind = readEnum(input, Wind.values(), "result round wind");
        Wind dealer = readEnum(input, Wind.values(), "result dealer");
        McrRoundOutcome outcome = McrRoundSnapshotCodec.readOutcome(input);
        if (outcome == null) throw new IllegalArgumentException("hand result omitted outcome");
        Map<Wind, Integer> deltas = readScores(input);
        Map<Wind, Integer> cumulative = readScores(input);
        long roundRevision = input.readLong();
        return new McrHandResult(
                handNumber,
                handSeed,
                roundWind,
                dealer,
                outcome,
                deltas,
                cumulative,
                roundRevision);
    }

    private static void writeScores(DataOutputStream output, Map<Wind, Integer> scores)
            throws IOException {
        for (Wind seat : Wind.values()) output.writeInt(scores.get(seat));
    }

    private static Map<Wind, Integer> readScores(DataInputStream input) throws IOException {
        EnumMap<Wind, Integer> result = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) result.put(seat, input.readInt());
        return result;
    }

    private static <E extends Enum<E>> void writeEnum(DataOutputStream output, E value)
            throws IOException {
        output.writeByte(value.ordinal());
    }

    private static <E extends Enum<E>> E readEnum(
            DataInputStream input, E[] values, String name) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) throw new IllegalArgumentException("invalid " + name);
        return values[ordinal];
    }

    private static int readCount(DataInputStream input, int maximum, String name)
            throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid " + name);
        return count;
    }

    private static int readPositiveCount(DataInputStream input, int maximum, String name)
            throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) throw new IllegalArgumentException("invalid " + name);
        return count;
    }
}
