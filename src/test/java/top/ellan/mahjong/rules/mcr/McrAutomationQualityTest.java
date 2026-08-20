package top.ellan.mahjong.rules.mcr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.MatchPlayer;
import top.ellan.mahjong.spi.MatchSeed;
import top.ellan.mahjong.spi.MatchSetup;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RuleState;
import top.ellan.mahjong.spi.RuleTransition;
import top.ellan.mahjong.spi.ScheduledRuleAction;
import top.ellan.mahjong.spi.SeatId;

/**
 * Guards the decision quality of the MCR automation, not merely its legality.
 *
 * <p>Before this suite existed the bot could pass on every claim and never kong while still
 * satisfying the "returns some accepted action" smoke test, so those regressions were invisible.</p>
 */
class McrAutomationQualityTest {
    private static final int MAX_STEPS = 20_000;

    private final McrRulePackProvider provider = new McrRulePackProvider();
    private final List<MatchPlayer> players = players();

    @Test
    void readyScoreRanksAReadyHandAboveAnUnreadyOne() {
        McrBotDecisionService brain = new McrBotDecisionService();
        // Waiting on 3m to complete 1m2m3m; every set is a pure straight family with 8+ fan.
        TileCounts ready = TileCounts.of(
                Tile.M1, Tile.M2, Tile.M4, Tile.M5, Tile.M6, Tile.M7, Tile.M8, Tile.M9,
                Tile.M3, Tile.M4, Tile.M5, Tile.M9, Tile.M9);
        TileCounts scattered = TileCounts.of(
                Tile.M1, Tile.S4, Tile.P7, Tile.EAST, Tile.SOUTH, Tile.WEST, Tile.NORTH,
                Tile.RED_DRAGON, Tile.GREEN_DRAGON, Tile.M5, Tile.S9, Tile.P2, Tile.M8);

        long readyScore = brain.readyScore(ready, List.of(), Wind.EAST, Wind.EAST, List.of());
        long scatteredScore =
                brain.readyScore(scattered, List.of(), Wind.EAST, Wind.EAST, List.of());

        assertTrue(scatteredScore == 0L, "a hand with no legal wait must score zero");
        assertTrue(readyScore > scatteredScore, "a ready hand must outrank an unready one");
    }

    @Test
    void discardPreferenceKeepsPairsAndNeighboursAndReleasesIsolatedTerminals() {
        TileCounts hand = TileCounts.of(
                Tile.M1, Tile.M5, Tile.M6, Tile.S3, Tile.S3, Tile.P9);

        int isolatedTerminal = McrBotDecisionService.discardPreference(hand, Tile.P9);
        int neighbour = McrBotDecisionService.discardPreference(hand, Tile.M5);
        int pair = McrBotDecisionService.discardPreference(hand, Tile.S3);

        assertTrue(isolatedTerminal > neighbour, "an isolated terminal should leave before a neighbour");
        assertTrue(isolatedTerminal > pair, "an isolated terminal should leave before a pair");
    }

    @Test
    void automatedMatchesClaimMeldsAndDeclareKongs() {
        Set<String> observed = new LinkedHashSet<>();
        for (long seed = 1; seed <= 6; seed++) {
            observed.addAll(drive(seed));
        }

        assertTrue(
                observed.stream().anyMatch(key -> key.startsWith("respond:pung")
                        || key.startsWith("respond:chow")
                        || key.startsWith("respond:kong")),
                "the MCR bot never claimed a meld across six matches: " + observed);
        assertTrue(
                observed.stream().anyMatch(key -> key.startsWith("concealed_kong")
                        || key.startsWith("added_kong")),
                "the MCR bot never declared a turn kong across six matches: " + observed);
        assertFalse(observed.isEmpty());
    }

    /** Plays one fully automated match and returns every action key the bot chose. */
    private Set<String> drive(long seed) {
        RuleState state = provider.createMatch(new MatchSetup(
                McrRulePackProvider.PROFILE_ID,
                new MatchSeed(seed, seed * 31 + 7),
                players,
                Map.of("handLimit", "4")));
        Set<String> chosen = new LinkedHashSet<>();
        for (int step = 0; step < MAX_STEPS; step++) {
            ArrayList<AutomatedPlayerActions> candidates = new ArrayList<>(4);
            for (MatchPlayer player : players) {
                List<LegalAction> legal = provider.legalActions(state, player.playerId());
                if (!legal.isEmpty()) {
                    candidates.add(new AutomatedPlayerActions(player.playerId(), legal));
                }
            }
            ScheduledRuleAction next = null;
            if (!candidates.isEmpty()) {
                next = provider.automatedAction(state, candidates).orElse(null);
                if (next != null) {
                    chosen.add(keyOf(state, next));
                }
            }
            if (next == null) {
                next = provider.scheduledAction(state).orElse(null);
            }
            if (next == null) {
                return chosen;
            }
            RuleTransition transition =
                    provider.transition(state, next.actor(), next.action());
            assertTrue(
                    transition.accepted(),
                    "automation produced a rejected action: " + next.action().type());
            state = transition.nextState();
        }
        throw new AssertionError("automated MCR match did not finish within " + MAX_STEPS + " steps");
    }

    private String keyOf(RuleState state, ScheduledRuleAction scheduled) {
        for (LegalAction legal : provider.legalActions(state, scheduled.actor())) {
            if (legal.action().equals(scheduled.action())) {
                return legal.key();
            }
        }
        return scheduled.action().type();
    }

    private static List<MatchPlayer> players() {
        ArrayList<MatchPlayer> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            result.add(new MatchPlayer(
                    new PlayerId(new UUID(0, index + 1L)), new SeatId(index)));
        }
        return List.copyOf(result);
    }
}
