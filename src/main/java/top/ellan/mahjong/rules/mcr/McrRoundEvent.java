package top.ellan.mahjong.rules.mcr;

/** Typed facts emitted by accepted hand transitions. */
public sealed interface McrRoundEvent
        permits McrRoundEvent.TileDiscarded,
                McrRoundEvent.ReactionRecorded,
                McrRoundEvent.DiscardUnclaimed,
                McrRoundEvent.AddedKongProposed,
                McrRoundEvent.MeldFormed,
                McrRoundEvent.TileDrawn,
                McrRoundEvent.FlowerExposed,
                McrRoundEvent.RoundWon,
                McrRoundEvent.ExhaustiveDraw {

    record TileDiscarded(Wind seat, McrTileInstance tile) implements McrRoundEvent {}

    record ReactionRecorded(Wind seat, McrReactionType type) implements McrRoundEvent {}

    record DiscardUnclaimed(Wind seat, McrTileInstance tile) implements McrRoundEvent {}

    record AddedKongProposed(Wind seat, McrTileInstance tile) implements McrRoundEvent {}

    record MeldFormed(Wind seat, McrPhysicalMeld meld) implements McrRoundEvent {}

    record TileDrawn(Wind seat, McrTileInstance tile, McrDrawSource source) implements McrRoundEvent {}

    record FlowerExposed(Wind seat, McrTileInstance tile) implements McrRoundEvent {}

    record RoundWon(McrRoundOutcome.Win outcome) implements McrRoundEvent {}

    record ExhaustiveDraw() implements McrRoundEvent {}
}
