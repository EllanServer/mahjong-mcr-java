package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.MatchPlayer;
import top.ellan.mahjong.spi.MatchSeed;
import top.ellan.mahjong.spi.MatchSetup;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.PrivateRuleView;
import top.ellan.mahjong.spi.PublicRuleView;
import top.ellan.mahjong.spi.RuleAction;
import top.ellan.mahjong.spi.RulePackProvider;
import top.ellan.mahjong.spi.RuleState;
import top.ellan.mahjong.spi.RuleStateSnapshot;
import top.ellan.mahjong.spi.RuleTransition;
import top.ellan.mahjong.spi.RuleViewZone;
import top.ellan.mahjong.spi.ScheduledRuleAction;
import top.ellan.mahjong.spi.SeatId;
import top.ellan.mahjong.spi.TransitionDisposition;
import top.ellan.mahjong.tck.RulePackTck;
import top.ellan.mahjong.tck.RulePackTckCase;
import top.ellan.mahjong.tck.RulePackTckReport;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McrRulePackProviderTest {
    private final McrRulePackProvider provider = new McrRulePackProvider();
    private final List<MatchPlayer> players = players();

    @Test
    void officialProviderPassesTheReusableRulePackTck() {
        MatchSetup setup = setup();
        RulePackTckReport report = RulePackTck.verify(
                provider,
                new RulePackTckCase(
                        setup,
                        Map.of(players.getFirst().playerId(),
                                new RuleAction("discard", new byte[] {(byte) 0xff}))));

        assertEquals(4, report.playersVerified());
        assertTrue(report.legalActionsVerified() >= 14);
        assertEquals(1, report.rejectedActionsVerified());
        assertEquals(3, report.snapshotsVerified());
    }

    @Test
    void serviceLoaderDescriptorAndResourcesExposeOneOfficialPack() throws Exception {
        List<RulePackProvider> providers = ServiceLoader.load(RulePackProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertEquals(1, providers.size());
        assertTrue(providers.getFirst() instanceof McrRulePackProvider);
        assertEquals(McrRulePackProvider.RULE_ID, provider.descriptor().ruleId());
        assertEquals(McrRulePackProvider.PROFILE_ID,
                provider.descriptor().profiles().getFirst().id());
        assertEquals(1, provider.descriptor().stateSchemaVersion());
        assertEquals(Set.of("assets/mcr/tile-visuals.properties"),
                provider.descriptor().requiredResources());

        Properties manifest = new Properties();
        try (InputStream input = McrRulePackProvider.class.getClassLoader()
                .getResourceAsStream("META-INF/mahjong-rule-pack.properties")) {
            assertTrue(input != null);
            manifest.load(input);
        }
        assertEquals(provider.descriptor().ruleId().value(), manifest.getProperty("id"));
        assertEquals(provider.descriptor().version(), manifest.getProperty("version"));
        assertEquals(provider.descriptor().spiVersion(), manifest.getProperty("spiVersion"));
        assertEquals(provider.descriptor().requiredCoreVersion(),
                manifest.getProperty("requiredCoreVersion"));
        assertEquals(Integer.toString(provider.descriptor().stateSchemaVersion()),
                manifest.getProperty("stateSchemaVersion"));
        assertEquals("assets/mcr/tile-visuals.properties",
                manifest.getProperty("requiredResources"));
        assertTrue(McrRulePackProvider.class.getClassLoader().getResource(
                "assets/mcr/tile-visuals.properties") != null);

        Properties tileVisuals = new Properties();
        try (InputStream input = McrRulePackProvider.class.getClassLoader()
                .getResourceAsStream("assets/mcr/tile-visuals.properties")) {
            assertTrue(input != null);
            tileVisuals.load(input);
        }
        assertEquals("mcr:tile/bamboo", tileVisuals.getProperty("bamboo_flower"));
    }

    @Test
    void publicAndPrivateViewsNeverPublishAnotherPlayersConcealedFaces() {
        RuleState state = provider.createMatch(setup());
        PublicRuleView publicView = provider.publicView(state, 7);
        assertEquals(7, publicView.stateRevision());
        assertEquals(144, publicView.tiles().size());
        assertEquals(144, new HashSet<>(publicView.tiles().stream()
                .map(tile -> tile.instanceId().value()).toList()).size());
        assertEquals(List.of(18, 18, 18, 18),
                publicView.tablePresentation().wall().stackCountsBySide());
        assertEquals(144, publicView.tablePresentation().wall().tileCapacity());
        assertTrue(publicView.tiles().stream()
                .filter(tile -> tile.zone() == RuleViewZone.WALL
                        || tile.zone() == RuleViewZone.HAND)
                .allMatch(tile -> !tile.faceUp()
                        && tile.visualId().value().equals("mcr:tile/back")));

        PlayerId east = players.getFirst().playerId();
        PrivateRuleView privateView = provider.privateView(state, east, 7);
        assertEquals(east, privateView.viewer());
        assertEquals(new SeatId(0), privateView.seat());
        assertEquals(14, privateView.tiles().size());
        assertTrue(privateView.tiles().stream().allMatch(tile -> tile.faceUp()
                && tile.zone() == RuleViewZone.HAND
                && tile.owner().orElseThrow().equals(new SeatId(0))
                && !tile.visualId().value().equals("mcr:tile/back")));
        assertThrows(IllegalArgumentException.class, () -> provider.privateView(
                state,
                new PlayerId(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
                7));
    }

    @Test
    void generatedActionsCarryOpaqueIdsAndForgedProjectionIsRejected() {
        RuleState state = provider.createMatch(setup());
        PlayerId east = players.getFirst().playerId();
        List<LegalAction> discards = provider.legalActions(state, east).stream()
                .filter(action -> action.action().type().equals("discard"))
                .toList();
        assertEquals(14, discards.size());
        for (LegalAction discard : discards) {
            byte[] payload = discard.action().payload();
            assertEquals(1, payload.length);
            assertEquals(
                    Integer.parseInt(discard.key().substring("discard:".length())),
                    Byte.toUnsignedInt(payload[0]));
            assertEquals(top.ellan.mahjong.spi.ActionPlacement.HAND_TILE,
                    discard.actionPresentation().placement());
            assertEquals(Byte.toUnsignedInt(payload[0]),
                    discard.actionPresentation().targetTile().orElseThrow().value());
        }

        RuleTransition forged = provider.transition(
                state, east, new RuleAction("discard", new byte[] {(byte) 0xff}));
        assertEquals(TransitionDisposition.REJECTED, forged.disposition());
        assertSame(state, forged.nextState());
        assertEquals("invalid_action_payload", forged.reasonCode());
    }

    @Test
    void providerSnapshotAuthenticatesPlayerAssignmentsAndRestoresAuthorization() {
        RuleState state = provider.createMatch(setup());
        RuleStateSnapshot snapshot = provider.snapshot(state, 23);
        RuleState restored = provider.restore(snapshot);
        assertEquals(provider.stateHash(state), provider.stateHash(restored));
        for (MatchPlayer player : players) {
            assertEquals(
                    provider.privateView(state, player.playerId(), 0),
                    provider.privateView(restored, player.playerId(), 0));
        }

        byte[] corrupted = snapshot.payload();
        corrupted[8] ^= 1;
        RuleStateSnapshot invalid = new RuleStateSnapshot(
                snapshot.schemaVersion(), snapshot.sequence(), corrupted, snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> provider.restore(invalid));
    }

    @Test
    void onlyTheIncomingDealerCanAdvanceACompletedHand() {
        McrProviderState boundary = boundaryProviderState();
        PlayerId east = players.getFirst().playerId();
        PlayerId south = players.get(1).playerId();
        assertTrue(provider.legalActions(boundary, east).isEmpty());
        LegalAction start = provider.legalActions(boundary, south).getFirst();
        assertEquals("start_next_hand", start.key());

        RuleTransition transition = provider.transition(boundary, south, start.action());
        assertTrue(transition.accepted());
        McrProviderState next = (McrProviderState) transition.nextState();
        assertEquals(2, next.match().currentHandNumber());
        assertEquals(Wind.SOUTH, next.match().playerAt(Wind.EAST));
        assertEquals("south", provider.publicView(next, 1).attributes().get("dealer"));
    }

    @Test
    void scheduledActionsCloseReactionsAndAdvanceOnlyThroughCanonicalTransitions() {
        McrProviderState reacting = reactingProviderState();
        ScheduledRuleAction close = provider.scheduledAction(reacting).orElseThrow();
        assertEquals(players.getFirst().playerId(), close.actor());
        assertEquals(McrScheduledActionPolicy.CLOSE_REACTIONS_TYPE, close.action().type());
        assertEquals(McrScheduledActionPolicy.REACTION_DELAY, close.delay());

        RuleTransition closed = provider.transition(reacting, close.actor(), close.action());
        assertTrue(closed.accepted());
        McrProviderState afterClose = (McrProviderState) closed.nextState();
        assertFalse(afterClose.match().roundState().phase() == McrRoundPhase.REACTIONS);
        assertTrue(provider.scheduledAction(afterClose).isEmpty());

        PlayerId wrongActor = players.get(1).playerId();
        RuleTransition forged = provider.transition(reacting, wrongActor, close.action());
        assertEquals(TransitionDisposition.REJECTED, forged.disposition());
        assertSame(reacting, forged.nextState());

        McrProviderState boundary = boundaryProviderState();
        ScheduledRuleAction nextHand = provider.scheduledAction(boundary).orElseThrow();
        assertEquals(players.get(1).playerId(), nextHand.actor());
        assertEquals("start_next_hand", nextHand.action().type());
        assertEquals(McrScheduledActionPolicy.NEXT_HAND_DELAY, nextHand.delay());
        assertTrue(provider.transition(
                boundary, nextHand.actor(), nextHand.action()).accepted());
    }

    @Test
    void unsupportedProfilesPlayerCountsAndConfigurationFailClosed() {
        MatchSetup wrongProfile = new MatchSetup(
                new top.ellan.mahjong.spi.ProfileId("local-house-rule"),
                new MatchSeed(1, 2),
                players,
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(wrongProfile));

        MatchSetup threePlayers = new MatchSetup(
                McrRulePackProvider.PROFILE_ID,
                new MatchSeed(1, 2),
                players.subList(0, 3),
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(threePlayers));
        MatchSetup configured = new MatchSetup(
                McrRulePackProvider.PROFILE_ID,
                new MatchSeed(1, 2),
                players,
                Map.of("minimumFan", "6"));
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(configured));
    }

    private McrProviderState boundaryProviderState() {
        long matchSeed = 0x4d43522d73706974L;
        McrMatchState active = new McrMatchState(
                new McrMatchConfig(2),
                matchSeed,
                0,
                McrMatchPhase.HAND_ACTIVE,
                0,
                McrMatchState.deriveHandSeed(matchSeed, 0),
                eastPureStraightInitialWin(),
                McrMatchState.zeroScores(),
                List.of());
        McrMatchState boundary = new McrMatchEngine().transition(
                active,
                new McrMatchAction.Play(new McrRoundAction.SelfDrawWin(Wind.EAST))).state();
        return new McrProviderState(
                boundary, players.stream().map(MatchPlayer::playerId).toList());
    }

    private McrProviderState reactingProviderState() {
        long matchSeed = 0x4d43522d74696d65L;
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        place(order, 4, 12);
        McrRoundState before = McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
        McrRoundState reacting = new McrRoundEngine().transition(
                before,
                new McrRoundAction.Discard(Wind.EAST, McrTileInstance.fromId(16))).state();
        assertEquals(McrRoundPhase.REACTIONS, reacting.phase());
        McrMatchState match = new McrMatchState(
                new McrMatchConfig(2),
                matchSeed,
                1,
                McrMatchPhase.HAND_ACTIVE,
                0,
                McrMatchState.deriveHandSeed(matchSeed, 0),
                reacting,
                McrMatchState.zeroScores(),
                List.of());
        return new McrProviderState(
                match, players.stream().map(MatchPlayer::playerId).toList());
    }

    private MatchSetup setup() {
        return new MatchSetup(
                McrRulePackProvider.PROFILE_ID,
                new MatchSeed(0x0123_4567_89ab_cdefL, 0xfedc_ba98_7654_3210L),
                players,
                Map.of());
    }

    private static List<MatchPlayer> players() {
        ArrayList<MatchPlayer> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            result.add(new MatchPlayer(
                    new PlayerId(new UUID(0, index + 1L)), new SeatId(index)));
        }
        return List.copyOf(result);
    }

    private static McrRoundState eastPureStraightInitialWin() {
        ArrayList<McrTileInstance> order = new ArrayList<>(McrTileInstance.fullSet());
        int[] eastPositions = {0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48};
        int[] concealedBeforeWin = {0, 4, 8, 12, 16, 20, 24, 28, 72, 73, 74, 40, 41};
        for (int index = 0; index < eastPositions.length; index++) {
            place(order, eastPositions[index], concealedBeforeWin[index]);
        }
        place(order, 52, 32);
        return McrRoundState.start(
                McrInitialDealer.deal(McrWall.fromOrder(order)), Wind.EAST);
    }

    private static void place(ArrayList<McrTileInstance> order, int position, int tileId) {
        int current = -1;
        for (int index = 0; index < order.size(); index++) {
            if (order.get(index).id() == tileId) {
                current = index;
                break;
            }
        }
        McrTileInstance displaced = order.get(position);
        order.set(position, order.get(current));
        order.set(current, displaced);
    }
}
