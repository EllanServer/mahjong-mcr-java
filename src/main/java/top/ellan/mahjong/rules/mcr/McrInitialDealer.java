package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.EnumMap;

/** Executes the standard MCR initial deal and mandatory initial flower replacement. */
public final class McrInitialDealer {
    private McrInitialDealer() {}

    public static McrInitialDeal deal(long seed) {
        return deal(McrWall.shuffled(seed));
    }

    public static McrInitialDeal deal(McrWall sourceWall) {
        if (sourceWall == null || sourceWall.remaining() != McrTileInstance.PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("initial deal requires a complete 144-tile wall");
        }

        EnumMap<Wind, ArrayList<McrTileInstance>> concealed = emptySeatLists();
        EnumMap<Wind, ArrayList<McrTileInstance>> flowers = emptySeatLists();
        McrWall wall = sourceWall;

        for (int block = 0; block < 3; block++) {
            for (Wind seat : Wind.values()) {
                for (int tile = 0; tile < 4; tile++) {
                    McrWall.Draw draw = wall.drawFront();
                    concealed.get(seat).add(draw.tile());
                    wall = draw.remainingWall();
                }
            }
        }

        ArrayList<McrTileInstance> finalFive = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            McrWall.Draw draw = wall.drawFront();
            finalFive.add(draw.tile());
            wall = draw.remainingWall();
        }
        concealed.get(Wind.EAST).add(finalFive.get(0));
        concealed.get(Wind.EAST).add(finalFive.get(4));
        concealed.get(Wind.SOUTH).add(finalFive.get(1));
        concealed.get(Wind.WEST).add(finalFive.get(2));
        concealed.get(Wind.NORTH).add(finalFive.get(3));

        for (Wind seat : Wind.values()) {
            ArrayList<McrTileInstance> hand = concealed.get(seat);
            int flowerIndex;
            while ((flowerIndex = firstFlower(hand)) >= 0) {
                flowers.get(seat).add(hand.remove(flowerIndex));
                McrWall.Draw replacement = wall.drawBack();
                hand.add(replacement.tile());
                wall = replacement.remainingWall();
            }
        }

        return new McrInitialDeal(
                copySeatLists(concealed),
                copySeatLists(flowers),
                wall);
    }

    private static EnumMap<Wind, ArrayList<McrTileInstance>> emptySeatLists() {
        EnumMap<Wind, ArrayList<McrTileInstance>> result = new EnumMap<>(Wind.class);
        for (Wind wind : Wind.values()) result.put(wind, new ArrayList<>());
        return result;
    }

    private static EnumMap<Wind, java.util.List<McrTileInstance>> copySeatLists(
            EnumMap<Wind, ArrayList<McrTileInstance>> source) {
        EnumMap<Wind, java.util.List<McrTileInstance>> result = new EnumMap<>(Wind.class);
        for (Wind wind : Wind.values()) result.put(wind, java.util.List.copyOf(source.get(wind)));
        return result;
    }

    private static int firstFlower(ArrayList<McrTileInstance> hand) {
        for (int i = 0; i < hand.size(); i++) {
            if (hand.get(i).kind().isFlower()) return i;
        }
        return -1;
    }
}
