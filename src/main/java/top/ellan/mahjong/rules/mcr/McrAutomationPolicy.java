package top.ellan.mahjong.rules.mcr;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/**
 * Deterministic, ting-aware baseline play for MCR bots and trustees.
 *
 * <p>Aligned with the 1.5.0 bot contract: a win is always taken, a chow/pung/kong claim is taken
 * only when the resulting hand strictly beats passing, a turn kong is only declared when it creates
 * a ready hand where none existed, and the discard is the one that leaves the best ready state.</p>
 */
final class McrAutomationPolicy {
    private static final Duration WIN_DELAY = Duration.ofMillis(250);
    private static final Duration REACTION_DELAY = Duration.ofMillis(400);
    private static final Duration DISCARD_DELAY = Duration.ofMillis(850);

    private final McrBotDecisionService brain = new McrBotDecisionService();

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

    private LegalAction select(McrProviderState state, AutomatedPlayerActions candidate) {
        LegalAction win = keyed(candidate.legalActions(), "self_draw_win");
        if (win == null) {
            win = prefixed(candidate.legalActions(), "respond:hu");
        }
        if (win != null) {
            return win;
        }
        Seat seat = Seat.resolve(state, candidate.actor());
        if (seat == null) {
            return firstOf(candidate.legalActions(), "respond:pass");
        }
        try {
            LegalAction pass = keyed(candidate.legalActions(), "respond:pass");
            if (pass != null) {
                return claim(state, seat, candidate, pass);
            }
            LegalAction kong = turnKong(state, seat, candidate);
            return kong != null ? kong : discard(state, seat, candidate);
        } catch (RuntimeException evaluationFailure) {
            LegalAction pass = keyed(candidate.legalActions(), "respond:pass");
            return pass != null ? pass : discard(state, seat, candidate);
        }
    }

    /**
     * Chooses between passing and every offered chow/pung/direct-kong.
     *
     * <p>A claim wins only when its post-claim ready score strictly beats passing, exactly as the
     * 1.5.0 bot decided; ties keep the hand closed.</p>
     */
    private LegalAction claim(
            McrProviderState state,
            Seat seat,
            AutomatedPlayerActions candidate,
            LegalAction pass) {
        long passScore = brain.readyScore(
                seat.concealed(), seat.melds(), seat.seatWind(), seat.roundWind(), seat.flowers());
        LegalAction best = null;
        long bestScore = passScore;
        int bestTiebreak = Integer.MIN_VALUE;
        for (LegalAction action : candidate.legalActions()) {
            Claim claim = Claim.decode(state, seat, action);
            if (claim == null) {
                continue;
            }
            long score = claim.exactCount()
                    ? brain.readyScore(
                            claim.concealed(),
                            claim.melds(),
                            seat.seatWind(),
                            seat.roundWind(),
                            seat.flowers())
                    : brain.bestReadyScoreAfterDiscard(
                            claim.concealed(),
                            claim.melds(),
                            seat.seatWind(),
                            seat.roundWind(),
                            seat.flowers());
            int tiebreak = claim.tiebreak();
            if (score > bestScore || (score == bestScore && best != null && tiebreak > bestTiebreak)) {
                best = action;
                bestScore = score;
                bestTiebreak = tiebreak;
            }
        }
        return best != null && bestScore > passScore ? best : pass;
    }

    /**
     * Declares a concealed or added kong when it does not damage the hand.
     *
     * <p>1.5.0 only konged to *create* a ready hand, which in practice meant it abandoned the fan a
     * kong is worth and left a fourth copy rotting in hand for the whole round. The rule here keeps
     * that protection — a kong that breaks an existing wait still scores lower and is declined — but
     * takes the free fan whenever the hand is left no worse off.</p>
     */
    private LegalAction turnKong(
            McrProviderState state, Seat seat, AutomatedPlayerActions candidate) {
        long baseline = brain.bestReadyScoreAfterDiscard(
                seat.concealed(), seat.melds(), seat.seatWind(), seat.roundWind(), seat.flowers());
        LegalAction best = null;
        long bestScore = -1L;
        int bestPriority = Integer.MIN_VALUE;
        for (LegalAction action : candidate.legalActions()) {
            Claim kong = Claim.decodeTurnKong(state, seat, action);
            if (kong == null) {
                continue;
            }
            long score = kong.exactCount()
                    ? brain.readyScore(
                            kong.concealed(),
                            kong.melds(),
                            seat.seatWind(),
                            seat.roundWind(),
                            seat.flowers())
                    : brain.bestReadyScoreAfterDiscard(
                            kong.concealed(),
                            kong.melds(),
                            seat.seatWind(),
                            seat.roundWind(),
                            seat.flowers());
            if (score < baseline) {
                continue;
            }
            if (score > bestScore || (score == bestScore && kong.tiebreak() > bestPriority)) {
                best = action;
                bestScore = score;
                bestPriority = kong.tiebreak();
            }
        }
        return best;
    }

    /** Picks the discard that leaves the best ready state, then the most expendable shape. */
    private LegalAction discard(
            McrProviderState state, Seat seat, AutomatedPlayerActions candidate) {
        LegalAction selected = null;
        long selectedScore = -1L;
        int selectedPreference = Integer.MIN_VALUE;
        int selectedKind = Integer.MAX_VALUE;
        // Two copies of one kind reach the same shape, so each kind is scored at most once.
        long[] memo = new long[Tile.STANDARD_KIND_COUNT];
        java.util.Arrays.fill(memo, -1L);
        for (LegalAction action : candidate.legalActions()) {
            if (!action.action().type().equals("discard")) {
                continue;
            }
            McrTileInstance tile = seat.projectedTile(state, action);
            if (tile == null) {
                continue;
            }
            Tile kind = tile.kind();
            if (memo[kind.index()] < 0L) {
                memo[kind.index()] = brain.readyScore(
                        seat.concealed().minus(kind),
                        seat.melds(),
                        seat.seatWind(),
                        seat.roundWind(),
                        seat.flowers());
            }
            long score = memo[kind.index()];
            int preference = McrBotDecisionService.discardPreference(seat.concealed(), kind);
            if (score > selectedScore
                    || (score == selectedScore && preference > selectedPreference)
                    || (score == selectedScore
                            && preference == selectedPreference
                            && kind.index() < selectedKind)) {
                selected = action;
                selectedScore = score;
                selectedPreference = preference;
                selectedKind = kind.index();
            }
        }
        return selected;
    }

    private static Duration delay(LegalAction action) {
        if (action.key().equals("self_draw_win") || action.key().startsWith("respond:hu")) {
            return WIN_DELAY;
        }
        return action.key().startsWith("respond:") ? REACTION_DELAY : DISCARD_DELAY;
    }

    private static LegalAction firstOf(List<LegalAction> actions, String key) {
        LegalAction action = keyed(actions, key);
        return action != null ? action : (actions.isEmpty() ? null : actions.getFirst());
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

    /** One automated seat's authoritative view of its own hand. */
    private record Seat(
            Wind seatWind,
            Wind roundWind,
            List<McrTileInstance> hand,
            TileCounts concealed,
            List<Meld> melds,
            List<Tile> flowers) {

        static Seat resolve(McrProviderState state, top.ellan.mahjong.spi.PlayerId actor) {
            Wind initialSeat = state.initialSeat(actor);
            if (initialSeat == null) {
                return null;
            }
            Wind seatWind = state.match().logicalSeatOf(initialSeat);
            McrRoundState round = state.match().roundState();
            List<McrTileInstance> hand = round.hand(seatWind);
            TileCounts concealed = TileCounts.empty();
            for (McrTileInstance tile : hand) {
                concealed = concealed.plus(tile.kind());
            }
            ArrayList<Meld> melds = new ArrayList<>(4);
            for (McrPhysicalMeld meld : round.melds(seatWind)) {
                melds.add(meld.scoringMeld());
            }
            ArrayList<Tile> flowers = new ArrayList<>();
            for (McrTileInstance flower : round.flowers(seatWind)) {
                flowers.add(flower.kind());
            }
            return new Seat(
                    seatWind, round.roundWind(), hand, concealed, List.copyOf(melds), flowers);
        }

        McrTileInstance projectedTile(McrProviderState state, LegalAction action) {
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
    }

    /** A simulated post-claim hand. */
    private record Claim(
            TileCounts concealed, List<Meld> melds, boolean exactCount, int tiebreak) {

        /** Decodes a reaction-window chow/pung/direct-kong claim into its resulting hand. */
        static Claim decode(McrProviderState state, Seat seat, LegalAction action) {
            if (!action.action().type().equals("respond")) {
                return null;
            }
            byte[] payload = action.action().payload();
            if (payload.length < 2) {
                return null;
            }
            int typeOrdinal = Byte.toUnsignedInt(payload[0]);
            if (typeOrdinal >= McrReactionType.values().length) {
                return null;
            }
            McrReactionType type = McrReactionType.values()[typeOrdinal];
            if (type != McrReactionType.CHOW
                    && type != McrReactionType.PUNG
                    && type != McrReactionType.KONG) {
                return null;
            }
            Optional<McrReactionWindow> window = state.match().roundState().reactionWindow();
            if (window.isEmpty()) {
                return null;
            }
            Tile claimed = window.get().discard().kind();
            List<Tile> consumed = consumed(state, seat, payload);
            if (consumed == null) {
                return null;
            }
            TileCounts concealed = seat.concealed();
            for (Tile tile : consumed) {
                if (concealed.count(tile) == 0) {
                    return null;
                }
                concealed = concealed.minus(tile);
            }
            ArrayList<Meld> melds = new ArrayList<>(seat.melds());
            switch (type) {
                case CHOW -> melds.add(Meld.chow(chowMiddle(claimed, consumed)));
                case PUNG -> melds.add(Meld.pung(claimed));
                default -> melds.add(Meld.openKong(claimed));
            }
            return new Claim(
                    concealed,
                    List.copyOf(melds),
                    type == McrReactionType.KONG,
                    claimPriority(type));
        }

        /** Decodes an own-turn concealed or added kong into its resulting hand. */
        static Claim decodeTurnKong(McrProviderState state, Seat seat, LegalAction action) {
            String type = action.action().type();
            if (type.equals("concealed_kong")) {
                byte[] payload = action.action().payload();
                if (payload.length != 4) {
                    return null;
                }
                Tile kind = resolveKind(state, seat, Byte.toUnsignedInt(payload[0]));
                if (kind == null || seat.concealed().count(kind) < 4) {
                    return null;
                }
                TileCounts concealed = seat.concealed();
                for (int copy = 0; copy < 4; copy++) {
                    concealed = concealed.minus(kind);
                }
                ArrayList<Meld> melds = new ArrayList<>(seat.melds());
                melds.add(Meld.concealedKong(kind));
                return new Claim(concealed, List.copyOf(melds), true, 1);
            }
            if (!type.equals("added_kong")) {
                return null;
            }
            byte[] payload = action.action().payload();
            if (payload.length != 1) {
                return null;
            }
            Tile kind = resolveKind(state, seat, Byte.toUnsignedInt(payload[0]));
            if (kind == null || seat.concealed().count(kind) == 0) {
                return null;
            }
            ArrayList<Meld> melds = new ArrayList<>(seat.melds().size());
            boolean upgraded = false;
            for (Meld meld : seat.melds()) {
                if (!upgraded && meld.type() == MeldType.PUNG && meld.tile() == kind) {
                    melds.add(Meld.openKong(kind));
                    upgraded = true;
                } else {
                    melds.add(meld);
                }
            }
            if (!upgraded) {
                return null;
            }
            return new Claim(seat.concealed().minus(kind), List.copyOf(melds), false, 0);
        }

        private static List<Tile> consumed(
                McrProviderState state, Seat seat, byte[] payload) {
            int count = Byte.toUnsignedInt(payload[1]);
            if (count == 0 || payload.length != count + 2) {
                return null;
            }
            ArrayList<Tile> consumed = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                Tile kind = resolveKind(state, seat, Byte.toUnsignedInt(payload[2 + index]));
                if (kind == null) {
                    return null;
                }
                consumed.add(kind);
            }
            return consumed;
        }

        private static Tile resolveKind(McrProviderState state, Seat seat, int projection) {
            for (McrTileInstance tile : seat.hand()) {
                if (state.projectionIds().project(tile) == projection) {
                    return tile.kind();
                }
            }
            return null;
        }

        /** The middle tile of the completed run, which is what {@link Meld#chow} expects. */
        private static Tile chowMiddle(Tile claimed, List<Tile> consumed) {
            int low = claimed.index();
            int high = claimed.index();
            for (Tile tile : consumed) {
                low = Math.min(low, tile.index());
                high = Math.max(high, tile.index());
            }
            return Tile.standard((low + high) / 2);
        }

        /** Prefers the wider set when two claims reach an identical ready score. */
        private static int claimPriority(McrReactionType type) {
            return switch (type) {
                case KONG -> 2;
                case PUNG -> 1;
                default -> 0;
            };
        }
    }
}
