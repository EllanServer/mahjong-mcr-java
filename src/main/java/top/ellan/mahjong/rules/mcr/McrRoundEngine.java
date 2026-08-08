package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Stateless pure-function engine for exact discard, reaction arbitration, meld
 * formation, replacement draws, flower replacement and discard wins.
 */
public final class McrRoundEngine {
    private final McrRulesEngine scorer;

    public McrRoundEngine() {
        this(new StandardMcrRulesEngine());
    }

    public McrRoundEngine(McrRulesEngine scorer) {
        if (scorer == null) throw new IllegalArgumentException("scorer is required");
        this.scorer = scorer;
    }

    public McrRoundTransition transition(McrRoundState state, McrRoundAction action) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (action == null) return McrRoundTransition.rejected(state, McrRoundViolation.NULL_INPUT);
        if (state.phase() == McrRoundPhase.ENDED) {
            return McrRoundTransition.rejected(state, McrRoundViolation.ROUND_ENDED);
        }
        try {
            if (action instanceof McrRoundAction.Discard discard) return discard(state, discard);
            if (action instanceof McrRoundAction.React react) return react(state, react.reaction());
            if (action instanceof McrRoundAction.CloseReactions) return closeReactions(state);
            return McrRoundTransition.rejected(state, McrRoundViolation.WRONG_PHASE);
        } catch (IllegalArgumentException | IllegalStateException mismatch) {
            return McrRoundTransition.rejected(state, McrRoundViolation.INTERNAL_STATE_MISMATCH);
        }
    }

    private McrRoundTransition discard(McrRoundState state, McrRoundAction.Discard action) {
        if (state.phase() != McrRoundPhase.AWAITING_DISCARD) {
            return McrRoundTransition.rejected(state, McrRoundViolation.WRONG_PHASE);
        }
        if (action.seat() != state.currentSeat()) {
            return McrRoundTransition.rejected(state, McrRoundViolation.NOT_CURRENT_SEAT);
        }
        if (!state.hand(action.seat()).contains(action.tile())) {
            return McrRoundTransition.rejected(state, McrRoundViolation.TILE_NOT_OWNED);
        }

        EnumMap<Wind, List<McrTileInstance>> hands = mutableSeats(state.rawHands());
        hands.put(action.seat(), removeExact(hands.get(action.seat()), action.tile()));
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = mutableSeats(state.rawRivers());
        ArrayList<McrPhysicalDiscard> river = new ArrayList<>(rivers.get(action.seat()));
        river.add(McrPhysicalDiscard.pending(action.seat(), action.tile()));
        rivers.put(action.seat(), List.copyOf(river));

        List<McrReaction> options = generateReactions(state, hands, action.seat(), action.tile());
        McrReactionWindow window = McrReactionWindow.open(action.seat(), action.tile(), options);
        for (Wind seat : Wind.values()) {
            if (seat != action.seat() && window.legalOptions(seat).isEmpty()) window = window.pass(seat);
        }

        ArrayList<McrRoundEvent> events = new ArrayList<>();
        events.add(new McrRoundEvent.TileDiscarded(action.seat(), action.tile()));
        McrRoundState reacting = new McrRoundState(
                state.revision() + 1,
                state.roundWind(),
                action.seat(),
                McrRoundPhase.REACTIONS,
                hands,
                state.rawFlowers(),
                state.rawMelds(),
                rivers,
                state.wall(),
                null,
                null,
                window,
                null);
        if (!window.isClosed()) return McrRoundTransition.accepted(reacting, events);
        return McrRoundTransition.accepted(resolve(reacting, events), events);
    }

    private McrRoundTransition react(McrRoundState state, McrReaction reaction) {
        if (state.phase() != McrRoundPhase.REACTIONS) {
            return McrRoundTransition.rejected(state, McrRoundViolation.WRONG_PHASE);
        }
        McrReactionWindow window = state.reactionWindow().orElseThrow();
        if (reaction.claimant() == window.discarder()) {
            return McrRoundTransition.rejected(state, McrRoundViolation.REACTION_NOT_LEGAL);
        }
        if (window.decisions().containsKey(reaction.claimant())) {
            return McrRoundTransition.rejected(state, McrRoundViolation.REACTION_ALREADY_RECORDED);
        }
        if (!reaction.discard().equals(window.discard())
                || reaction.type() != McrReactionType.PASS
                && !window.legalOptions(reaction.claimant()).contains(reaction)) {
            return McrRoundTransition.rejected(state, McrRoundViolation.REACTION_NOT_LEGAL);
        }

        McrReactionWindow updated = window.respond(reaction);
        ArrayList<McrRoundEvent> events = new ArrayList<>();
        events.add(new McrRoundEvent.ReactionRecorded(reaction.claimant(), reaction.type()));
        McrRoundState collecting = copyWithWindow(state, updated);
        if (!updated.isClosed()) return McrRoundTransition.accepted(collecting, events);
        return McrRoundTransition.accepted(resolve(collecting, events), events);
    }

    private McrRoundTransition closeReactions(McrRoundState state) {
        if (state.phase() != McrRoundPhase.REACTIONS) {
            return McrRoundTransition.rejected(state, McrRoundViolation.WRONG_PHASE);
        }
        McrReactionWindow closed = state.reactionWindow().orElseThrow().closeWithPasses();
        ArrayList<McrRoundEvent> events = new ArrayList<>();
        McrRoundState collecting = copyWithWindow(state, closed);
        return McrRoundTransition.accepted(resolve(collecting, events), events);
    }

    private McrRoundState copyWithWindow(McrRoundState state, McrReactionWindow window) {
        return new McrRoundState(
                state.revision() + 1,
                state.roundWind(),
                state.currentSeat(),
                McrRoundPhase.REACTIONS,
                state.rawHands(),
                state.rawFlowers(),
                state.rawMelds(),
                state.rawRivers(),
                state.wall(),
                state.rawLastDraw(),
                state.rawLastDrawSource(),
                window,
                null);
    }

    private McrRoundState resolve(McrRoundState state, ArrayList<McrRoundEvent> events) {
        McrReactionResolution resolution = state.reactionWindow().orElseThrow().resolution();
        if (resolution instanceof McrReactionResolution.NoClaim noClaim) {
            return resolveUnclaimed(state, noClaim.nextDrawer(), events);
        }
        if (resolution instanceof McrReactionResolution.MeldClaim meldClaim) {
            return resolveMeld(state, meldClaim.reaction(), events);
        }
        McrReactionResolution.Win win = (McrReactionResolution.Win) resolution;
        return resolveWin(state, win.reaction(), events);
    }

    private McrRoundState resolveUnclaimed(
            McrRoundState state, Wind nextDrawer, ArrayList<McrRoundEvent> events) {
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = resolvePendingRiver(
                state, McrDiscardStatus.UNCLAIMED, null);
        events.add(new McrRoundEvent.DiscardUnclaimed(
                state.currentSeat(), state.reactionWindow().orElseThrow().discard()));
        if (state.wall().isEmpty()) {
            events.add(new McrRoundEvent.ExhaustiveDraw());
            return terminalDraw(state, rivers);
        }

        DrawResult draw = drawStandard(
                nextDrawer,
                state.rawHands(),
                state.rawFlowers(),
                state.wall(),
                McrDrawSource.NORMAL,
                events);
        if (draw.standardTile == null) {
            events.add(new McrRoundEvent.ExhaustiveDraw());
            return terminalDraw(state, draw.hands, draw.flowers, rivers, draw.wall, nextDrawer);
        }
        return new McrRoundState(
                state.revision(),
                state.roundWind(),
                nextDrawer,
                McrRoundPhase.AWAITING_DISCARD,
                draw.hands,
                draw.flowers,
                state.rawMelds(),
                rivers,
                draw.wall,
                draw.standardTile,
                draw.source,
                null,
                null);
    }

    private McrRoundState resolveMeld(
            McrRoundState state, McrReaction reaction, ArrayList<McrRoundEvent> events) {
        Wind claimant = reaction.claimant();
        EnumMap<Wind, List<McrTileInstance>> hands = mutableSeats(state.rawHands());
        List<McrTileInstance> hand = hands.get(claimant);
        for (McrTileInstance tile : reaction.concealedTiles()) hand = removeExact(hand, tile);
        hands.put(claimant, hand);

        ArrayList<McrTileInstance> physicalTiles = new ArrayList<>(reaction.concealedTiles());
        physicalTiles.add(reaction.discard());
        McrPhysicalMeld meld = switch (reaction.type()) {
            case CHOW -> McrPhysicalMeld.chow(physicalTiles, reaction.discard(), state.currentSeat());
            case PUNG -> McrPhysicalMeld.pung(physicalTiles, reaction.discard(), state.currentSeat());
            case KONG -> McrPhysicalMeld.directKong(
                    physicalTiles, reaction.discard(), state.currentSeat());
            default -> throw new IllegalStateException("resolution did not contain a meld claim");
        };
        EnumMap<Wind, List<McrPhysicalMeld>> melds = mutableSeats(state.rawMelds());
        ArrayList<McrPhysicalMeld> seatMelds = new ArrayList<>(melds.get(claimant));
        seatMelds.add(meld);
        melds.put(claimant, List.copyOf(seatMelds));
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = resolvePendingRiver(
                state, McrDiscardStatus.MELD_CLAIMED, claimant);
        events.add(new McrRoundEvent.MeldFormed(claimant, meld));

        if (reaction.type() != McrReactionType.KONG) {
            return new McrRoundState(
                    state.revision(),
                    state.roundWind(),
                    claimant,
                    McrRoundPhase.AWAITING_DISCARD,
                    hands,
                    state.rawFlowers(),
                    melds,
                    rivers,
                    state.wall(),
                    null,
                    null,
                    null,
                    null);
        }

        DrawResult draw = drawStandard(
                claimant,
                hands,
                state.rawFlowers(),
                state.wall(),
                McrDrawSource.KONG_REPLACEMENT,
                events);
        if (draw.standardTile == null) {
            events.add(new McrRoundEvent.ExhaustiveDraw());
            return terminalDraw(state, draw.hands, draw.flowers, rivers, draw.wall, claimant, melds);
        }
        return new McrRoundState(
                state.revision(),
                state.roundWind(),
                claimant,
                McrRoundPhase.AWAITING_DISCARD,
                draw.hands,
                draw.flowers,
                melds,
                rivers,
                draw.wall,
                draw.standardTile,
                draw.source,
                null,
                null);
    }

    private McrRoundState resolveWin(
            McrRoundState state, McrReaction reaction, ArrayList<McrRoundEvent> events) {
        WinEvaluation evaluation = evaluateDiscardWin(
                state, state.rawHands(), reaction.claimant(), reaction.discard());
        if (!evaluation.legalWin()) throw new IllegalStateException("issued hu option no longer scores");
        Payment payment = McrPayments.settle(evaluation, reaction.claimant(), state.currentSeat());
        McrRoundOutcome.Win outcome = new McrRoundOutcome.Win(
                reaction.claimant(), state.currentSeat(), evaluation, payment);
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = resolvePendingRiver(
                state, McrDiscardStatus.WIN_CLAIMED, reaction.claimant());
        events.add(new McrRoundEvent.RoundWon(outcome));
        return new McrRoundState(
                state.revision(),
                state.roundWind(),
                reaction.claimant(),
                McrRoundPhase.ENDED,
                state.rawHands(),
                state.rawFlowers(),
                state.rawMelds(),
                rivers,
                state.wall(),
                null,
                null,
                null,
                outcome);
    }

    private List<McrReaction> generateReactions(
            McrRoundState state,
            Map<Wind, List<McrTileInstance>> hands,
            Wind discarder,
            McrTileInstance discard) {
        ArrayList<McrReaction> result = new ArrayList<>();
        for (Wind seat : Wind.values()) {
            if (seat == discarder) continue;
            WinEvaluation evaluation = evaluateDiscardWin(state, hands, seat, discard);
            if (evaluation.legalWin()) result.add(McrReaction.hu(seat, discard));
            if (state.wall().isEmpty()) continue;

            ArrayList<McrTileInstance> matching = matching(hands.get(seat), discard.kind());
            for (int first = 0; first < matching.size(); first++) {
                for (int second = first + 1; second < matching.size(); second++) {
                    result.add(McrReaction.pung(
                            seat, discard, matching.get(first), matching.get(second)));
                }
            }
            if (matching.size() == 3) {
                result.add(McrReaction.kong(
                        seat, discard, matching.get(0), matching.get(1), matching.get(2)));
            }
            if (seat == McrReactionWindow.nextSeat(discarder)) {
                addChows(result, seat, discard, hands.get(seat));
            }
        }
        return List.copyOf(result);
    }

    private WinEvaluation evaluateDiscardWin(
            McrRoundState state,
            Map<Wind, List<McrTileInstance>> hands,
            Wind winner,
            McrTileInstance discard) {
        ArrayList<Tile> concealed = new ArrayList<>(hands.get(winner).size());
        for (McrTileInstance tile : hands.get(winner)) concealed.add(tile.kind());
        ArrayList<Meld> melds = new ArrayList<>(state.melds(winner).size());
        for (McrPhysicalMeld meld : state.melds(winner)) melds.add(meld.scoringMeld());
        EnumSet<WinFlag> flags = EnumSet.noneOf(WinFlag.class);
        if (state.wall().isEmpty()) flags.add(WinFlag.LAST_TILE);
        if (isPublicLastOfKind(state, discard)) flags.add(WinFlag.LAST_OF_KIND);
        ArrayList<Tile> flowers = new ArrayList<>(state.flowers(winner).size());
        for (McrTileInstance flower : state.flowers(winner)) flowers.add(flower.kind());
        return scorer.evaluate(new WinInput(
                TileCounts.of(concealed),
                melds,
                discard.kind(),
                new WinContext(
                        winner,
                        state.roundWind(),
                        WinMethod.DISCARD,
                        flags,
                        flowers)));
    }

    private static boolean isPublicLastOfKind(McrRoundState state, McrTileInstance winningTile) {
        int visibleOtherCopies = 0;
        for (Wind seat : Wind.values()) {
            for (McrPhysicalDiscard discard : state.river(seat)) {
                if (!discard.tile().equals(winningTile)
                        && discard.tile().kind() == winningTile.kind()
                        && discard.status() != McrDiscardStatus.MELD_CLAIMED) {
                    visibleOtherCopies++;
                }
            }
            for (McrPhysicalMeld meld : state.melds(seat)) {
                if (meld.origin() == McrMeldOrigin.CONCEALED_KONG) continue;
                for (McrTileInstance tile : meld.tiles()) {
                    if (!tile.equals(winningTile) && tile.kind() == winningTile.kind()) {
                        visibleOtherCopies++;
                    }
                }
            }
        }
        return visibleOtherCopies == 3;
    }

    private static void addChows(
            List<McrReaction> result,
            Wind claimant,
            McrTileInstance discard,
            List<McrTileInstance> hand) {
        Tile kind = discard.kind();
        if (!kind.isNumbered()) return;
        for (int start = kind.rank() - 2; start <= kind.rank(); start++) {
            if (start < 1 || start > 7) continue;
            int firstRank = start == kind.rank() ? start + 1 : start;
            int secondRank = start + 2 == kind.rank() ? start + 1 : start + 2;
            if (start + 1 == kind.rank()) {
                firstRank = start;
                secondRank = start + 2;
            }
            Tile firstKind = numbered(kind.suit(), firstRank);
            Tile secondKind = numbered(kind.suit(), secondRank);
            ArrayList<McrTileInstance> firstTiles = matching(hand, firstKind);
            ArrayList<McrTileInstance> secondTiles = matching(hand, secondKind);
            for (McrTileInstance first : firstTiles) {
                for (McrTileInstance second : secondTiles) {
                    result.add(McrReaction.chow(claimant, discard, first, second));
                }
            }
        }
    }

    private static Tile numbered(Suit suit, int rank) {
        int base = switch (suit) {
            case CHARACTERS -> 0;
            case BAMBOO -> 9;
            case DOTS -> 18;
            default -> throw new IllegalArgumentException("numbered suit required");
        };
        return Tile.standard(base + rank - 1);
    }

    private static ArrayList<McrTileInstance> matching(
            List<McrTileInstance> hand, Tile kind) {
        ArrayList<McrTileInstance> result = new ArrayList<>(4);
        for (McrTileInstance tile : hand) if (tile.kind() == kind) result.add(tile);
        return result;
    }

    private static DrawResult drawStandard(
            Wind seat,
            Map<Wind, List<McrTileInstance>> sourceHands,
            Map<Wind, List<McrTileInstance>> sourceFlowers,
            McrWall sourceWall,
            McrDrawSource initialSource,
            List<McrRoundEvent> events) {
        EnumMap<Wind, List<McrTileInstance>> hands = mutableSeats(sourceHands);
        EnumMap<Wind, List<McrTileInstance>> flowers = mutableSeats(sourceFlowers);
        McrWall wall = sourceWall;
        McrDrawSource source = initialSource;
        while (!wall.isEmpty()) {
            McrWall.Draw draw = source == McrDrawSource.NORMAL
                    ? wall.drawFront()
                    : wall.drawBack();
            wall = draw.remainingWall();
            events.add(new McrRoundEvent.TileDrawn(seat, draw.tile(), source));
            if (!draw.tile().kind().isFlower()) {
                hands.put(seat, addSorted(hands.get(seat), draw.tile()));
                return new DrawResult(hands, flowers, wall, draw.tile(), source);
            }
            flowers.put(seat, addSorted(flowers.get(seat), draw.tile()));
            events.add(new McrRoundEvent.FlowerExposed(seat, draw.tile()));
            source = McrDrawSource.FLOWER_REPLACEMENT;
        }
        return new DrawResult(hands, flowers, wall, null, null);
    }

    private static EnumMap<Wind, List<McrPhysicalDiscard>> resolvePendingRiver(
            McrRoundState state, McrDiscardStatus status, Wind claimant) {
        Wind discarder = state.reactionWindow().orElseThrow().discarder();
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = mutableSeats(state.rawRivers());
        ArrayList<McrPhysicalDiscard> river = new ArrayList<>(rivers.get(discarder));
        int index = river.size() - 1;
        if (index < 0 || river.get(index).status() != McrDiscardStatus.PENDING) {
            throw new IllegalStateException("pending discard is not the latest river tile");
        }
        McrPhysicalDiscard pending = river.get(index);
        McrPhysicalDiscard resolved = switch (status) {
            case UNCLAIMED -> pending.unclaimed();
            case MELD_CLAIMED -> pending.claimedForMeld(claimant);
            case WIN_CLAIMED -> pending.claimedForWin(claimant);
            case PENDING -> throw new IllegalArgumentException("pending is not a resolution");
        };
        river.set(index, resolved);
        rivers.put(discarder, List.copyOf(river));
        return rivers;
    }

    private static McrRoundState terminalDraw(
            McrRoundState state, Map<Wind, List<McrPhysicalDiscard>> rivers) {
        return terminalDraw(
                state,
                state.rawHands(),
                state.rawFlowers(),
                rivers,
                state.wall(),
                state.currentSeat());
    }

    private static McrRoundState terminalDraw(
            McrRoundState state,
            Map<Wind, List<McrTileInstance>> hands,
            Map<Wind, List<McrTileInstance>> flowers,
            Map<Wind, List<McrPhysicalDiscard>> rivers,
            McrWall wall,
            Wind currentSeat) {
        return terminalDraw(state, hands, flowers, rivers, wall, currentSeat, state.rawMelds());
    }

    private static McrRoundState terminalDraw(
            McrRoundState state,
            Map<Wind, List<McrTileInstance>> hands,
            Map<Wind, List<McrTileInstance>> flowers,
            Map<Wind, List<McrPhysicalDiscard>> rivers,
            McrWall wall,
            Wind currentSeat,
            Map<Wind, List<McrPhysicalMeld>> melds) {
        return new McrRoundState(
                state.revision(),
                state.roundWind(),
                currentSeat,
                McrRoundPhase.ENDED,
                hands,
                flowers,
                melds,
                rivers,
                wall,
                null,
                null,
                null,
                new McrRoundOutcome.ExhaustiveDraw());
    }

    private static <T> EnumMap<Wind, List<T>> mutableSeats(Map<Wind, List<T>> source) {
        EnumMap<Wind, List<T>> result = new EnumMap<>(Wind.class);
        result.putAll(source);
        return result;
    }

    private static List<McrTileInstance> removeExact(
            List<McrTileInstance> source, McrTileInstance tile) {
        ArrayList<McrTileInstance> result = new ArrayList<>(source);
        if (!result.remove(tile)) throw new IllegalStateException("physical tile is not owned");
        return List.copyOf(result);
    }

    private static List<McrTileInstance> addSorted(
            List<McrTileInstance> source, McrTileInstance tile) {
        ArrayList<McrTileInstance> result = new ArrayList<>(source);
        result.add(tile);
        Collections.sort(result);
        return List.copyOf(result);
    }

    private record DrawResult(
            Map<Wind, List<McrTileInstance>> hands,
            Map<Wind, List<McrTileInstance>> flowers,
            McrWall wall,
            McrTileInstance standardTile,
            McrDrawSource source) {}
}
