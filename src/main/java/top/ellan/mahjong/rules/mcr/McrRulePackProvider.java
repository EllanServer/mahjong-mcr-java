package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.MatchPlayer;
import top.ellan.mahjong.spi.MatchSeed;
import top.ellan.mahjong.spi.MatchSetup;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.PrivateRuleView;
import top.ellan.mahjong.spi.ProfileId;
import top.ellan.mahjong.spi.PublicRuleView;
import top.ellan.mahjong.spi.RuleAction;
import top.ellan.mahjong.spi.RuleEvent;
import top.ellan.mahjong.spi.RuleId;
import top.ellan.mahjong.spi.RulePackDescriptor;
import top.ellan.mahjong.spi.RulePackProvider;
import top.ellan.mahjong.spi.RuleProfileDescriptor;
import top.ellan.mahjong.spi.RuleState;
import top.ellan.mahjong.spi.RuleStateSnapshot;
import top.ellan.mahjong.spi.RuleTablePresentation;
import top.ellan.mahjong.spi.RuleTilePresentation;
import top.ellan.mahjong.spi.RuleTransition;
import top.ellan.mahjong.spi.RuleViewTile;
import top.ellan.mahjong.spi.RuleViewZone;
import top.ellan.mahjong.spi.ScheduledRuleAction;
import top.ellan.mahjong.spi.SeatId;
import top.ellan.mahjong.spi.SpiVersion;
import top.ellan.mahjong.spi.TileInstanceId;
import top.ellan.mahjong.spi.TileVisualId;
import top.ellan.mahjong.spi.TransitionDisposition;

/** Official four-player WMO/EMA Green Book provider for MahjongPaper's Java SPI. */
public final class McrRulePackProvider implements RulePackProvider {
    public static final RuleId RULE_ID = new RuleId("mcr");
    public static final ProfileId PROFILE_ID = new ProfileId("green-book");
    public static final String PACK_VERSION = "2.0.0";
    public static final String SETUP_SEED_ALGORITHM = "xor-rotate-splitmix64-finalizer-v1";

    private static final TileVisualId BACK = new TileVisualId("mcr:tile/back");
    private static final TileVisualId[] FACE_VISUALS = createFaceVisuals();
    private static final RulePackDescriptor DESCRIPTOR = descriptorValue();

    private final McrMatchEngine engine = new McrMatchEngine();
    private final McrLegalActionGenerator legalActions = new McrLegalActionGenerator();
    private final McrScheduledActionPolicy scheduledActions =
            new McrScheduledActionPolicy(legalActions);
    private final McrAutomationPolicy automation = new McrAutomationPolicy();
    private final McrViewProjector views = new McrViewProjector();
    private final McrProviderSnapshotCodec snapshots = new McrProviderSnapshotCodec();

    @Override
    public RulePackDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public RuleState createMatch(MatchSetup setup) {
        if (setup == null) throw new NullPointerException("setup");
        if (!PROFILE_ID.equals(setup.profileId())) {
            throw new IllegalArgumentException("unsupported MCR profile: " + setup.profileId());
        }
        if (setup.players().size() != Wind.values().length) {
            throw new IllegalArgumentException("the Green Book profile requires four players");
        }
        if (!setup.configuration().isEmpty()) {
            throw new IllegalArgumentException("the Green Book profile has no house-rule options");
        }
        PlayerId[] players = new PlayerId[Wind.values().length];
        for (MatchPlayer player : setup.players()) {
            int seat = player.seatId().value();
            if (seat >= players.length || players[seat] != null) {
                throw new IllegalArgumentException("initial seats 0 through 3 must each be assigned once");
            }
            players[seat] = player.playerId();
        }
        for (PlayerId player : players) {
            if (player == null) {
                throw new IllegalArgumentException("initial seats 0 through 3 must each be assigned once");
            }
        }
        McrMatchState match = McrMatchState.start(deriveMatchSeed(setup.seed()));
        return new McrProviderState(match, List.of(players));
    }

    @Override
    public RuleTransition transition(RuleState state, PlayerId actor, RuleAction action) {
        McrProviderState current = requireState(state);
        if (actor == null || action == null) {
            return RuleTransition.rejected(current, "null_input");
        }
        Wind initialSeat = current.initialSeat(actor);
        if (initialSeat == null) {
            return RuleTransition.rejected(current, "actor_not_seated");
        }
        Wind logicalSeat = current.match().logicalSeatOf(initialSeat);
        McrMatchAction decoded;
        if (scheduledActions.isActorOwned(action)) {
            Optional<McrMatchAction> actorOwned =
                    scheduledActions.decodeActorOwned(current, actor, action);
            if (actorOwned.isEmpty()) {
                return RuleTransition.rejected(current, "actor_action_not_authorized");
            }
            decoded = actorOwned.orElseThrow();
        } else {
            try {
                decoded = McrSpiActions.decode(
                        current.match(), current.projectionIds(), logicalSeat, action);
            } catch (IllegalArgumentException invalid) {
                return RuleTransition.rejected(current, "invalid_action_payload");
            }
        }
        if (decoded instanceof McrMatchAction.StartNextHand
                && !scheduledActions.mayStartNextHand(current, actor)) {
            return RuleTransition.rejected(current, "next_dealer_required");
        }

        McrMatchTransition domain = engine.transition(current.match(), decoded);
        if (!domain.accepted()) {
            return RuleTransition.rejected(current, rejectionReason(domain));
        }
        List<RuleEvent> events = McrSpiEvents.encode(domain.events(), current.match());
        if (events.isEmpty()) {
            throw new IllegalStateException("accepted MCR transition emitted no canonical event");
        }
        McrProviderState next = current.withMatch(domain.state());
        return new RuleTransition(
                next,
                disposition(domain.state(), domain.events()),
                events,
                McrPresentationCues.from(next, domain.events()),
                "accepted");
    }

    @Override
    public List<LegalAction> legalActions(RuleState state, PlayerId actor) {
        McrProviderState current = requireState(state);
        if (actor == null) return List.of();
        Wind initialSeat = current.initialSeat(actor);
        if (initialSeat == null || current.match().phase() == McrMatchPhase.ENDED) {
            return List.of();
        }
        if (current.match().phase() == McrMatchPhase.BETWEEN_HANDS) {
            return scheduledActions.mayStartNextHand(current, actor)
                    ? List.of(McrSpiActions.startNextHand()) : List.of();
        }
        Wind logicalSeat = current.match().logicalSeatOf(initialSeat);
        List<McrLegalAction> domain = legalActions.forPlayer(
                current.match().roundState(),
                logicalSeat,
                current.projectionIds());
        ArrayList<LegalAction> result = new ArrayList<>(domain.size());
        for (McrLegalAction legal : domain) {
            result.add(McrSpiActions.encode(
                    current.projectionIds(), logicalSeat, legal));
        }
        return List.copyOf(result);
    }

    @Override
    public Optional<ScheduledRuleAction> scheduledAction(RuleState state) {
        return scheduledActions.next(requireState(state));
    }

    @Override
    public Optional<ScheduledRuleAction> automatedAction(
            RuleState state, List<AutomatedPlayerActions> candidates) {
        return automation.next(requireState(state), List.copyOf(candidates));
    }

    @Override
    public PublicRuleView publicView(RuleState state, long revision) {
        McrProviderState current = requireState(state);
        McrPublicView view = views.publicView(
                current.match().roundState(), current.projectionIds());
        ArrayList<RuleViewTile> tiles = new ArrayList<>(view.tiles().size());
        for (McrViewTile tile : view.tiles()) {
            tiles.add(ruleViewTile(current, tile));
        }
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("profile", PROFILE_ID.value());
        attributes.put("matchPhase", name(current.match().phase()));
        attributes.put("handNumber", Integer.toString(current.match().currentHandNumber()));
        attributes.put("handsCompleted", Integer.toString(current.match().handsCompleted()));
        attributes.put("roundWind", name(view.roundWind()));
        attributes.put("dealer", name(current.match().playerAt(Wind.EAST)));
        attributes.put("currentSeat", name(current.match().playerAt(view.currentSeat())));
        attributes.put("roundPhase", name(view.phase()));
        attributes.put("wallRemaining", Integer.toString(view.wallRemaining()));
        attributes.put("cumulativeScore", score(current.match().cumulativeScore()));
        view.winner().ifPresent(winner ->
                attributes.put("winner", name(current.match().playerAt(winner))));
        McrRoundState round = current.match().roundState();
        Optional<TileInstanceId> lastDiscard = round.reactionWindow()
                .filter(window -> window.origin() == McrReactionOrigin.DISCARD)
                .map(McrReactionWindow::discard)
                .map(current.projectionIds()::project)
                .map(TileInstanceId::new);
        return new PublicRuleView(
                revision,
                name(current.match().phase()),
                tiles,
                attributes,
                new RuleTablePresentation(
                        4,
                        current.openingLayout().wall(),
                        6,
                        Optional.of(new SeatId(
                                current.match().playerAt(Wind.EAST).ordinal())),
                        Optional.of(new SeatId(
                                current.match().playerAt(view.currentSeat()).ordinal())),
                        lastDiscard,
                        Optional.of(current.openingLayout().opening())));
    }

    @Override
    public PrivateRuleView privateView(RuleState state, PlayerId viewer, long revision) {
        McrProviderState current = requireState(state);
        Wind initialSeat = current.initialSeat(viewer);
        if (initialSeat == null) {
            throw new IllegalArgumentException("viewer is not seated in this MCR match");
        }
        Wind logicalSeat = current.match().logicalSeatOf(initialSeat);
        McrPrivateView view = views.privateView(
                current.match().roundState(), logicalSeat, current.projectionIds());
        ArrayList<RuleViewTile> tiles = new ArrayList<>(view.concealedTiles().size());
        for (McrViewTile tile : view.concealedTiles()) {
            tiles.add(ruleViewTile(current, tile));
        }
        return new PrivateRuleView(
                revision,
                viewer,
                new SeatId(initialSeat.ordinal()),
                tiles,
                Map.of(
                        "initialSeat", name(initialSeat),
                        "seatWind", name(logicalSeat),
                        "handSize", Integer.toString(tiles.size())));
    }

    @Override
    public String stateHash(RuleState state) {
        return snapshots.snapshot(requireState(state), 0).sha256();
    }

    @Override
    public RuleStateSnapshot snapshot(RuleState state, long sequence) {
        return snapshots.snapshot(requireState(state), sequence);
    }

    @Override
    public RuleState restore(RuleStateSnapshot snapshot) {
        return snapshots.restore(snapshot);
    }

    private static RulePackDescriptor descriptorValue() {
        LinkedHashSet<String> resources = new LinkedHashSet<>();
        resources.add("assets/mcr/tile-visuals.properties");
        String schema = "{\"type\":\"object\",\"properties\":{},"
                + "\"additionalProperties\":false}";
        return new RulePackDescriptor(
                RULE_ID,
                PACK_VERSION,
                SpiVersion.CURRENT,
                ">=2.0.0",
                McrMatchSnapshot.SCHEMA_VERSION,
                List.of(new RuleProfileDescriptor(
                        PROFILE_ID,
                        "WMO/EMA Green Book (four player)",
                        schema)),
                Set.copyOf(resources));
    }

    private static long deriveMatchSeed(MatchSeed seed) {
        long value = seed.high() ^ Long.rotateLeft(seed.low(), 31) ^ 0x4d43_522d_4d41_5443L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static TransitionDisposition disposition(
            McrMatchState state, List<McrMatchEvent> events) {
        if (state.phase() == McrMatchPhase.ENDED) return TransitionDisposition.MATCH_ENDED;
        for (McrMatchEvent event : events) {
            if (event instanceof McrMatchEvent.HandCompleted) {
                return TransitionDisposition.ROUND_ENDED;
            }
        }
        return TransitionDisposition.ACCEPTED;
    }

    private static String rejectionReason(McrMatchTransition transition) {
        if (!transition.matchViolations().isEmpty()) {
            return "match." + name(transition.matchViolations().getFirst());
        }
        if (!transition.roundViolations().isEmpty()) {
            return "round." + name(transition.roundViolations().getFirst());
        }
        return "rule.rejected";
    }

    private static McrProviderState requireState(RuleState state) {
        if (!(state instanceof McrProviderState mcr)) {
            throw new IllegalArgumentException("rule state was not created by the MCR provider");
        }
        return mcr;
    }

    private static RuleViewTile ruleViewTile(McrProviderState state, McrViewTile tile) {
        TileVisualId visual = tile.faceUp()
                ? FACE_VISUALS[tile.face().orElseThrow().ordinal()]
                : BACK;
        RuleTilePresentation presentation = tile.zone() == McrViewZone.WALL
                ? RuleTilePresentation.natural(state.wallSlot(tile.projectionId()))
                : tile.presentation();
        return new RuleViewTile(
                new TileInstanceId(tile.projectionId()),
                visual,
                tile.owner()
                        .map(state.match()::playerAt)
                        .map(seat -> new SeatId(seat.ordinal())),
                zone(tile.zone()),
                tile.index(),
                tile.faceUp(),
                presentation);
    }

    private static RuleViewZone zone(McrViewZone zone) {
        return switch (zone) {
            case WALL -> RuleViewZone.WALL;
            case CONCEALED_HAND -> RuleViewZone.HAND;
            case FLOWER -> RuleViewZone.FLOWER;
            case WIN_CLAIM -> RuleViewZone.WIN_CLAIM;
            case RIVER -> RuleViewZone.DISCARD;
            case MELD -> RuleViewZone.MELD;
        };
    }

    private static String score(Map<Wind, Integer> score) {
        return score.get(Wind.EAST) + "," + score.get(Wind.SOUTH) + ","
                + score.get(Wind.WEST) + "," + score.get(Wind.NORTH);
    }

    private static String visualName(Tile tile) {
        return tile == Tile.BAMBOO_FLOWER ? "bamboo" : name(tile);
    }

    private static TileVisualId[] createFaceVisuals() {
        Tile[] tiles = Tile.values();
        TileVisualId[] result = new TileVisualId[tiles.length];
        for (Tile tile : tiles) {
            result[tile.ordinal()] = new TileVisualId("mcr:tile/" + visualName(tile));
        }
        return result;
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
