package top.ellan.mahjong.rules.mcr;

import java.util.List;

/** Atomic complete-game result; rejection retains the exact input state. */
public record McrMatchTransition(
        boolean accepted,
        McrMatchState state,
        List<McrMatchEvent> events,
        List<McrMatchViolation> matchViolations,
        List<McrRoundViolation> roundViolations) {

    public McrMatchTransition {
        if (state == null || events == null || matchViolations == null || roundViolations == null) {
            throw new IllegalArgumentException("match transition fields are required");
        }
        events = List.copyOf(events);
        matchViolations = List.copyOf(matchViolations);
        roundViolations = List.copyOf(roundViolations);
        boolean rejected = !matchViolations.isEmpty() || !roundViolations.isEmpty();
        if (accepted == rejected || !accepted && !events.isEmpty()) {
            throw new IllegalArgumentException("match transition disposition is inconsistent");
        }
    }

    static McrMatchTransition accepted(McrMatchState state, List<McrMatchEvent> events) {
        return new McrMatchTransition(true, state, events, List.of(), List.of());
    }

    static McrMatchTransition rejectedMatch(
            McrMatchState state, McrMatchViolation violation) {
        return new McrMatchTransition(false, state, List.of(), List.of(violation), List.of());
    }

    static McrMatchTransition rejectedRound(
            McrMatchState state, List<McrRoundViolation> violations) {
        return new McrMatchTransition(false, state, List.of(), List.of(), violations);
    }
}
