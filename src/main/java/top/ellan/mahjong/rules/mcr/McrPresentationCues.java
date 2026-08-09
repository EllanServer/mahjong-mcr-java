package top.ellan.mahjong.rules.mcr;

import java.util.LinkedHashSet;
import java.util.List;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RulePresentationCue;
import top.ellan.mahjong.spi.RulePresentationCueType;

/** Maps typed MCR events to platform-neutral, non-persisted feedback. */
final class McrPresentationCues {
    private McrPresentationCues() {}

    static List<RulePresentationCue> from(
            McrProviderState next, List<McrMatchEvent> events) {
        LinkedHashSet<RulePresentationCue> cues = new LinkedHashSet<>();
        for (McrMatchEvent event : events) {
            if (event instanceof McrMatchEvent.HandStarted) {
                cues.add(broadcast(RulePresentationCueType.TILE_SHUFFLE));
            } else if (event instanceof McrMatchEvent.RoundAdvanced advanced) {
                addRoundEvents(next, advanced.events(), cues);
            }
        }
        return List.copyOf(cues);
    }

    private static void addRoundEvents(
            McrProviderState next,
            List<McrRoundEvent> events,
            LinkedHashSet<RulePresentationCue> cues) {
        for (McrRoundEvent event : events) {
            if (event instanceof McrRoundEvent.TileDrawn drawn) {
                cues.add(broadcast(RulePresentationCueType.TILE_DRAW));
                cues.add(RulePresentationCue.toPlayer(
                        RulePresentationCueType.TURN_CHANGE,
                        playerAt(next, drawn.seat())));
            } else if (event instanceof McrRoundEvent.TileDiscarded) {
                cues.add(broadcast(RulePresentationCueType.TILE_DISCARD));
            } else if (event instanceof McrRoundEvent.MeldFormed formed) {
                cues.add(broadcast(meldCue(formed.meld().origin())));
            } else if (event instanceof McrRoundEvent.RoundWon) {
                cues.add(broadcast(RulePresentationCueType.ROUND_WIN));
            } else if (event instanceof McrRoundEvent.ExhaustiveDraw) {
                cues.add(broadcast(RulePresentationCueType.ROUND_DRAW));
            }
        }
    }

    private static PlayerId playerAt(McrProviderState state, Wind logicalSeat) {
        return state.player(state.match().playerAt(logicalSeat));
    }

    private static RulePresentationCueType meldCue(McrMeldOrigin origin) {
        return switch (origin) {
            case CHOW -> RulePresentationCueType.REACTION_CHI;
            case PUNG -> RulePresentationCueType.REACTION_PON;
            case DIRECT_KONG, CONCEALED_KONG, ADDED_KONG ->
                    RulePresentationCueType.REACTION_KAN;
        };
    }

    private static RulePresentationCue broadcast(RulePresentationCueType type) {
        return RulePresentationCue.broadcast(type);
    }
}
