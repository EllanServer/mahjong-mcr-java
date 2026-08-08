package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable exact physical state of one MCR hand. Every tile identity is present
 * exactly once across hands, flowers, melds, rivers and wall.
 */
public final class McrRoundState {
    private final long revision;
    private final Wind roundWind;
    private final Wind currentSeat;
    private final McrRoundPhase phase;
    private final Map<Wind, List<McrTileInstance>> hands;
    private final Map<Wind, List<McrTileInstance>> flowers;
    private final Map<Wind, List<McrPhysicalMeld>> melds;
    private final Map<Wind, List<McrPhysicalDiscard>> rivers;
    private final McrWall wall;
    private final McrTileInstance lastDraw;
    private final McrDrawSource lastDrawSource;
    private final McrReactionWindow reactionWindow;
    private final McrRoundOutcome outcome;

    McrRoundState(
            long revision,
            Wind roundWind,
            Wind currentSeat,
            McrRoundPhase phase,
            Map<Wind, List<McrTileInstance>> hands,
            Map<Wind, List<McrTileInstance>> flowers,
            Map<Wind, List<McrPhysicalMeld>> melds,
            Map<Wind, List<McrPhysicalDiscard>> rivers,
            McrWall wall,
            McrTileInstance lastDraw,
            McrDrawSource lastDrawSource,
            McrReactionWindow reactionWindow,
            McrRoundOutcome outcome) {
        if (revision < 0 || roundWind == null || currentSeat == null || phase == null || wall == null) {
            throw new IllegalArgumentException("revision, winds, phase and wall are required");
        }
        this.revision = revision;
        this.roundWind = roundWind;
        this.currentSeat = currentSeat;
        this.phase = phase;
        this.hands = copySeats(hands, "hands");
        this.flowers = copySeats(flowers, "flowers");
        this.melds = copySeats(melds, "melds");
        this.rivers = copySeats(rivers, "rivers");
        this.wall = wall;
        this.lastDraw = lastDraw;
        this.lastDrawSource = lastDrawSource;
        this.reactionWindow = reactionWindow;
        this.outcome = outcome;
        validate();
    }

    public static McrRoundState start(long seed, Wind roundWind) {
        return start(McrInitialDealer.deal(seed), roundWind);
    }

    public static McrRoundState start(McrInitialDeal deal, Wind roundWind) {
        if (deal == null || roundWind == null) {
            throw new IllegalArgumentException("initial deal and round wind are required");
        }
        EnumMap<Wind, List<McrPhysicalMeld>> melds = emptySeatLists();
        EnumMap<Wind, List<McrPhysicalDiscard>> rivers = emptySeatLists();
        return new McrRoundState(
                0,
                roundWind,
                Wind.EAST,
                McrRoundPhase.AWAITING_DISCARD,
                deal.concealed(),
                deal.flowers(),
                melds,
                rivers,
                deal.wall(),
                deal.dealerLastTile(),
                deal.dealerLastTileSource(),
                null,
                null);
    }

    public long revision() {
        return revision;
    }

    public Wind roundWind() {
        return roundWind;
    }

    public Wind currentSeat() {
        return currentSeat;
    }

    public McrRoundPhase phase() {
        return phase;
    }

    public List<McrTileInstance> hand(Wind seat) {
        return requiredSeat(hands, seat);
    }

    public List<McrTileInstance> flowers(Wind seat) {
        return requiredSeat(flowers, seat);
    }

    public List<McrPhysicalMeld> melds(Wind seat) {
        return requiredSeat(melds, seat);
    }

    public List<McrPhysicalDiscard> river(Wind seat) {
        return requiredSeat(rivers, seat);
    }

    public McrWall wall() {
        return wall;
    }

    public Optional<McrTileInstance> lastDraw() {
        return Optional.ofNullable(lastDraw);
    }

    public Optional<McrDrawSource> lastDrawSource() {
        return Optional.ofNullable(lastDrawSource);
    }

    public Optional<McrReactionWindow> reactionWindow() {
        return Optional.ofNullable(reactionWindow);
    }

    public Optional<McrRoundOutcome> outcome() {
        return Optional.ofNullable(outcome);
    }

    Map<Wind, List<McrTileInstance>> rawHands() {
        return hands;
    }

    Map<Wind, List<McrTileInstance>> rawFlowers() {
        return flowers;
    }

    Map<Wind, List<McrPhysicalMeld>> rawMelds() {
        return melds;
    }

    Map<Wind, List<McrPhysicalDiscard>> rawRivers() {
        return rivers;
    }

    McrTileInstance rawLastDraw() {
        return lastDraw;
    }

    McrDrawSource rawLastDrawSource() {
        return lastDrawSource;
    }

    private void validate() {
        if ((phase == McrRoundPhase.REACTIONS) != (reactionWindow != null)) {
            throw new IllegalArgumentException("reaction phase and reaction window must agree");
        }
        if ((phase == McrRoundPhase.ENDED) != (outcome != null)) {
            throw new IllegalArgumentException("ended phase and outcome must agree");
        }
        if (phase != McrRoundPhase.ENDED) validateLiveStructuralCounts();

        boolean[] placed = new boolean[McrTileInstance.PHYSICAL_TILE_COUNT];
        McrPhysicalDiscard pending = null;
        for (Wind owner : Wind.values()) {
            for (McrTileInstance tile : hand(owner)) {
                if (tile.kind().isFlower()) throw new IllegalArgumentException("flower remained in a hand");
                mark(placed, tile, "hand");
            }
            for (McrTileInstance tile : flowers(owner)) {
                if (!tile.kind().isFlower()) throw new IllegalArgumentException("standard tile in flower area");
                mark(placed, tile, "flower area");
            }
            for (McrPhysicalMeld meld : melds(owner)) {
                if (meld.sourceSeat() == owner) {
                    throw new IllegalArgumentException("an open meld cannot claim its owner's discard");
                }
                for (McrTileInstance tile : meld.tiles()) mark(placed, tile, "meld");
            }
            for (McrPhysicalDiscard discard : river(owner)) {
                if (discard.sourceSeat() != owner) {
                    throw new IllegalArgumentException("discard stored in the wrong river");
                }
                if (discard.status() == McrDiscardStatus.PENDING) {
                    if (pending != null) throw new IllegalArgumentException("more than one discard is pending");
                    pending = discard;
                }
                if (discard.status() != McrDiscardStatus.MELD_CLAIMED) {
                    mark(placed, discard.tile(), "river");
                } else {
                    validateClaimedDiscardPlacement(discard);
                }
            }
        }
        for (int i = 0; i < wall.remaining(); i++) mark(placed, wall.peekAt(i), "wall");
        for (int id = 0; id < placed.length; id++) {
            if (!placed[id]) throw new IllegalArgumentException("physical tile is not placed: " + id);
        }

        if (reactionWindow != null) {
            if (pending == null || pending.sourceSeat() != reactionWindow.discarder()
                    || !pending.tile().equals(reactionWindow.discard())) {
                throw new IllegalArgumentException("reaction window does not match pending river tile");
            }
            for (Wind seat : Wind.values()) {
                if (seat == reactionWindow.discarder()) continue;
                for (McrReaction option : reactionWindow.legalOptions(seat)) {
                    if (!hand(seat).containsAll(option.concealedTiles())) {
                        throw new IllegalArgumentException("reaction option consumes a tile absent from its hand");
                    }
                }
            }
        } else if (pending != null) {
            throw new IllegalArgumentException("pending discard requires a reaction window");
        }
        if ((lastDraw == null) != (lastDrawSource == null)) {
            throw new IllegalArgumentException("last draw and its source must be present together");
        }
        if (lastDraw != null && !hand(currentSeat).contains(lastDraw)) {
            throw new IllegalArgumentException("last draw must remain in the current hand");
        }
    }

    private void validateLiveStructuralCounts() {
        for (Wind seat : Wind.values()) {
            int structural = hand(seat).size() + 3 * melds(seat).size();
            int expected = phase == McrRoundPhase.AWAITING_DISCARD && seat == currentSeat ? 14 : 13;
            if (structural != expected) {
                throw new IllegalArgumentException(
                        seat + " has structural tile count " + structural + ", expected " + expected);
            }
        }
    }

    private void validateClaimedDiscardPlacement(McrPhysicalDiscard discard) {
        int matches = 0;
        for (McrPhysicalMeld meld : melds(discard.claimant())) {
            if (discard.tile().equals(meld.claimedDiscard())
                    && discard.sourceSeat() == meld.sourceSeat()) matches++;
        }
        if (matches != 1) {
            throw new IllegalArgumentException("meld-claimed river tile must occur in exactly one claimant meld");
        }
    }

    private static void mark(boolean[] placed, McrTileInstance tile, String area) {
        if (placed[tile.id()]) {
            throw new IllegalArgumentException("physical tile appears twice at " + area + ": " + tile.id());
        }
        placed[tile.id()] = true;
    }

    private static <T> Map<Wind, List<T>> copySeats(Map<Wind, List<T>> source, String name) {
        if (source == null) throw new IllegalArgumentException(name + " map is required");
        EnumMap<Wind, List<T>> result = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) {
            List<T> values = source.get(seat);
            if (values == null) throw new IllegalArgumentException(name + " missing seat " + seat);
            result.put(seat, List.copyOf(values));
        }
        if (source.size() != Wind.values().length) {
            throw new IllegalArgumentException(name + " may contain only the four seats");
        }
        return Collections.unmodifiableMap(result);
    }

    private static <T> EnumMap<Wind, List<T>> emptySeatLists() {
        EnumMap<Wind, List<T>> result = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) result.put(seat, List.of());
        return result;
    }

    private static <T> List<T> requiredSeat(Map<Wind, List<T>> source, Wind seat) {
        if (seat == null) throw new IllegalArgumentException("seat is required");
        return source.get(seat);
    }
}
