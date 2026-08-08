package top.ellan.mahjong.rules.mcr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RuleStateSnapshot;

/** Canonical SPI envelope binding fixed player identities to the match snapshot. */
final class McrProviderSnapshotCodec {
    private static final int MAGIC = 0x4d435250; // MCRP
    private static final int VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = McrMatchSnapshot.MAX_PAYLOAD_BYTES + 128;

    private final McrMatchSnapshotCodec matchCodec = new McrMatchSnapshotCodec();

    RuleStateSnapshot snapshot(McrProviderState state, long sequence) {
        if (state == null || sequence < 0) {
            throw new IllegalArgumentException("state and non-negative sequence are required");
        }
        byte[] payload = encode(state);
        return new RuleStateSnapshot(
                McrMatchSnapshot.SCHEMA_VERSION,
                sequence,
                payload,
                McrRoundSnapshotCodec.sha256(payload));
    }

    McrProviderState restore(RuleStateSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (snapshot.schemaVersion() != McrMatchSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported MCR provider snapshot schema");
        }
        byte[] payload = snapshot.payload();
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES
                || !snapshot.sha256().equals(McrRoundSnapshotCodec.sha256(payload))) {
            throw new IllegalArgumentException("invalid MCR provider snapshot envelope");
        }
        McrProviderState state;
        try {
            state = decode(payload, snapshot.sequence());
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("invalid MCR provider snapshot", invalid);
        }
        if (!Arrays.equals(payload, encode(state))) {
            throw new IllegalArgumentException("non-canonical MCR provider snapshot");
        }
        return state;
    }

    private byte[] encode(McrProviderState state) {
        McrMatchSnapshot match = matchCodec.snapshot(state.match(), 0);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(match.payload().length + 80);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(VERSION);
            for (Wind seat : Wind.values()) {
                UUID id = state.player(seat).value();
                output.writeLong(id.getMostSignificantBits());
                output.writeLong(id.getLeastSignificantBits());
            }
            byte[] matchPayload = match.payload();
            output.writeInt(matchPayload.length);
            output.write(matchPayload);
            output.flush();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory MCR provider encoding failed", impossible);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("MCR provider snapshot exceeds its size limit");
        }
        return payload;
    }

    private McrProviderState decode(byte[] payload, long sequence) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new IllegalArgumentException("unknown MCR provider snapshot header");
            }
            ArrayList<PlayerId> players = new ArrayList<>(Wind.values().length);
            for (Wind ignored : Wind.values()) {
                players.add(new PlayerId(new UUID(input.readLong(), input.readLong())));
            }
            int matchLength = input.readInt();
            if (matchLength <= 0 || matchLength > McrMatchSnapshot.MAX_PAYLOAD_BYTES
                    || matchLength != input.available()) {
                throw new IllegalArgumentException("invalid nested MCR match snapshot length");
            }
            byte[] matchPayload = input.readNBytes(matchLength);
            if (matchPayload.length != matchLength || input.available() != 0) {
                throw new IllegalArgumentException("truncated MCR provider snapshot");
            }
            McrMatchSnapshot nested = new McrMatchSnapshot(
                    McrMatchSnapshot.SCHEMA_VERSION,
                    sequence,
                    matchPayload,
                    McrRoundSnapshotCodec.sha256(matchPayload));
            return new McrProviderState(
                    matchCodec.restore(nested), List.copyOf(players));
        }
    }
}
