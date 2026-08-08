package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class McrMatchSnapshotCodecTest {
    private final McrMatchSnapshotCodec codec = new McrMatchSnapshotCodec();
    private final McrMatchEngine engine = new McrMatchEngine();

    @Test
    void initialAndNextHandStatesRoundTripCanonically() {
        assertCanonicalRoundTrip(McrMatchState.start(20260808L), 4);

        McrMatchState boundary = completeFirstHand(new McrMatchConfig(2));
        assertCanonicalRoundTrip(boundary, 5);
        McrMatchState next = engine.transition(
                boundary, new McrMatchAction.StartNextHand()).state();
        McrMatchState restored = assertCanonicalRoundTrip(next, 6);
        assertEquals(Wind.SOUTH, restored.playerAt(Wind.EAST));
        assertEquals(boundary.cumulativeScore(), restored.cumulativeScore());
    }

    @Test
    void terminalOutcomeHistoryAndBoundPaymentRoundTrip() {
        McrMatchState ended = completeFirstHand(new McrMatchConfig(1));
        McrMatchState restored = assertCanonicalRoundTrip(ended, 99);

        assertEquals(McrMatchPhase.ENDED, restored.phase());
        assertEquals(1, restored.handResults().size());
        assertEquals(ended.handResults().getFirst().outcome(),
                restored.handResults().getFirst().outcome());
        assertEquals(ended.cumulativeScore(), restored.cumulativeScore());
        assertEquals(0, restored.cumulativeScore().values().stream()
                .mapToInt(Integer::intValue).sum());
    }

    @Test
    void checksumTrailingBytesAndUnsupportedSchemaFailClosed() {
        McrMatchSnapshot snapshot = codec.snapshot(McrMatchState.start(7L), 3);
        byte[] corrupted = snapshot.payload();
        corrupted[corrupted.length / 2] ^= 0x20;
        McrMatchSnapshot badHash = new McrMatchSnapshot(
                snapshot.schemaVersion(), snapshot.sequence(), corrupted, snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> codec.restore(badHash));

        byte[] trailing = Arrays.copyOf(snapshot.payload(), snapshot.payload().length + 1);
        McrMatchSnapshot nonCanonical = new McrMatchSnapshot(
                snapshot.schemaVersion(),
                snapshot.sequence(),
                trailing,
                McrRoundSnapshotCodec.sha256(trailing));
        assertThrows(IllegalArgumentException.class, () -> codec.restore(nonCanonical));

        McrMatchSnapshot unsupported = new McrMatchSnapshot(
                2, snapshot.sequence(), snapshot.payload(), snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> codec.restore(unsupported));
    }

    @Test
    void forgedCumulativeScoreWithARecomputedHashStillFailsInvariants() {
        McrMatchSnapshot snapshot = codec.snapshot(
                completeFirstHand(new McrMatchConfig(1)), 7);
        byte[] forged = snapshot.payload();
        int cumulativeScoreOffset = 4 + 4 + 4 + 8 + 8 + 1 + 4 + 8;
        forged[cumulativeScoreOffset + Integer.BYTES - 1] ^= 0x01;
        McrMatchSnapshot rehashed = new McrMatchSnapshot(
                snapshot.schemaVersion(),
                snapshot.sequence(),
                forged,
                McrRoundSnapshotCodec.sha256(forged));

        assertThrows(IllegalArgumentException.class, () -> codec.restore(rehashed));
    }

    @Test
    void payloadAndHashAreStableAndDefensivelyCopied() {
        McrMatchState state = McrMatchState.start(1234L);
        McrMatchSnapshot first = codec.snapshot(state, 0);
        McrMatchSnapshot second = codec.snapshot(state, 0);
        assertEquals(first, second);
        assertEquals(first.sha256(), codec.stateHash(state));

        byte[] exposed = first.payload();
        exposed[0] ^= 0x7f;
        assertNotEquals(exposed[0], first.payload()[0]);
    }

    private McrMatchState assertCanonicalRoundTrip(McrMatchState state, long sequence) {
        McrMatchSnapshot snapshot = codec.snapshot(state, sequence);
        McrMatchState restored = codec.restore(snapshot);
        assertEquals(snapshot, codec.snapshot(restored, sequence));
        return restored;
    }

    private McrMatchState completeFirstHand(McrMatchConfig config) {
        long matchSeed = 0x4d43522d736e6170L;
        McrMatchState state = new McrMatchState(
                config,
                matchSeed,
                0,
                McrMatchPhase.HAND_ACTIVE,
                0,
                McrMatchState.deriveHandSeed(matchSeed, 0),
                eastPureStraightInitialWin(),
                McrMatchState.zeroScores(),
                List.of());
        return engine.transition(
                state,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST))).state();
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
