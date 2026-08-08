package top.ellan.mahjong.rules.mcr;

import java.util.List;

/** Atomic result: a rejection always retains the exact input state and emits no events. */
public record McrRoundTransition(
        boolean accepted,
        McrRoundState state,
        List<McrRoundEvent> events,
        List<McrRoundViolation> violations) {

    public McrRoundTransition {
        if (state == null || events == null || violations == null) {
            throw new IllegalArgumentException("transition state, events and violations are required");
        }
        events = List.copyOf(events);
        violations = List.copyOf(violations);
        if (accepted == !violations.isEmpty() || !accepted && !events.isEmpty()) {
            throw new IllegalArgumentException("transition acceptance, events and violations disagree");
        }
    }

    static McrRoundTransition accepted(McrRoundState state, List<McrRoundEvent> events) {
        return new McrRoundTransition(true, state, events, List.of());
    }

    static McrRoundTransition rejected(McrRoundState state, McrRoundViolation violation) {
        return new McrRoundTransition(false, state, List.of(), List.of(violation));
    }
}
