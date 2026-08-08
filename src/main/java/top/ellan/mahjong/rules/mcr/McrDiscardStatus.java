package top.ellan.mahjong.rules.mcr;

/** Final disposition of an exact tile placed into a player's river. */
public enum McrDiscardStatus {
    PENDING,
    UNCLAIMED,
    MELD_CLAIMED,
    WIN_CLAIMED
}
