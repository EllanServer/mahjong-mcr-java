package top.ellan.mahjong.rules.mcr;

/** Stable complete-game rejection reasons. */
public enum McrMatchViolation {
    NULL_INPUT,
    WRONG_MATCH_PHASE,
    MATCH_ENDED,
    INTERNAL_STATE_MISMATCH
}
