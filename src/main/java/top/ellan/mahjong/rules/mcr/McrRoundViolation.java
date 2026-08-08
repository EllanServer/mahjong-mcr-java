package top.ellan.mahjong.rules.mcr;

/** Stable rejection reasons returned without changing the original state. */
public enum McrRoundViolation {
    NULL_INPUT,
    ROUND_ENDED,
    WRONG_PHASE,
    NOT_CURRENT_SEAT,
    TILE_NOT_OWNED,
    WIN_NOT_LEGAL,
    KONG_NOT_LEGAL,
    ADDED_KONG_NOT_LEGAL,
    REACTION_NOT_LEGAL,
    REACTION_ALREADY_RECORDED,
    INTERNAL_STATE_MISMATCH
}
