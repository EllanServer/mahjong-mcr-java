package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.RuleAction;

/** Compact SPI mapping whose player-originated payloads contain only opaque projection IDs. */
final class McrSpiActions {
    private McrSpiActions() {}

    static LegalAction encode(
            McrMatchState state, Wind actor, McrLegalAction legal) {
        RuleAction action = encodeAction(state, actor, legal.action());
        return new LegalAction(
                legal.key(),
                action,
                Map.of("label", legal.key(), "type", action.type()));
    }

    static LegalAction startNextHand() {
        return new LegalAction(
                "start_next_hand",
                new RuleAction("start_next_hand", new byte[0]),
                Map.of("label", "start_next_hand", "type", "start_next_hand"));
    }

    static McrMatchAction decode(McrMatchState state, Wind actor, RuleAction action) {
        if (state == null || actor == null || action == null) {
            throw new IllegalArgumentException("state, actor and action are required");
        }
        byte[] payload = action.payload();
        return switch (action.type()) {
            case "discard" -> play(new McrRoundAction.Discard(
                    actor, resolveOne(state, actor, payload, "discard")));
            case "self_draw_win" -> {
                requireEmpty(payload);
                yield play(new McrRoundAction.SelfDrawWin(actor));
            }
            case "concealed_kong" -> play(new McrRoundAction.ConcealedKong(
                    actor, resolveMany(state, actor, payload, 4, "concealed kong")));
            case "added_kong" -> play(new McrRoundAction.AddedKong(
                    actor, resolveOne(state, actor, payload, "added kong")));
            case "respond" -> play(new McrRoundAction.React(
                    decodeReaction(state, actor, payload)));
            case "start_next_hand" -> {
                requireEmpty(payload);
                yield new McrMatchAction.StartNextHand();
            }
            default -> throw new IllegalArgumentException("unknown MCR action type");
        };
    }

    private static RuleAction encodeAction(
            McrMatchState state, Wind actor, McrRoundAction action) {
        McrProjectionIds ids = McrProjectionIds.forHand(state.currentHandSeed());
        if (action instanceof McrRoundAction.Discard discard) {
            requireActor(actor, discard.seat());
            return oneProjection("discard", ids.project(discard.tile()));
        }
        if (action instanceof McrRoundAction.SelfDrawWin win) {
            requireActor(actor, win.seat());
            return new RuleAction("self_draw_win", new byte[0]);
        }
        if (action instanceof McrRoundAction.ConcealedKong kong) {
            requireActor(actor, kong.seat());
            return new RuleAction("concealed_kong", projected(ids, kong.tiles()));
        }
        if (action instanceof McrRoundAction.AddedKong kong) {
            requireActor(actor, kong.seat());
            return oneProjection("added_kong", ids.project(kong.tile()));
        }
        if (action instanceof McrRoundAction.React respond) {
            McrReaction reaction = respond.reaction();
            requireActor(actor, reaction.claimant());
            byte[] projections = projected(ids, reaction.concealedTiles());
            byte[] payload = new byte[2 + projections.length];
            payload[0] = (byte) reaction.type().ordinal();
            payload[1] = (byte) projections.length;
            System.arraycopy(projections, 0, payload, 2, projections.length);
            return new RuleAction("respond", payload);
        }
        throw new IllegalArgumentException("actor-owned MCR action cannot be player-authorized");
    }

    private static McrReaction decodeReaction(
            McrMatchState state, Wind actor, byte[] payload) {
        if (payload.length < 2) {
            throw new IllegalArgumentException("response payload is truncated");
        }
        int typeOrdinal = Byte.toUnsignedInt(payload[0]);
        if (typeOrdinal >= McrReactionType.values().length) {
            throw new IllegalArgumentException("unknown MCR reaction type");
        }
        int count = Byte.toUnsignedInt(payload[1]);
        if (count > 3 || payload.length != count + 2) {
            throw new IllegalArgumentException("invalid MCR response tile count");
        }
        byte[] projected = Arrays.copyOfRange(payload, 2, payload.length);
        List<McrTileInstance> concealed = resolveMany(
                state, actor, projected, count, "reaction");
        McrReactionWindow window = state.roundState().reactionWindow().orElseThrow(
                () -> new IllegalArgumentException("there is no active reaction window"));
        return new McrReaction(
                actor,
                McrReactionType.values()[typeOrdinal],
                window.discard(),
                concealed);
    }

    private static McrTileInstance resolveOne(
            McrMatchState state, Wind actor, byte[] payload, String name) {
        return resolveMany(state, actor, payload, 1, name).getFirst();
    }

    private static List<McrTileInstance> resolveMany(
            McrMatchState state,
            Wind actor,
            byte[] payload,
            int expected,
            String name) {
        if (payload.length != expected) {
            throw new IllegalArgumentException(name + " payload has the wrong length");
        }
        McrProjectionIds ids = McrProjectionIds.forHand(state.currentHandSeed());
        ArrayList<McrTileInstance> result = new ArrayList<>(expected);
        HashSet<Integer> unique = new HashSet<>();
        for (byte value : payload) {
            int projected = Byte.toUnsignedInt(value);
            if (projected >= McrTileInstance.PHYSICAL_TILE_COUNT || !unique.add(projected)) {
                throw new IllegalArgumentException(name + " contains an invalid projection");
            }
            McrTileInstance resolved = null;
            for (McrTileInstance tile : state.roundState().hand(actor)) {
                if (ids.project(tile) == projected) {
                    resolved = tile;
                    break;
                }
            }
            if (resolved == null) {
                throw new IllegalArgumentException(name + " projection is absent from the actor hand");
            }
            result.add(resolved);
        }
        return List.copyOf(result);
    }

    private static byte[] projected(McrProjectionIds ids, List<McrTileInstance> tiles) {
        int[] projected = new int[tiles.size()];
        for (int index = 0; index < tiles.size(); index++) {
            projected[index] = Math.toIntExact(ids.project(tiles.get(index)));
        }
        Arrays.sort(projected);
        byte[] result = new byte[projected.length];
        for (int index = 0; index < projected.length; index++) {
            result[index] = (byte) projected[index];
        }
        return result;
    }

    private static RuleAction oneProjection(String type, long projection) {
        return new RuleAction(type, new byte[] {(byte) Math.toIntExact(projection)});
    }

    private static void requireActor(Wind expected, Wind actual) {
        if (expected != actual) throw new IllegalArgumentException("MCR action actor diverged");
    }

    private static void requireEmpty(byte[] payload) {
        if (payload.length != 0) {
            throw new IllegalArgumentException("MCR action payload must be empty");
        }
    }

    private static McrMatchAction play(McrRoundAction action) {
        return new McrMatchAction.Play(action);
    }
}
