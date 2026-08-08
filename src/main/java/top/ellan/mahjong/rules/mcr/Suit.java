package top.ellan.mahjong.rules.mcr;

public enum Suit {
    CHARACTERS, BAMBOO, DOTS, WIND, DRAGON, FLOWER;

    public boolean isNumbered() {
        return this == CHARACTERS || this == BAMBOO || this == DOTS;
    }
}
