package top.ellan.mahjong.rules.mcr;

import java.util.Objects;
import java.util.regex.Pattern;

/** A deterministic private presentation key paired with its exact round command. */
public record McrLegalAction(String key, McrRoundAction action) {
    private static final Pattern VALID_KEY = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,95}");

    public McrLegalAction {
        key = Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        if (!VALID_KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("invalid MCR legal-action key: " + key);
        }
    }
}
