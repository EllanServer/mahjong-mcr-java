package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Projects exact player and actor-owned timeout commands from one immutable
 * revision. Physical IDs never cross this presentation boundary.
 */
public final class McrLegalActionGenerator {
    private final McrRoundEngine engine;

    public McrLegalActionGenerator() {
        this(new McrRoundEngine());
    }

    public McrLegalActionGenerator(McrRoundEngine engine) {
        if (engine == null) throw new IllegalArgumentException("round engine is required");
        this.engine = engine;
    }

    /** Returns only commands authorized for {@code actor} at the supplied revision. */
    public List<McrLegalAction> forPlayer(McrRoundState state, Wind actor, long handSalt) {
        if (state == null || actor == null) {
            throw new IllegalArgumentException("state and actor are required");
        }
        if (state.phase() == McrRoundPhase.ENDED) return List.of();
        McrProjectionIds ids = McrProjectionIds.forHand(handSalt);
        if (state.phase() == McrRoundPhase.REACTIONS) {
            return reactionActions(state, actor, ids);
        }
        if (actor != state.currentSeat()) return List.of();

        ArrayList<McrLegalAction> result = new ArrayList<>(state.hand(actor).size() + 6);
        McrRoundAction.SelfDrawWin selfDraw = new McrRoundAction.SelfDrawWin(actor);
        if (engine.transition(state, selfDraw).accepted()) {
            result.add(new McrLegalAction("self_draw_win", selfDraw));
        }
        for (McrTileInstance tile : state.hand(actor)) {
            result.add(new McrLegalAction(
                    "discard:" + ids.project(tile),
                    new McrRoundAction.Discard(actor, tile)));
        }
        addKongs(state, actor, ids, result);
        return List.copyOf(result);
    }

    /** Actor-owned commands are scheduled by the table actor and never sent to a player. */
    public List<McrLegalAction> systemActions(McrRoundState state) {
        if (state == null) throw new IllegalArgumentException("state is required");
        if (state.phase() != McrRoundPhase.REACTIONS) return List.of();
        McrRoundAction.CloseReactions close = new McrRoundAction.CloseReactions();
        return List.of(new McrLegalAction("close_reactions", close));
    }

    private static List<McrLegalAction> reactionActions(
            McrRoundState state, Wind actor, McrProjectionIds ids) {
        McrReactionWindow window = state.reactionWindow().orElseThrow();
        if (!window.pendingSeats().contains(actor)) return List.of();
        List<McrReaction> options = window.legalOptions(actor);
        ArrayList<McrLegalAction> result = new ArrayList<>(options.size() + 1);
        for (McrReaction reaction : options) {
            result.add(new McrLegalAction(
                    reactionKey(reaction, ids), new McrRoundAction.React(reaction)));
        }
        McrReaction pass = McrReaction.pass(actor, window.discard());
        result.add(new McrLegalAction("respond:pass", new McrRoundAction.React(pass)));
        return List.copyOf(result);
    }

    private static void addKongs(
            McrRoundState state,
            Wind actor,
            McrProjectionIds ids,
            List<McrLegalAction> result) {
        if (state.lastDraw().isEmpty() || state.wall().isEmpty()) return;
        @SuppressWarnings("unchecked")
        ArrayList<McrTileInstance>[] byKind = new ArrayList[Tile.STANDARD_KIND_COUNT];
        for (McrTileInstance tile : state.hand(actor)) {
            ArrayList<McrTileInstance> copies = byKind[tile.kind().index()];
            if (copies == null) {
                copies = new ArrayList<>(4);
                byKind[tile.kind().index()] = copies;
            }
            copies.add(tile);
        }
        for (ArrayList<McrTileInstance> copies : byKind) {
            if (copies != null && copies.size() == 4) {
                Tile kind = copies.getFirst().kind();
                result.add(new McrLegalAction(
                        "concealed_kong:" + kind.name().toLowerCase(java.util.Locale.ROOT),
                        new McrRoundAction.ConcealedKong(actor, copies)));
            }
        }
        for (McrPhysicalMeld meld : state.melds(actor)) {
            if (meld.origin() != McrMeldOrigin.PUNG) continue;
            ArrayList<McrTileInstance> copies = byKind[meld.tiles().getFirst().kind().index()];
            if (copies == null) continue;
            for (McrTileInstance fourth : copies) {
                result.add(new McrLegalAction(
                        "added_kong:" + ids.project(fourth),
                        new McrRoundAction.AddedKong(actor, fourth)));
            }
        }
    }

    private static String reactionKey(McrReaction reaction, McrProjectionIds ids) {
        StringBuilder key = new StringBuilder("respond:")
                .append(reaction.type().name().toLowerCase(java.util.Locale.ROOT));
        if (!reaction.concealedTiles().isEmpty()) {
            List<Long> projected = reaction.concealedTiles().stream()
                    .map(ids::project)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            key.append(':');
            for (int index = 0; index < projected.size(); index++) {
                if (index > 0) key.append('-');
                key.append(projected.get(index));
            }
        }
        return key.toString();
    }
}
