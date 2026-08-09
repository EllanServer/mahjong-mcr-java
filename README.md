# mahjong-mcr-java

Pure Java 21 implementation of Mahjong Competition Rules (MCR) hand structure,
the 81 official scoring elements, waits, the eight-point minimum, settlement, and
the first physical-match primitives used by the MahjongPaper 2.0 rule pack.
The runtime has no third-party dependencies and contains no JNI, JSON, reflection,
global mutable cache, UI, server, or bot code.

This is an independent implementation library, not an official WMO/EMA-certified
rules engine. Its pinned normative baseline is the WMO Mahjong Competition Rules
"Green Book" hosted by EMA, supplemented by the EMA MCR tournament regulations:

- https://mahjong-europe.org/portal/images/docs/mcr_EN.pdf
- https://mahjong-europe.org/portal/images/docs/mcr_regulations.pdf

Both sources were read on 2026-08-08. GB-Mahjong revision
`a2911ad6447f31aa17e553350f9ec5dffb7d3053` is used only as a differential
migration oracle. It is never a rules authority, and a native/Java mismatch must
be resolved against the pinned normative sources.

**Migrating all 81 algorithm branches is not the same as independent golden-case
certification of all 81 fans.** The current per-fan validation status and remaining
release work are stated explicitly in `RULE_COVERAGE.md`.

## Build

Use the checked-in Gradle wrapper on a JDK 21+ installation:

```shell
./gradlew clean test
./gradlew jar
```

The published main artifact has no runtime dependencies. JUnit 5 and the JUnit
Platform launcher are test-only dependencies.

## API

The concealed count passed to `WinInput` excludes the winning tile. A kong counts
as one structural set, so a hand with one declared meld has ten concealed tiles
before winning, two declared melds have seven, and so on.

```java
McrRulesEngine engine = new StandardMcrRulesEngine();
WinEvaluation result = engine.evaluate(new WinInput(
    TileCounts.parse("W1", "W2", "W3", "W4", "W5", "W6", "W7", "W8",
                     "B1", "B1", "B1", "T2", "T2"),
    List.of(),
    Tile.parse("W9"),
    WinContext.standard(Wind.EAST, Wind.EAST, WinMethod.DISCARD)
));

if (result.legalWin()) {
    Payment payment = McrPayments.settle(result, Wind.EAST, Wind.SOUTH);
}
```

`winningShape()` and `legalWin()` are deliberately different. `legalWin()` requires
at least eight qualifying points. Flower points are reported in `flowerFan()` and
`totalFan()` but never contribute to `qualifyingFan()` or the minimum.

`WinEvaluation` is immutable and engine-issued; it has no public constructor. This
prevents a caller from fabricating a typed award list and passing a forged score to
`McrPayments.settle`.

`waits(WaitInput)` returns a tile only when discard win, self draw, or both meet the
minimum. It records the two methods separately, allowing a seven-point discard hand
which becomes eight on self draw.

## Physical wall and initial deal

`McrTileInstance` defines all 144 identities: four copies of each of the 34
standard kinds and one copy of each of the eight flowers. `McrWall` is immutable;
ordinary draws consume its front and replacement draws consume its back. Its
repository-owned SplitMix64 shuffle is stable across supported JVMs so a recorded
seed can be replayed without relying on a JDK random-provider implementation.

`McrInitialDealer` follows Green Book section 3.5.7: three four-tile blocks per
seat, the dealer's final "one and three" pair, one final tile for each other seat,
then initial flower replacement in East/South/West/North order. A replacement that
is itself a flower is exposed and replaced again from the back. The resulting
`McrInitialDeal` validates all 144 identities, contains no flower in a concealed
hand, and exposes no mutable collection.

`McrOpeningLayout` derives the two official two-dice rolls once from the immutable
hand seed. The first total identifies the second roller/open side and both totals
identify the wall break. The SPI publishes only these rule facts plus the 18-stack
wall geometry. CraftEngine owns dice/furniture assets, transforms, culling and the
bounded opening animation; the rule pack contains no renderer or animation loop.

The exact physical state is the foundation of the complete-game implementation.
`McrReactionWindow` accepts only alternatives issued by the rules engine:
it accepts only exact legal alternatives issued by the rules engine, collects one
immutable decision per opponent, and applies Green Book sections 3.6.6--3.6.8 and
3.7.1--3.7.2.4. Hu beats every meld; when several players call hu, only the player
nearest after the discarder wins; pung/kong beats chow; and only the next player
may chow.

`McrRoundState` now joins those primitives into one validation boundary. It retains
exact hands, exposed flowers, physical melds with their source discard, rivers,
the wall, current seat, last draw, reaction window, terminal result, and a monotonic
revision. Construction rejects a missing or duplicated identity anywhere in the
144-tile state and verifies live structural hand counts. `McrRoundOutcome` also
recomputes settlement, so an adapter cannot pair a genuine evaluation with forged
score deltas. Action transitions are the next migration step.

`McrRoundEngine` now provides the first atomic action slice: exact discard,
generated discard reactions, one-shot reaction timeout, chow/pung/direct-kong
formation, recursive flower replacement, ordinary and back-wall draws, discard
win evaluation, and exhaustive termination. Invalid or stale actions return the
unchanged input object with a typed violation. Seats with no legal reaction are
passed internally, so an unclaimable discard does not allocate an idle timer or
mailbox round trip.

The state also records the dealer's final acquisition during the deal, including
whether an initial flower replacement supplied it. This supports the Green Book
3.5.7 case where East declares hu before the first discard. Self-draw settlement
and exact concealed-kong transitions are now implemented; a kong replacement that
first yields a flower is deliberately classified as `FLOWER_REPLACEMENT`, not
`KONG_REPLACEMENT`, matching the scoring exception for Out With Replacement Tile.
Added kong is now a two-phase transition. The fourth tile remains in the
declarer's exact hand while a hu-only `ADDED_KONG` window is open. If all seats
pass, the original pung upgrades and a back-wall replacement is drawn. If hu wins
priority, the pung remains unchanged and the fourth instance moves to a dedicated
`McrRobbedKongClaim`; it cannot remain duplicated in the declarer's hand. Scoring
sets `ROBBING_KONG`, and settlement treats the kong declarer as the discarder.

`McrRoundSnapshotCodec` now encodes every exact zone, wall order, reaction option
and decision, draw source, scored outcome, and robbed-kong placement into a bounded
schema-1 binary payload with SHA-256. Restore reconstructs typed objects, reruns all
round invariants, and requires byte-for-byte canonical re-encoding. Payload arrays
are defensive copies and Java native serialization is not used.

`McrViewProjector` creates a 144-node public view and a seat-authorized private
overlay. Per-hand Sattolo projection IDs are deterministic but never equal the raw
physical ID and change with the hand salt. Wall and concealed nodes carry no face;
the private overlay reveals only its viewer's hand. At a win, only the winner's
concealed tiles become public, while non-winners remain hidden. Live concealed
kongs stay face-down and are revealed only after the hand ends.

`McrLegalActionGenerator` exposes exact discards, self draw, concealed/added kongs,
issued reaction alternatives and pass using those opaque projection IDs. A
responder disappears from the action list after committing once. Reaction timeout
is a separate actor-owned command and is never presented as a player action.

`McrMatchEngine` implements the Green Book complete-game rotation independently of
platform players: four hands per prevalent wind and East/South/West/North rounds,
for 16 hands total. Dealer passes right after every hand regardless of winner, while
cumulative zero-sum payments remain keyed by each player's fixed initial seat. A
tournament deadline can end the session only between hands; no rule code reads a
wall clock.

`McrMatchSnapshotCodec` wraps the exact current-hand payload with match revision,
rotation, fixed-player cumulative score and every completed result. The schema is
bounded, canonical and SHA-256 authenticated; restore rebuilds typed outcomes and
payments and reruns both hand and complete-game invariants.

`McrRulePackProvider` supplies the parent-loaded MahjongPaper SPI through
`ServiceLoader`: deterministic match creation, player-authorized opaque actions,
public/private views, canonical events and identity-bound snapshots. The rule-pack
JAR keeps `mahjong-rule-spi` compile-only, carries its static descriptor and MCR
tile manifest, and is verified against SPI/TCK 1.5.0 built from pinned
MahjongEngine commit `d4150d358d171c5ea9171a806a36b62a35930654` in GitHub Actions.
It is an installable candidate, not a signed or independently certified release.

The provider also owns MCR bot/trustee decisions: it wins immediately, safely passes
unwanted calls, and discards the least connected legal tile. The core supplies already-computed
legal actions and never interprets MCR action names.

## Representation and performance

- Standard tile kinds have stable indexes `0..33`; flowers are `34..41`.
- Physical IDs are stable in `0..143`; the wall stores compact IDs and shares its
  immutable order between successor states.
- `TileCounts` stores a defensively copied `byte[34]` value.
- All legal standard decompositions are enumerated. Associated-combination scoring
  uses a compact disjoint-set search to enforce the non-identical/non-reuse rules.
- The evaluator is stateless and thread-safe. Hot paths use loops and primitive
  arrays, not JSON, reflection, streams, or string conversion.
- The library intentionally has no built-in cache. A caller cache must key on the
  complete immutable input, never on an unverified hash alone.

See `BENCHMARK.md` for the dependency-free smoke benchmark and
`RULE_COVERAGE.md` for implementation and validation status.

## Mixed concealed and melded kong ruling

The historical native engine used an extra `MINGANGANG` key. It is not one of the
81 official `Fan` names and the vendored revision incorrectly assigned it five
points. The Green Book's entry for Two Melded Kongs explicitly assigns six points
to one concealed plus one melded kong. Java therefore exposes this six-point case
through `InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG`, keeping the public
official-fan enum at exactly 81 entries without preserving the native defect.

## License

MIT. See `LICENSE` and `NOTICE`. The scoring algorithm is an adaptation and rewrite
of MIT-licensed GB-Mahjong code; the upstream copyright and license are preserved.
