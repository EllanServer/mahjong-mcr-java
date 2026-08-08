# mahjong-mcr-java

Pure Java 21 implementation of Mahjong Competition Rules (MCR) hand structure,
the 81 official scoring elements, waits, the eight-point minimum, and settlement.
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

## Representation and performance

- Standard tile kinds have stable indexes `0..33`; flowers are `34..41`.
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
