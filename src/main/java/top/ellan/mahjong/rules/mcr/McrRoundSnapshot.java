package top.ellan.mahjong.rules.mcr;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Versioned bounded binary snapshot of one exact MCR hand. */
public record McrRoundSnapshot(int schemaVersion, long sequence, byte[] payload, String sha256) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PAYLOAD_BYTES = 65_536;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    public McrRoundSnapshot {
        if (schemaVersion < 1 || sequence < 0) {
            throw new IllegalArgumentException("invalid MCR snapshot schema or sequence");
        }
        payload = Objects.requireNonNull(payload, "payload").clone();
        if (payload.length == 0 || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("MCR snapshot payload has invalid size");
        }
        sha256 = Objects.requireNonNull(sha256, "sha256").toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("invalid MCR snapshot SHA-256");
        }
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof McrRoundSnapshot snapshot
                        && schemaVersion == snapshot.schemaVersion
                        && sequence == snapshot.sequence
                        && sha256.equals(snapshot.sha256)
                        && Arrays.equals(payload, snapshot.payload);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(schemaVersion);
        result = 31 * result + Long.hashCode(sequence);
        result = 31 * result + Arrays.hashCode(payload);
        return 31 * result + sha256.hashCode();
    }
}
