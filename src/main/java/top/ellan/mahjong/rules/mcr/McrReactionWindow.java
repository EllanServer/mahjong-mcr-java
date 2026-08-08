package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable collection and arbitration of reactions to one discard.
 *
 * <p>The round engine supplies the legal non-pass alternatives. Network input
 * can only select one of those exact physical alternatives or pass, preventing
 * a caller from manufacturing a hu or substituting different concealed tiles.
 */
public final class McrReactionWindow {
    private final Wind discarder;
    private final McrTileInstance discard;
    private final Map<Wind, List<McrReaction>> legalOptions;
    private final Map<Wind, McrReaction> decisions;

    private McrReactionWindow(
            Wind discarder,
            McrTileInstance discard,
            Map<Wind, List<McrReaction>> legalOptions,
            Map<Wind, McrReaction> decisions) {
        this.discarder = discarder;
        this.discard = discard;
        this.legalOptions = legalOptions;
        this.decisions = decisions;
    }

    public static McrReactionWindow open(
            Wind discarder,
            McrTileInstance discard,
            List<McrReaction> legalOptions) {
        if (discarder == null || discard == null || discard.kind().isFlower()) {
            throw new IllegalArgumentException("discarder and standard discard are required");
        }
        List<McrReaction> source = legalOptions == null ? List.of() : List.copyOf(legalOptions);
        EnumMap<Wind, ArrayList<McrReaction>> mutable = new EnumMap<>(Wind.class);
        for (Wind seat : Wind.values()) {
            if (seat != discarder) mutable.put(seat, new ArrayList<>());
        }
        LinkedHashSet<McrReaction> unique = new LinkedHashSet<>();
        for (McrReaction reaction : source) {
            if (reaction == null || reaction.claimant() == discarder
                    || !reaction.discard().equals(discard)
                    || reaction.type() == McrReactionType.PASS) {
                throw new IllegalArgumentException("invalid legal reaction option for this discard");
            }
            if (reaction.type() == McrReactionType.CHOW
                    && reaction.claimant() != nextSeat(discarder)) {
                throw new IllegalArgumentException("only the next player may chow a discard");
            }
            if (!unique.add(reaction)) {
                throw new IllegalArgumentException("duplicate legal reaction option");
            }
            mutable.get(reaction.claimant()).add(reaction);
        }

        EnumMap<Wind, List<McrReaction>> immutableOptions = new EnumMap<>(Wind.class);
        for (Map.Entry<Wind, ArrayList<McrReaction>> entry : mutable.entrySet()) {
            immutableOptions.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return new McrReactionWindow(
                discarder,
                discard,
                Collections.unmodifiableMap(immutableOptions),
                Map.of());
    }

    public Wind discarder() {
        return discarder;
    }

    public McrTileInstance discard() {
        return discard;
    }

    public List<McrReaction> legalOptions(Wind seat) {
        requireResponder(seat);
        return legalOptions.get(seat);
    }

    public Set<Wind> pendingSeats() {
        LinkedHashSet<Wind> pending = new LinkedHashSet<>();
        for (Wind seat : Wind.values()) {
            if (seat != discarder && !decisions.containsKey(seat)) pending.add(seat);
        }
        return Collections.unmodifiableSet(pending);
    }

    public Map<Wind, McrReaction> decisions() {
        return decisions;
    }

    public boolean isClosed() {
        return decisions.size() == Wind.values().length - 1;
    }

    public McrReactionWindow respond(McrReaction reaction) {
        if (reaction == null || !reaction.discard().equals(discard)) {
            throw new IllegalArgumentException("reaction must target this exact discard");
        }
        Wind seat = reaction.claimant();
        requireResponder(seat);
        if (decisions.containsKey(seat)) {
            throw new IllegalStateException("seat has already responded: " + seat);
        }
        if (reaction.type() != McrReactionType.PASS && !legalOptions.get(seat).contains(reaction)) {
            throw new IllegalArgumentException("reaction was not issued as a legal option");
        }

        EnumMap<Wind, McrReaction> updated = new EnumMap<>(Wind.class);
        updated.putAll(decisions);
        updated.put(seat, reaction);
        return new McrReactionWindow(
                discarder,
                discard,
                legalOptions,
                Collections.unmodifiableMap(updated));
    }

    public McrReactionWindow pass(Wind seat) {
        return respond(McrReaction.pass(seat, discard));
    }

    /** Closes a timed-out window by recording pass for every seat still pending. */
    public McrReactionWindow closeWithPasses() {
        McrReactionWindow result = this;
        for (Wind seat : Wind.values()) {
            if (seat != discarder && !result.decisions.containsKey(seat)) result = result.pass(seat);
        }
        return result;
    }

    public McrReactionResolution resolution() {
        if (!isClosed()) throw new IllegalStateException("reaction window is still open");
        List<McrReaction> claims = decisions.values().stream()
                .filter(reaction -> reaction.type() != McrReactionType.PASS)
                .toList();

        McrReaction hu = nearest(claims, McrReactionType.HU);
        if (hu != null) return new McrReactionResolution.Win(hu);

        McrReaction pungOrKong = nearestPungOrKong(claims);
        if (pungOrKong != null) return new McrReactionResolution.MeldClaim(pungOrKong);

        McrReaction chow = nearest(claims, McrReactionType.CHOW);
        if (chow != null) return new McrReactionResolution.MeldClaim(chow);
        return new McrReactionResolution.NoClaim(nextSeat(discarder));
    }

    private McrReaction nearest(List<McrReaction> claims, McrReactionType type) {
        McrReaction best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (McrReaction claim : claims) {
            if (claim.type() != type) continue;
            int distance = turnDistance(discarder, claim.claimant());
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    private McrReaction nearestPungOrKong(List<McrReaction> claims) {
        McrReaction best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (McrReaction claim : claims) {
            if (claim.type() != McrReactionType.PUNG && claim.type() != McrReactionType.KONG) continue;
            int distance = turnDistance(discarder, claim.claimant());
            if (distance < bestDistance) {
                best = claim;
                bestDistance = distance;
            }
        }
        return best;
    }

    private void requireResponder(Wind seat) {
        if (seat == null || seat == discarder) {
            throw new IllegalArgumentException("discarder cannot respond to their own discard");
        }
    }

    static Wind nextSeat(Wind seat) {
        return Wind.values()[(seat.ordinal() + 1) % Wind.values().length];
    }

    private static int turnDistance(Wind from, Wind to) {
        return (to.ordinal() - from.ordinal() + Wind.values().length) % Wind.values().length;
    }
}
