package top.ellan.mahjong.rules.mcr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;

/** Canonical bounded MCR hand codec; Java native serialization is never used. */
public final class McrRoundSnapshotCodec {
    private static final int MAGIC = 0x4d435232; // MCR2
    private static final int PAYLOAD_VERSION = 1;

    public McrRoundSnapshot snapshot(McrRoundState state, long sequence) {
        if (state == null || sequence < 0) {
            throw new IllegalArgumentException("state and non-negative sequence are required");
        }
        byte[] payload = encode(state);
        return new McrRoundSnapshot(
                McrRoundSnapshot.SCHEMA_VERSION,
                sequence,
                payload,
                sha256(payload));
    }

    public String stateHash(McrRoundState state) {
        return snapshot(state, 0).sha256();
    }

    public McrRoundState restore(McrRoundSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot is required");
        if (snapshot.schemaVersion() != McrRoundSnapshot.SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported MCR snapshot schema");
        }
        byte[] payload = snapshot.payload();
        if (!snapshot.sha256().equals(sha256(payload))) {
            throw new IllegalArgumentException("MCR snapshot SHA-256 mismatch");
        }
        McrRoundState state;
        try {
            state = decode(payload);
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("invalid MCR snapshot payload", invalid);
        }
        if (!Arrays.equals(payload, encode(state))) {
            throw new IllegalArgumentException("MCR snapshot payload is not canonical");
        }
        return state;
    }

    static String sha256(byte[] payload) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK does not provide SHA-256", impossible);
        }
    }

    private static byte[] encode(McrRoundState state) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(4096);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeInt(PAYLOAD_VERSION);
            output.writeLong(state.revision());
            writeEnum(output, state.roundWind());
            writeEnum(output, state.currentSeat());
            writeEnum(output, state.phase());
            for (Wind seat : Wind.values()) writeTiles(output, state.hand(seat));
            for (Wind seat : Wind.values()) writeTiles(output, state.flowers(seat));
            for (Wind seat : Wind.values()) {
                List<McrPhysicalMeld> melds = state.melds(seat);
                output.writeInt(melds.size());
                for (McrPhysicalMeld meld : melds) writeMeld(output, meld);
            }
            for (Wind seat : Wind.values()) {
                List<McrPhysicalDiscard> river = state.river(seat);
                output.writeInt(river.size());
                for (McrPhysicalDiscard discard : river) writeDiscard(output, discard);
            }
            writeTiles(output, state.wall().remainingTiles());
            writeOptionalTile(output, state.lastDraw().orElse(null));
            writeOptionalEnum(output, state.lastDrawSource().orElse(null));
            McrReactionWindow window = state.reactionWindow().orElse(null);
            output.writeBoolean(window != null);
            if (window != null) writeReactionWindow(output, window);
            McrRobbedKongClaim robbed = state.robbedKongClaim().orElse(null);
            output.writeBoolean(robbed != null);
            if (robbed != null) {
                writeEnum(output, robbed.sourceSeat());
                writeEnum(output, robbed.winner());
                output.writeByte(robbed.tile().id());
            }
            writeOutcome(output, state.outcome().orElse(null));
            output.flush();
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory MCR snapshot encoding failed", impossible);
        }
        byte[] payload = bytes.toByteArray();
        if (payload.length > McrRoundSnapshot.MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("MCR snapshot exceeds payload limit");
        }
        return payload;
    }

    private static McrRoundState decode(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (input.readInt() != MAGIC || input.readInt() != PAYLOAD_VERSION) {
                throw new IllegalArgumentException("unknown MCR snapshot header");
            }
            long revision = input.readLong();
            if (revision < 0) throw new IllegalArgumentException("negative round revision");
            Wind roundWind = readEnum(input, Wind.values(), "round wind");
            Wind currentSeat = readEnum(input, Wind.values(), "current seat");
            McrRoundPhase phase = readEnum(input, McrRoundPhase.values(), "round phase");
            EnumMap<Wind, List<McrTileInstance>> hands = new EnumMap<>(Wind.class);
            for (Wind seat : Wind.values()) hands.put(seat, readTiles(input, 14, "hand"));
            EnumMap<Wind, List<McrTileInstance>> flowers = new EnumMap<>(Wind.class);
            for (Wind seat : Wind.values()) flowers.put(seat, readTiles(input, 8, "flowers"));
            EnumMap<Wind, List<McrPhysicalMeld>> melds = new EnumMap<>(Wind.class);
            for (Wind seat : Wind.values()) {
                int count = readCount(input, 4, "meld count");
                ArrayList<McrPhysicalMeld> values = new ArrayList<>(count);
                for (int index = 0; index < count; index++) values.add(readMeld(input));
                melds.put(seat, List.copyOf(values));
            }
            EnumMap<Wind, List<McrPhysicalDiscard>> rivers = new EnumMap<>(Wind.class);
            for (Wind seat : Wind.values()) {
                int count = readCount(input, McrTileInstance.PHYSICAL_TILE_COUNT, "river count");
                ArrayList<McrPhysicalDiscard> values = new ArrayList<>(count);
                for (int index = 0; index < count; index++) values.add(readDiscard(input));
                rivers.put(seat, List.copyOf(values));
            }
            McrWall wall = McrWall.fromRemainingOrder(
                    readTiles(input, McrTileInstance.PHYSICAL_TILE_COUNT, "wall"));
            McrTileInstance lastDraw = readOptionalTile(input);
            McrDrawSource lastDrawSource = readOptionalEnum(
                    input, McrDrawSource.values(), "last draw source");
            McrReactionWindow window = input.readBoolean() ? readReactionWindow(input) : null;
            McrRobbedKongClaim robbed = null;
            if (input.readBoolean()) {
                robbed = new McrRobbedKongClaim(
                        readEnum(input, Wind.values(), "robbed-kong source"),
                        readEnum(input, Wind.values(), "robbed-kong winner"),
                        readTile(input));
            }
            McrRoundOutcome outcome = readOutcome(input);
            if (input.available() != 0) throw new IllegalArgumentException("trailing MCR snapshot bytes");
            return new McrRoundState(
                    revision,
                    roundWind,
                    currentSeat,
                    phase,
                    hands,
                    flowers,
                    melds,
                    rivers,
                    wall,
                    lastDraw,
                    lastDrawSource,
                    window,
                    robbed,
                    outcome);
        }
    }

    private static void writeMeld(DataOutputStream output, McrPhysicalMeld meld) throws IOException {
        writeEnum(output, meld.origin());
        writeTiles(output, meld.tiles());
        writeOptionalTile(output, meld.claimedDiscard());
        writeOptionalEnum(output, meld.sourceSeat());
    }

    private static McrPhysicalMeld readMeld(DataInputStream input) throws IOException {
        return new McrPhysicalMeld(
                readEnum(input, McrMeldOrigin.values(), "meld origin"),
                readTiles(input, 4, "meld tiles"),
                readOptionalTile(input),
                readOptionalEnum(input, Wind.values(), "meld source"));
    }

    private static void writeDiscard(DataOutputStream output, McrPhysicalDiscard discard)
            throws IOException {
        writeEnum(output, discard.sourceSeat());
        output.writeByte(discard.tile().id());
        writeEnum(output, discard.status());
        writeOptionalEnum(output, discard.claimant());
    }

    private static McrPhysicalDiscard readDiscard(DataInputStream input) throws IOException {
        return new McrPhysicalDiscard(
                readEnum(input, Wind.values(), "discard source"),
                readTile(input),
                readEnum(input, McrDiscardStatus.values(), "discard status"),
                readOptionalEnum(input, Wind.values(), "discard claimant"));
    }

    private static void writeReactionWindow(DataOutputStream output, McrReactionWindow window)
            throws IOException {
        writeEnum(output, window.origin());
        writeEnum(output, window.discarder());
        output.writeByte(window.discard().id());
        int optionCount = 0;
        for (Wind seat : Wind.values()) {
            if (seat != window.discarder()) optionCount += window.legalOptions(seat).size();
        }
        output.writeInt(optionCount);
        for (Wind seat : Wind.values()) {
            if (seat == window.discarder()) continue;
            for (McrReaction option : window.legalOptions(seat)) writeReaction(output, option);
        }
        output.writeInt(window.decisions().size());
        for (Wind seat : Wind.values()) {
            McrReaction decision = window.decisions().get(seat);
            if (decision != null) writeReaction(output, decision);
        }
    }

    private static McrReactionWindow readReactionWindow(DataInputStream input) throws IOException {
        McrReactionOrigin origin = readEnum(input, McrReactionOrigin.values(), "reaction origin");
        Wind source = readEnum(input, Wind.values(), "reaction source");
        McrTileInstance tile = readTile(input);
        int optionCount = readCount(input, 256, "legal reaction count");
        ArrayList<McrReaction> options = new ArrayList<>(optionCount);
        for (int index = 0; index < optionCount; index++) options.add(readReaction(input));
        McrReactionWindow window = origin == McrReactionOrigin.DISCARD
                ? McrReactionWindow.open(source, tile, options)
                : McrReactionWindow.openAddedKong(source, tile, options);
        int decisionCount = readCount(input, 3, "reaction decision count");
        for (int index = 0; index < decisionCount; index++) window = window.respond(readReaction(input));
        return window;
    }

    private static void writeReaction(DataOutputStream output, McrReaction reaction) throws IOException {
        writeEnum(output, reaction.claimant());
        writeEnum(output, reaction.type());
        output.writeByte(reaction.discard().id());
        writeTiles(output, reaction.concealedTiles());
    }

    private static McrReaction readReaction(DataInputStream input) throws IOException {
        return new McrReaction(
                readEnum(input, Wind.values(), "reaction claimant"),
                readEnum(input, McrReactionType.values(), "reaction type"),
                readTile(input),
                readTiles(input, 3, "reaction concealed tiles"));
    }

    private static void writeOutcome(DataOutputStream output, McrRoundOutcome outcome)
            throws IOException {
        if (outcome == null) {
            output.writeByte(0);
        } else if (outcome instanceof McrRoundOutcome.ExhaustiveDraw) {
            output.writeByte(1);
        } else {
            McrRoundOutcome.Win win = (McrRoundOutcome.Win) outcome;
            output.writeByte(2);
            writeEnum(output, win.winner());
            writeOptionalEnum(output, win.discarder());
            writeEvaluation(output, win.evaluation());
        }
    }

    private static McrRoundOutcome readOutcome(DataInputStream input) throws IOException {
        int kind = input.readUnsignedByte();
        if (kind == 0) return null;
        if (kind == 1) return new McrRoundOutcome.ExhaustiveDraw();
        if (kind != 2) throw new IllegalArgumentException("unknown MCR outcome type");
        Wind winner = readEnum(input, Wind.values(), "winner");
        Wind discarder = readOptionalEnum(input, Wind.values(), "winner discarder");
        WinEvaluation evaluation = readEvaluation(input);
        return new McrRoundOutcome.Win(
                winner,
                discarder,
                evaluation,
                McrPayments.settle(evaluation, winner, discarder));
    }

    private static void writeEvaluation(DataOutputStream output, WinEvaluation evaluation)
            throws IOException {
        writeEnum(output, evaluation.status());
        writeEnum(output, evaluation.winningTile());
        writeEnum(output, evaluation.winMethod());
        output.writeInt(evaluation.qualifyingFan());
        output.writeInt(evaluation.flowerFan());
        output.writeInt(evaluation.totalFan());
        output.writeInt(evaluation.awards().size());
        for (FanAward award : evaluation.awards()) {
            writeEnum(output, award.fan());
            output.writeInt(award.count());
        }
        output.writeInt(evaluation.internalAwards().size());
        for (InternalAward award : evaluation.internalAwards()) {
            writeEnum(output, award.combination());
            output.writeInt(award.count());
        }
        output.writeInt(evaluation.violations().size());
        for (String violation : evaluation.violations()) writeString(output, violation);
    }

    private static WinEvaluation readEvaluation(DataInputStream input) throws IOException {
        EvaluationStatus status = readEnum(input, EvaluationStatus.values(), "evaluation status");
        Tile winningTile = readEnum(input, Tile.values(), "winning tile");
        WinMethod method = readEnum(input, WinMethod.values(), "win method");
        int qualifying = readCount(input, 4096, "qualifying fan");
        int flowers = readCount(input, 8, "flower fan");
        int total = readCount(input, 4104, "total fan");
        int awardCount = readCount(input, Fan.values().length, "fan award count");
        ArrayList<FanAward> awards = new ArrayList<>(awardCount);
        for (int index = 0; index < awardCount; index++) {
            awards.add(new FanAward(
                    readEnum(input, Fan.values(), "fan award"),
                    readPositiveCount(input, 8, "fan multiplicity")));
        }
        int internalCount = readCount(
                input, InternalCombination.values().length, "internal award count");
        ArrayList<InternalAward> internal = new ArrayList<>(internalCount);
        for (int index = 0; index < internalCount; index++) {
            internal.add(new InternalAward(
                    readEnum(input, InternalCombination.values(), "internal award"),
                    readPositiveCount(input, 8, "internal multiplicity")));
        }
        int violationCount = readCount(input, 16, "evaluation violation count");
        ArrayList<String> violations = new ArrayList<>(violationCount);
        for (int index = 0; index < violationCount; index++) violations.add(readString(input));
        return new WinEvaluation(
                status,
                winningTile,
                method,
                qualifying,
                flowers,
                total,
                awards,
                internal,
                violations);
    }

    private static void writeTiles(DataOutputStream output, List<McrTileInstance> tiles)
            throws IOException {
        output.writeInt(tiles.size());
        for (McrTileInstance tile : tiles) output.writeByte(tile.id());
    }

    private static List<McrTileInstance> readTiles(
            DataInputStream input, int maximum, String name) throws IOException {
        int count = readCount(input, maximum, name + " count");
        ArrayList<McrTileInstance> tiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) tiles.add(readTile(input));
        return List.copyOf(tiles);
    }

    private static void writeOptionalTile(DataOutputStream output, McrTileInstance tile)
            throws IOException {
        output.writeBoolean(tile != null);
        if (tile != null) output.writeByte(tile.id());
    }

    private static McrTileInstance readOptionalTile(DataInputStream input) throws IOException {
        return input.readBoolean() ? readTile(input) : null;
    }

    private static McrTileInstance readTile(DataInputStream input) throws IOException {
        int id = input.readUnsignedByte();
        return McrTileInstance.fromId(id);
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > 4096) throw new IllegalArgumentException("snapshot string is too long");
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length = readCount(input, 4096, "string length");
        byte[] encoded = input.readNBytes(length);
        if (encoded.length != length) throw new IllegalArgumentException("truncated snapshot string");
        return new String(encoded, StandardCharsets.UTF_8);
    }

    private static <E extends Enum<E>> void writeEnum(DataOutputStream output, E value)
            throws IOException {
        output.writeByte(value.ordinal());
    }

    private static <E extends Enum<E>> void writeOptionalEnum(DataOutputStream output, E value)
            throws IOException {
        output.writeBoolean(value != null);
        if (value != null) writeEnum(output, value);
    }

    private static <E extends Enum<E>> E readEnum(
            DataInputStream input, E[] values, String name) throws IOException {
        int ordinal = input.readUnsignedByte();
        if (ordinal >= values.length) throw new IllegalArgumentException("invalid " + name);
        return values[ordinal];
    }

    private static <E extends Enum<E>> E readOptionalEnum(
            DataInputStream input, E[] values, String name) throws IOException {
        return input.readBoolean() ? readEnum(input, values, name) : null;
    }

    private static int readCount(DataInputStream input, int maximum, String name) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("invalid " + name);
        return count;
    }

    private static int readPositiveCount(
            DataInputStream input, int maximum, String name) throws IOException {
        int count = input.readInt();
        if (count <= 0 || count > maximum) throw new IllegalArgumentException("invalid " + name);
        return count;
    }
}
