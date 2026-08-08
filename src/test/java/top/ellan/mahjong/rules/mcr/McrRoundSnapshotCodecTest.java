package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McrRoundSnapshotCodecTest {
    private final McrRoundSnapshotCodec codec = new McrRoundSnapshotCodec();
    private final McrRoundEngine engine = new McrRoundEngine();

    @Test
    void initialAndOpenReactionStatesRoundTripCanonically() {
        McrRoundState initial = McrRoundState.start(20260808L, Wind.EAST);
        assertCanonicalRoundTrip(initial, 17);

        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 4, 12);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrRoundState reacting = engine.transition(
                before,
                new McrRoundAction.Discard(Wind.EAST, McrTileInstance.fromId(16))).state();
        assertEquals(McrRoundPhase.REACTIONS, reacting.phase());
        assertCanonicalRoundTrip(reacting, 18);
    }

    @Test
    void terminalEvaluationAndPaymentRoundTripWithoutTrustingSerializedDeltas() {
        McrRoundState winning = eastPureStraightInitialWin();
        McrRoundState ended = engine.transition(
                winning, new McrRoundAction.SelfDrawWin(Wind.EAST)).state();
        McrRoundSnapshot snapshot = codec.snapshot(ended, 99);

        McrRoundState restored = codec.restore(snapshot);

        assertEquals(snapshot, codec.snapshot(restored, 99));
        McrRoundOutcome.Win original = (McrRoundOutcome.Win) ended.outcome().orElseThrow();
        McrRoundOutcome.Win copy = (McrRoundOutcome.Win) restored.outcome().orElseThrow();
        assertEquals(original.evaluation(), copy.evaluation());
        assertEquals(original.payment(), copy.payment());
    }

    @Test
    void checksumTrailingBytesAndUnsupportedSchemaFailClosed() {
        McrRoundSnapshot snapshot = codec.snapshot(McrRoundState.start(7L, Wind.EAST), 3);
        byte[] corrupted = snapshot.payload();
        corrupted[corrupted.length / 2] ^= 0x40;
        McrRoundSnapshot badHash = new McrRoundSnapshot(
                snapshot.schemaVersion(), snapshot.sequence(), corrupted, snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> codec.restore(badHash));

        byte[] trailing = Arrays.copyOf(snapshot.payload(), snapshot.payload().length + 1);
        McrRoundSnapshot nonCanonical = new McrRoundSnapshot(
                snapshot.schemaVersion(),
                snapshot.sequence(),
                trailing,
                McrRoundSnapshotCodec.sha256(trailing));
        assertThrows(IllegalArgumentException.class, () -> codec.restore(nonCanonical));

        McrRoundSnapshot unsupported = new McrRoundSnapshot(
                2, snapshot.sequence(), snapshot.payload(), snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> codec.restore(unsupported));
    }

    @Test
    void snapshotPayloadAndHashAreStableAndDefensivelyCopied() {
        McrRoundState state = McrRoundState.start(1234L, Wind.SOUTH);
        McrRoundSnapshot first = codec.snapshot(state, 0);
        McrRoundSnapshot second = codec.snapshot(state, 0);
        assertEquals(first, second);
        assertEquals(first.sha256(), codec.stateHash(state));

        byte[] exposed = first.payload();
        exposed[0] ^= 0x7f;
        assertNotEquals(exposed[0], first.payload()[0]);
    }

    private void assertCanonicalRoundTrip(McrRoundState state, long sequence) {
        McrRoundSnapshot snapshot = codec.snapshot(state, sequence);
        McrRoundState restored = codec.restore(snapshot);
        assertEquals(snapshot, codec.snapshot(restored, sequence));
    }

    private static McrRoundState eastPureStraightInitialWin() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        int[] eastPositions = {0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48};
        int[] concealedBeforeWin = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int index = 0; index < eastPositions.length; index++) {
            place(order, eastPositions[index], concealedBeforeWin[index]);
        }
        place(order, 52, 32);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static void place(ArrayList<McrTileInstance> order, int position, int tileId) {
        int current = -1;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id() == tileId) {
                current = index;
                break;
            }
        }
        McrTileInstance displaced = order.get(position);
        order.set(position, order.get(current));
        order.set(current, displaced);
    }
}
