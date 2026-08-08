package top.ellan.mahjong.rules.mcr;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Test-only reader for the compact notation used by the vendored GB-Mahjong
 * {@code unit_test.cpp}. Production code deliberately does not parse this format.
 */
final class LegacyCppFixtureParser {
    private LegacyCppFixtureParser() {}

    static WinInput win(String fixture) {
        Parsed parsed = parse(fixture, true);
        return new WinInput(TileCounts.of(parsed.concealed()), parsed.melds(), parsed.winningTile(),
                parsed.context());
    }

    static ParsedWait wait(String fixture) {
        Parsed parsed = parse(fixture, false);
        return new ParsedWait(TileCounts.of(parsed.concealed()), parsed.melds());
    }

    private static Parsed parse(String fixture, boolean removeWinningTile) {
        if (fixture == null) throw new IllegalArgumentException("fixture is required");
        String compact = fixture.replace(" ", "");
        String[] sections = compact.split("\\|", -1);
        List<Meld> melds = new ArrayList<>();
        List<Tile> concealed = new ArrayList<>();
        parseTiles(sections[0], melds, concealed);

        Tile winning = null;
        if (removeWinningTile) {
            if (concealed.isEmpty()) throw new IllegalArgumentException("fixture has no winning tile: " + fixture);
            winning = concealed.removeLast();
        }

        String state = sections.length > 1 && !sections[1].isEmpty() ? sections[1] : "EE0000";
        if (state.length() != 6) throw new IllegalArgumentException("invalid fixture state: " + state);
        Wind round = wind(state.charAt(0));
        Wind seat = wind(state.charAt(1));
        boolean selfDraw = bit(state.charAt(2));
        EnumSet<WinFlag> flags = EnumSet.noneOf(WinFlag.class);
        if (bit(state.charAt(3))) flags.add(WinFlag.LAST_OF_KIND);
        if (bit(state.charAt(4))) flags.add(WinFlag.LAST_TILE);
        if (bit(state.charAt(5))) flags.add(selfDraw ? WinFlag.AFTER_KONG : WinFlag.ROBBING_KONG);
        List<Tile> flowers = sections.length > 2 ? flowers(sections[2]) : List.of();
        WinContext context = new WinContext(seat, round,
                selfDraw ? WinMethod.SELF_DRAW : WinMethod.DISCARD, flags, flowers);
        return new Parsed(List.copyOf(concealed), List.copyOf(melds), winning, context);
    }

    private static void parseTiles(String value, List<Meld> melds, List<Tile> concealed) {
        int at = 0;
        while (at < value.length()) {
            char current = value.charAt(at);
            if (current == '[') {
                int end = value.indexOf(']', at + 1);
                if (end < 0) throw new IllegalArgumentException("unclosed meld: " + value);
                parseMeld(value.substring(at + 1, end), melds);
                at = end + 1;
            } else if (Character.isDigit(current)) {
                int digitsEnd = at;
                while (digitsEnd < value.length() && Character.isDigit(value.charAt(digitsEnd))) digitsEnd++;
                if (digitsEnd == value.length()) throw new IllegalArgumentException("numbered tiles lack suit: " + value);
                char suit = value.charAt(digitsEnd);
                for (int i = at; i < digitsEnd; i++) concealed.add(numbered(value.charAt(i), suit));
                at = digitsEnd + 1;
            } else {
                concealed.add(honor(current));
                at++;
            }
        }
    }

    private static void parseMeld(String value, List<Meld> melds) {
        int comma = value.indexOf(',');
        String tiles = comma < 0 ? value : value.substring(0, comma);
        boolean explicitOffer = comma >= 0;
        List<Tile> parsed = new ArrayList<>(4);
        char last = tiles.charAt(tiles.length() - 1);
        if (last == 'm' || last == 's' || last == 'p') {
            for (int i = 0; i < tiles.length() - 1; i++) parsed.add(numbered(tiles.charAt(i), last));
        } else {
            for (int i = 0; i < tiles.length(); i++) parsed.add(honor(tiles.charAt(i)));
        }
        if (parsed.size() == 3 && parsed.get(0) == parsed.get(1) && parsed.get(1) == parsed.get(2)) {
            melds.add(Meld.pung(parsed.get(0)));
        } else if (parsed.size() == 3 && parsed.get(0).isNumbered()
                && parsed.get(0).suit() == parsed.get(1).suit()
                && parsed.get(1).suit() == parsed.get(2).suit()
                && parsed.get(0).rank() + 1 == parsed.get(1).rank()
                && parsed.get(1).rank() + 1 == parsed.get(2).rank()) {
            melds.add(Meld.chow(parsed.get(1)));
        } else if (parsed.size() == 4 && parsed.stream().allMatch(tile -> tile == parsed.get(0))) {
            melds.add(explicitOffer ? Meld.openKong(parsed.get(0)) : Meld.concealedKong(parsed.get(0)));
        } else {
            throw new IllegalArgumentException("unsupported fixture meld: " + value);
        }
    }

    private static Tile numbered(char rankCharacter, char suit) {
        int rank = rankCharacter - '0';
        if (rank < 1 || rank > 9) throw new IllegalArgumentException("invalid rank: " + rankCharacter);
        int base = switch (suit) {
            case 'm' -> 0;
            case 's' -> 9;
            case 'p' -> 18;
            default -> throw new IllegalArgumentException("invalid suit: " + suit);
        };
        return Tile.standard(base + rank - 1);
    }

    private static Tile honor(char value) {
        return switch (value) {
            case 'E' -> Tile.EAST;
            case 'S' -> Tile.SOUTH;
            case 'W' -> Tile.WEST;
            case 'N' -> Tile.NORTH;
            case 'C' -> Tile.RED_DRAGON;
            case 'F' -> Tile.GREEN_DRAGON;
            case 'P' -> Tile.WHITE_DRAGON;
            default -> throw new IllegalArgumentException("invalid honor: " + value);
        };
    }

    private static Wind wind(char value) {
        return switch (value) {
            case 'E' -> Wind.EAST;
            case 'S' -> Wind.SOUTH;
            case 'W' -> Wind.WEST;
            case 'N' -> Wind.NORTH;
            default -> throw new IllegalArgumentException("invalid wind: " + value);
        };
    }

    private static boolean bit(char value) {
        if (value == '0') return false;
        if (value == '1') return true;
        throw new IllegalArgumentException("expected binary fixture flag, got: " + value);
    }

    private static List<Tile> flowers(String value) {
        if (value.isEmpty() || value.equals("0")) return List.of();
        int count;
        if (value.length() == 1 && Character.isDigit(value.charAt(0))) {
            count = value.charAt(0) - '0';
        } else {
            count = value.length();
        }
        if (count < 0 || count > 8) throw new IllegalArgumentException("invalid flower count: " + value);
        List<Tile> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) result.add(Tile.values()[34 + i]);
        return List.copyOf(result);
    }

    private record Parsed(List<Tile> concealed, List<Meld> melds, Tile winningTile, WinContext context) {}

    record ParsedWait(TileCounts concealed, List<Meld> melds) {
        ParsedWait {
            melds = List.copyOf(melds);
        }
    }
}
