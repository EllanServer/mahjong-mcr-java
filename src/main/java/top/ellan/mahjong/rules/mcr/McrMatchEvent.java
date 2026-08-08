package top.ellan.mahjong.rules.mcr;

import java.util.List;
import java.util.Map;

/** Typed facts emitted by accepted complete-game transitions. */
public sealed interface McrMatchEvent
        permits McrMatchEvent.RoundAdvanced,
                McrMatchEvent.HandCompleted,
                McrMatchEvent.HandStarted,
                McrMatchEvent.MatchEnded {

    record RoundAdvanced(int handNumber, List<McrRoundEvent> events) implements McrMatchEvent {
        public RoundAdvanced {
            if (handNumber < 1 || events == null || events.isEmpty()) {
                throw new IllegalArgumentException("round advancement requires typed events");
            }
            events = List.copyOf(events);
        }
    }

    record HandCompleted(McrHandResult result) implements McrMatchEvent {
        public HandCompleted {
            if (result == null) throw new IllegalArgumentException("hand result is required");
        }
    }

    record HandStarted(
            int handNumber, long handSeed, Wind roundWind, Wind dealerInitialSeat)
            implements McrMatchEvent {
        public HandStarted {
            if (handNumber < 1 || roundWind == null || dealerInitialSeat == null) {
                throw new IllegalArgumentException("invalid started-hand metadata");
            }
            if (roundWind != McrMatchState.roundWindForHand(handNumber)
                    || dealerInitialSeat
                    != Wind.values()[(handNumber - 1) % Wind.values().length]) {
                throw new IllegalArgumentException("started hand diverged from canonical rotation");
            }
        }
    }

    record MatchEnded(Map<Wind, Integer> finalScore) implements McrMatchEvent {
        public MatchEnded {
            finalScore = Map.copyOf(finalScore);
            if (finalScore.size() != Wind.values().length) {
                throw new IllegalArgumentException("final score requires four players");
            }
            int sum = 0;
            for (Wind seat : Wind.values()) {
                Integer score = finalScore.get(seat);
                if (score == null) throw new IllegalArgumentException("final score omitted " + seat);
                sum = Math.addExact(sum, score);
            }
            if (sum != 0) throw new IllegalArgumentException("final score must be zero-sum");
        }
    }
}
