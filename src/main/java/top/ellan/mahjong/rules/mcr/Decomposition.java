package top.ellan.mahjong.rules.mcr;

import java.util.List;

record Decomposition(List<Group> groups, int knittedPattern, boolean winningInKnitted) {
    Decomposition {
        groups = List.copyOf(groups);
    }

    boolean hasKnittedStraight() { return knittedPattern >= 0; }
}
