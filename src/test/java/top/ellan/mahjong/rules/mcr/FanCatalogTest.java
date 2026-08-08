package top.ellan.mahjong.rules.mcr;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FanCatalogTest {
    @Test
    void exposesExactlyTheEightyOneOfficialFans() {
        assertEquals(81, Fan.values().length);
        for (Fan fan : Fan.values()) {
            assertFalse(fan.name().contains("MINGANGANG"));
            assertFalse(fan.name().contains("MIXED_CONCEALED"));
        }
        assertEquals(6, InternalCombination.MIXED_CONCEALED_AND_MELDED_KONG.points());
    }
}
