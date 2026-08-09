package top.ellan.mahjong.rules.mcr;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RuleAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/** Deterministic actor-owned actions for reaction and hand-boundary deadlines. */
final class McrScheduledActionPolicy {
    static final String CLOSE_REACTIONS_TYPE = "system.close_reactions";
    static final Duration REACTION_DELAY = Duration.ofSeconds(5);
    static final Duration NEXT_HAND_DELAY = Duration.ofSeconds(3);

    private static final RuleAction CLOSE_REACTIONS =
            new RuleAction(CLOSE_REACTIONS_TYPE, new byte[0]);
    private static final RuleAction START_NEXT_HAND =
            new RuleAction("start_next_hand", new byte[0]);

    private final McrLegalActionGenerator legalActions;

    McrScheduledActionPolicy(McrLegalActionGenerator legalActions) {
        this.legalActions = legalActions;
    }

    Optional<ScheduledRuleAction> next(McrProviderState state) {
        McrMatchState match = state.match();
        if (match.phase() == McrMatchPhase.HAND_ACTIVE
                && hasCloseReactionAction(match.roundState())) {
            return Optional.of(new ScheduledRuleAction(
                    reactionActor(state),
                    CLOSE_REACTIONS,
                    REACTION_DELAY,
                    "mcr.reaction_timeout"));
        }
        if (match.phase() == McrMatchPhase.BETWEEN_HANDS) {
            return Optional.of(new ScheduledRuleAction(
                    nextDealer(state),
                    START_NEXT_HAND,
                    NEXT_HAND_DELAY,
                    "mcr.next_hand"));
        }
        return Optional.empty();
    }

    boolean isActorOwned(RuleAction action) {
        return CLOSE_REACTIONS_TYPE.equals(action.type());
    }

    Optional<McrMatchAction> decodeActorOwned(
            McrProviderState state, PlayerId actor, RuleAction action) {
        if (!isActorOwned(action)
                || action.payload().length != 0
                || !reactionActor(state).equals(actor)
                || !hasCloseReactionAction(state.match().roundState())) {
            return Optional.empty();
        }
        return Optional.of(new McrMatchAction.Play(new McrRoundAction.CloseReactions()));
    }

    boolean mayStartNextHand(McrProviderState state, PlayerId actor) {
        return state.match().phase() == McrMatchPhase.BETWEEN_HANDS
                && nextDealer(state).equals(actor);
    }

    private boolean hasCloseReactionAction(McrRoundState state) {
        List<McrLegalAction> actions = legalActions.systemActions(state);
        return actions.size() == 1
                && actions.getFirst().action() instanceof McrRoundAction.CloseReactions;
    }

    private static PlayerId reactionActor(McrProviderState state) {
        Wind currentLogicalSeat = state.match().roundState().currentSeat();
        return state.player(state.match().playerAt(currentLogicalSeat));
    }

    private static PlayerId nextDealer(McrProviderState state) {
        int seat = state.match().handsCompleted() % Wind.values().length;
        return state.player(Wind.values()[seat]);
    }
}
