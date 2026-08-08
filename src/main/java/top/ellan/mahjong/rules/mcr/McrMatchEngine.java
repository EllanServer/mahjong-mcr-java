package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Pure complete-game transition function layered over the exact hand engine. */
public final class McrMatchEngine {
    private final McrRoundEngine roundEngine;

    public McrMatchEngine() {
        this(new McrRoundEngine());
    }

    public McrMatchEngine(McrRoundEngine roundEngine) {
        if (roundEngine == null) throw new IllegalArgumentException("round engine is required");
        this.roundEngine = roundEngine;
    }

    public McrMatchTransition transition(McrMatchState state, McrMatchAction action) {
        if (state == null) throw new IllegalArgumentException("match state is required");
        if (action == null) {
            return McrMatchTransition.rejectedMatch(state, McrMatchViolation.NULL_INPUT);
        }
        if (state.phase() == McrMatchPhase.ENDED) {
            return McrMatchTransition.rejectedMatch(state, McrMatchViolation.MATCH_ENDED);
        }
        try {
            if (action instanceof McrMatchAction.Play play) return play(state, play.action());
            if (action instanceof McrMatchAction.StartNextHand) return startNextHand(state);
            if (action instanceof McrMatchAction.EndMatch) return endMatch(state);
            return McrMatchTransition.rejectedMatch(state, McrMatchViolation.WRONG_MATCH_PHASE);
        } catch (IllegalArgumentException | IllegalStateException mismatch) {
            return McrMatchTransition.rejectedMatch(
                    state, McrMatchViolation.INTERNAL_STATE_MISMATCH);
        }
    }

    private McrMatchTransition play(McrMatchState state, McrRoundAction action) {
        if (state.phase() != McrMatchPhase.HAND_ACTIVE) {
            return McrMatchTransition.rejectedMatch(
                    state, McrMatchViolation.WRONG_MATCH_PHASE);
        }
        McrRoundTransition round = roundEngine.transition(state.roundState(), action);
        if (!round.accepted()) {
            return McrMatchTransition.rejectedRound(state, round.violations());
        }
        ArrayList<McrMatchEvent> events = new ArrayList<>(4);
        if (!round.events().isEmpty()) {
            events.add(new McrMatchEvent.RoundAdvanced(
                    state.currentHandNumber(), round.events()));
        }
        if (round.state().phase() != McrRoundPhase.ENDED) {
            McrMatchState next = new McrMatchState(
                    state.config(),
                    state.matchSeed(),
                    Math.addExact(state.revision(), 1),
                    McrMatchPhase.HAND_ACTIVE,
                    state.handsCompleted(),
                    state.currentHandSeed(),
                    round.state(),
                    state.cumulativeScore(),
                    state.handResults());
            return McrMatchTransition.accepted(next, events);
        }

        Map<Wind, Integer> handDeltas = translatePayment(state, round.state());
        Map<Wind, Integer> cumulative = McrMatchState.plus(
                state.cumulativeScore(), handDeltas);
        McrHandResult result = new McrHandResult(
                state.currentHandNumber(),
                state.currentHandSeed(),
                round.state().roundWind(),
                state.playerAt(Wind.EAST),
                round.state().outcome().orElseThrow(),
                handDeltas,
                cumulative,
                round.state().revision());
        ArrayList<McrHandResult> results = new ArrayList<>(state.handResults());
        results.add(result);
        int handsCompleted = state.handsCompleted() + 1;
        McrMatchPhase phase = handsCompleted == state.config().handLimit()
                ? McrMatchPhase.ENDED : McrMatchPhase.BETWEEN_HANDS;
        McrMatchState next = new McrMatchState(
                state.config(),
                state.matchSeed(),
                Math.addExact(state.revision(), 1),
                phase,
                handsCompleted,
                state.currentHandSeed(),
                round.state(),
                cumulative,
                results);
        events.add(new McrMatchEvent.HandCompleted(result));
        if (phase == McrMatchPhase.ENDED) {
            events.add(new McrMatchEvent.MatchEnded(cumulative));
        }
        return McrMatchTransition.accepted(next, events);
    }

    private McrMatchTransition startNextHand(McrMatchState state) {
        if (state.phase() != McrMatchPhase.BETWEEN_HANDS) {
            return McrMatchTransition.rejectedMatch(
                    state, McrMatchViolation.WRONG_MATCH_PHASE);
        }
        int handNumber = state.handsCompleted() + 1;
        long handSeed = McrMatchState.deriveHandSeed(state.matchSeed(), handNumber - 1);
        Wind roundWind = McrMatchState.roundWindForHand(handNumber);
        McrRoundState round = McrRoundState.start(handSeed, roundWind);
        McrMatchState next = new McrMatchState(
                state.config(),
                state.matchSeed(),
                Math.addExact(state.revision(), 1),
                McrMatchPhase.HAND_ACTIVE,
                state.handsCompleted(),
                handSeed,
                round,
                state.cumulativeScore(),
                state.handResults());
        return McrMatchTransition.accepted(next, List.of(new McrMatchEvent.HandStarted(
                handNumber,
                handSeed,
                roundWind,
                next.playerAt(Wind.EAST))));
    }

    private McrMatchTransition endMatch(McrMatchState state) {
        if (state.phase() != McrMatchPhase.BETWEEN_HANDS) {
            return McrMatchTransition.rejectedMatch(
                    state, McrMatchViolation.WRONG_MATCH_PHASE);
        }
        McrMatchState next = new McrMatchState(
                state.config(),
                state.matchSeed(),
                Math.addExact(state.revision(), 1),
                McrMatchPhase.ENDED,
                state.handsCompleted(),
                state.currentHandSeed(),
                state.roundState(),
                state.cumulativeScore(),
                state.handResults());
        return McrMatchTransition.accepted(
                next, List.of(new McrMatchEvent.MatchEnded(next.cumulativeScore())));
    }

    private static Map<Wind, Integer> translatePayment(
            McrMatchState match, McrRoundState round) {
        EnumMap<Wind, Integer> result = new EnumMap<>(Wind.class);
        for (Wind initialSeat : Wind.values()) result.put(initialSeat, 0);
        if (round.outcome().orElseThrow() instanceof McrRoundOutcome.Win win) {
            for (Wind logicalSeat : Wind.values()) {
                result.put(match.playerAt(logicalSeat), win.payment().delta(logicalSeat));
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
