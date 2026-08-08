package top.ellan.mahjong.rules.mcr;

/** Fixed upper hand count; a timed session may end earlier only at a hand boundary. */
public record McrMatchConfig(int handLimit) {
    public static final int STANDARD_HAND_LIMIT = 16;
    public static final int MAX_HAND_LIMIT = 16;

    public McrMatchConfig {
        if (handLimit < 1 || handLimit > MAX_HAND_LIMIT) {
            throw new IllegalArgumentException("handLimit must be in [1, 16]");
        }
    }

    public static McrMatchConfig standard() {
        return new McrMatchConfig(STANDARD_HAND_LIMIT);
    }
}
