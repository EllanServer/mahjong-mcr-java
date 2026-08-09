package top.ellan.mahjong.rules.mcr;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/** Deterministic, allocation-bounded baseline play for MCR bots and trustees. */
final class McrAutomationPolicy {
    private static final Duration WIN_DELAY = Duration.ofMillis(250);
    private static final Duration REACTION_DELAY = Duration.ofMillis(400);
    private static final Duration DISCARD_DELAY = Duration.ofMillis(850);

    Optional<ScheduledRuleAction> next(
            McrProviderState state, List<AutomatedPlayerActions> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        for (AutomatedPlayerActions candidate : candidates) {
            LegalAction action = select(state, candidate);
            if (action != null) {
                return Optional.of(new ScheduledRuleAction(
                        candidate.actor(),
                        action.action(),
                        delay(action),
                        "automation.mcr"));
            }
        }
        return Optional.empty();
    }

    private static LegalAction select(
            McrProviderState state, AutomatedPlayerActions candidate) {
        LegalAction win = keyed(candidate.legalActions(), "self_draw_win");
        if (win == null) {
            win = prefixed(candidate.legalActions(), "respond:hu");
        }
        if (win != null) {
            return win;
        }
        LegalAction pass = keyed(candidate.legalActions(), "respond:pass");
        if (pass != null) {
            return pass;
        }
        return discard(state, candidate);
    }

    private static LegalAction discard(
            McrProviderState state, AutomatedPlayerActions candidate) {
        Wind initialSeat = state.initialSeat(candidate.actor());
        if (initialSeat == null) {
            return null;
        }
        Wind seat = state.match().logicalSeatOf(initialSeat);
        List<McrTileInstance> hand = state.match().roundState().hand(seat);
        LegalAction selected = null;
        int selectedValue = Integer.MAX_VALUE;
        int selectedKind = Integer.MAX_VALUE;
        for (LegalAction action : candidate.legalActions()) {
            if (!action.action().type().equals("discard")) {
                continue;
            }
            McrTileInstance tile = projectedTile(state, hand, action);
            if (tile == null) {
                continue;
            }
            int value = keepValue(tile.kind(), hand);
            int kind = tile.kind().ordinal();
            if (value < selectedValue || (value == selectedValue && kind < selectedKind)) {
                selected = action;
                selectedValue = value;
                selectedKind = kind;
            }
        }
        return selected;
    }

    private static McrTileInstance projectedTile(
            McrProviderState state, List<McrTileInstance> hand, LegalAction action) {
        byte[] payload = action.action().payload();
        if (payload.length != 1) {
            return null;
        }
        int projection = Byte.toUnsignedInt(payload[0]);
        for (McrTileInstance tile : hand) {
            if (state.projectionIds().project(tile) == projection) {
                return tile;
            }
        }
        return null;
    }

    private static int keepValue(Tile tile, List<McrTileInstance> hand) {
        int value = tile.isFlower() ? -100 : tile.isHonor() ? -1 : 0;
        for (McrTileInstance other : hand) {
            Tile kind = other.kind();
            if (kind == tile) {
                value += 10;
            } else if (tile.isNumbered() && kind.suit() == tile.suit()) {
                int distance = Math.abs(kind.rank() - tile.rank());
                value += distance == 1 ? 5 : distance == 2 ? 2 : 0;
            }
        }
        return value;
    }

    private static Duration delay(LegalAction action) {
        if (action.key().equals("self_draw_win") || action.key().startsWith("respond:hu")) {
            return WIN_DELAY;
        }
        return action.key().equals("respond:pass") ? REACTION_DELAY : DISCARD_DELAY;
    }

    private static LegalAction keyed(List<LegalAction> actions, String key) {
        for (LegalAction action : actions) {
            if (action.key().equals(key)) {
                return action;
            }
        }
        return null;
    }

    private static LegalAction prefixed(List<LegalAction> actions, String prefix) {
        for (LegalAction action : actions) {
            if (action.key().startsWith(prefix)) {
                return action;
            }
        }
        return null;
    }
}
