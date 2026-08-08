package top.ellan.mahjong.rules.mcr;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable complete-game state with fixed player identities and rotating seat winds. */
public final class McrMatchState {
    public static final String HAND_SEED_ALGORITHM = "splitmix64-hand-index-v1";

    private final McrMatchConfig config;
    private final long matchSeed;
    private final long revision;
    private final McrMatchPhase phase;
    private final int handsCompleted;
    private final long currentHandSeed;
    private final McrRoundState roundState;
    private final Map<Wind, Integer> cumulativeScore;
    private final List<McrHandResult> handResults;

    McrMatchState(
            McrMatchConfig config,
            long matchSeed,
            long revision,
            McrMatchPhase phase,
            int handsCompleted,
            long currentHandSeed,
            McrRoundState roundState,
            Map<Wind, Integer> cumulativeScore,
            List<McrHandResult> handResults) {
        if (config == null || revision < 0 || phase == null || handsCompleted < 0
                || handsCompleted > config.handLimit() || roundState == null
                || handResults == null) {
            throw new IllegalArgumentException("invalid MCR match metadata");
        }
        this.config = config;
        this.matchSeed = matchSeed;
        this.revision = revision;
        this.phase = phase;
        this.handsCompleted = handsCompleted;
        this.currentHandSeed = currentHandSeed;
        this.roundState = roundState;
        this.cumulativeScore = copyScores(cumulativeScore);
        this.handResults = List.copyOf(handResults);
        validate();
    }

    public static McrMatchState start(long matchSeed) {
        return start(matchSeed, McrMatchConfig.standard());
    }

    public static McrMatchState start(long matchSeed, McrMatchConfig config) {
        if (config == null) throw new IllegalArgumentException("match config is required");
        long handSeed = deriveHandSeed(matchSeed, 0);
        return new McrMatchState(
                config,
                matchSeed,
                0,
                McrMatchPhase.HAND_ACTIVE,
                0,
                handSeed,
                McrRoundState.start(handSeed, Wind.EAST),
                zeroScores(),
                List.of());
    }

    public McrMatchConfig config() {
        return config;
    }

    public long matchSeed() {
        return matchSeed;
    }

    public long revision() {
        return revision;
    }

    public McrMatchPhase phase() {
        return phase;
    }

    public int handsCompleted() {
        return handsCompleted;
    }

    public int currentHandNumber() {
        return phase == McrMatchPhase.HAND_ACTIVE ? handsCompleted + 1 : handsCompleted;
    }

    public long currentHandSeed() {
        return currentHandSeed;
    }

    public McrRoundState roundState() {
        return roundState;
    }

    public Map<Wind, Integer> cumulativeScore() {
        return cumulativeScore;
    }

    public List<McrHandResult> handResults() {
        return handResults;
    }

    /** Returns the initial fixed player seat currently carrying this logical seat wind. */
    public Wind playerAt(Wind logicalSeat) {
        if (logicalSeat == null) throw new IllegalArgumentException("logical seat is required");
        int offset = (currentHandNumber() - 1) % Wind.values().length;
        return Wind.values()[(logicalSeat.ordinal() + offset) % Wind.values().length];
    }

    /** Returns the current logical seat wind held by one fixed player. */
    public Wind logicalSeatOf(Wind initialSeat) {
        if (initialSeat == null) throw new IllegalArgumentException("initial seat is required");
        int offset = (currentHandNumber() - 1) % Wind.values().length;
        return Wind.values()[(initialSeat.ordinal() - offset + Wind.values().length)
                % Wind.values().length];
    }

    public static Wind roundWindForHand(int handNumber) {
        if (handNumber < 1 || handNumber > McrMatchConfig.MAX_HAND_LIMIT) {
            throw new IllegalArgumentException("hand number must be in [1, 16]");
        }
        return Wind.values()[(handNumber - 1) / Wind.values().length];
    }

    public static long deriveHandSeed(long matchSeed, int zeroBasedHandIndex) {
        if (zeroBasedHandIndex < 0 || zeroBasedHandIndex >= McrMatchConfig.MAX_HAND_LIMIT) {
            throw new IllegalArgumentException("invalid MCR hand index");
        }
        long value = matchSeed + 0x9E3779B97F4A7C15L * (zeroBasedHandIndex + 1L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    static Map<Wind, Integer> zeroScores() {
        EnumMap<Wind, Integer> result = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) result.put(seat, 0);
        return Collections.unmodifiableMap(result);
    }

    private void validate() {
        if (handResults.size() != handsCompleted) {
            throw new IllegalArgumentException("hand results and completed count diverged");
        }
        Map<Wind, Integer> expectedCumulative = zeroScores();
        for (int index = 0; index < handResults.size(); index++) {
            McrHandResult result = handResults.get(index);
            int handNumber = index + 1;
            if (result.handNumber() != handNumber
                    || result.handSeed() != deriveHandSeed(matchSeed, index)
                    || result.roundWind() != roundWindForHand(handNumber)
                    || result.dealerInitialSeat() != Wind.values()[index % Wind.values().length]) {
                throw new IllegalArgumentException("hand results are not a canonical rotation");
            }
            expectedCumulative = plus(expectedCumulative, result.deltasByInitialSeat());
            if (!expectedCumulative.equals(result.cumulativeAfter())) {
                throw new IllegalArgumentException("hand cumulative score diverged");
            }
        }
        if (!expectedCumulative.equals(cumulativeScore)) {
            throw new IllegalArgumentException("match cumulative score diverged");
        }

        if (phase == McrMatchPhase.HAND_ACTIVE) {
            if (handsCompleted >= config.handLimit()
                    || roundState.phase() == McrRoundPhase.ENDED
                    || currentHandSeed != deriveHandSeed(matchSeed, handsCompleted)
                    || roundState.roundWind() != roundWindForHand(handsCompleted + 1)) {
                throw new IllegalArgumentException("active MCR match has no canonical active hand");
            }
        } else {
            if (handsCompleted == 0 || roundState.phase() != McrRoundPhase.ENDED
                    || currentHandSeed != deriveHandSeed(matchSeed, handsCompleted - 1)
                    || roundState.roundWind() != roundWindForHand(handsCompleted)) {
                throw new IllegalArgumentException("MCR hand boundary is inconsistent");
            }
            if (phase == McrMatchPhase.BETWEEN_HANDS && handsCompleted >= config.handLimit()) {
                throw new IllegalArgumentException("hand limit must end the MCR match");
            }
        }
    }

    static Map<Wind, Integer> plus(Map<Wind, Integer> left, Map<Wind, Integer> right) {
        EnumMap<Wind, Integer> result = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) {
            result.put(seat, Math.addExact(left.get(seat), right.get(seat)));
        }
        return Collections.unmodifiableMap(result);
    }

    private static Map<Wind, Integer> copyScores(Map<Wind, Integer> source) {
        if (source == null || source.size() != Wind.values().length) {
            throw new IllegalArgumentException("cumulative score must contain four seats");
        }
        EnumMap<Wind, Integer> result = new EnumMap<>(Wind.class);
        int sum = 0;
        for (Wind seat : Wind.values()) {
            Integer score = source.get(seat);
            if (score == null) throw new IllegalArgumentException("missing score for " + seat);
            result.put(seat, score);
            sum = Math.addExact(sum, score);
        }
        if (sum != 0) throw new IllegalArgumentException("cumulative score must be zero-sum");
        return Collections.unmodifiableMap(result);
    }
}
