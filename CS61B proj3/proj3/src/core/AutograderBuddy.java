package core;

import tileengine.TETile;
import tileengine.Tileset;

public class AutograderBuddy {

    /** A leading seed prefix: `N<digits>S` (case-insensitive). Characters after the
     *  trailing S are the command stream (3B), not part of the seed. */
    private static final java.util.regex.Pattern SEED_FORM =
            java.util.regex.Pattern.compile("^[nN]([0-9]+)[sS].*$");

    /**
     * Simulates a game, but doesn't render anything or call any StdDraw
     * methods. Instead, returns the world that would result if the input string
     * had been typed on the keyboard.
     *
     * Recall that strings ending in ":q" should cause the game to quit and
     * save. To "quit" in this method, save the game to a file, then just return
     * the TETile[][]. Do not call System.exit(0) in this method.
     *
     * @param input the input string to feed to your program
     * @return the 2D TETile[][] representing the state of the world
     */
    public static TETile[][] getWorldFromInput(String input) {
        if (input != null && !input.isEmpty()
                && (input.charAt(0) == 'L' || input.charAt(0) == 'l')) {
            // A load string: continue the world stored by a previous ':Q', then run
            // the keys that follow the L. No save present -> the empty world (no error).
            RuntimeWorld world = RuntimeWorld.load();
            world.type(input.substring(1));
            return world.snapshot();
        }
        long seed = parseSeed(input);
        RuntimeWorld world = RuntimeWorld.start(seed);
        world.type(afterSeedPrefix(input));
        return world.snapshot();
    }

    /**
     * The characters after the validated {@code N<digits>S} prefix of {@code input}
     * — the command stream. Requires {@link #parseSeed} to have accepted the string
     * first, so the prefix is guaranteed present and well-formed.
     */
    private static String afterSeedPrefix(String input) {
        int i = 1; // skip leading N / n
        while (i < input.length() && Character.isDigit(input.charAt(i))) {
            i++;
        }
        return input.substring(i + 1); // skip trailing S / s
    }

    /**
     * Extracts the numeric seed from a leading "N<digits>S" prefix of {@code input}.
     *
     * @param input the raw input string
     * @return the seed as a long
     * @throws IllegalArgumentException if no valid seed prefix is present
     */
    static long parseSeed(String input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        java.util.regex.Matcher m = SEED_FORM.matcher(input);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid seed string. Expected N<digits>S "
                    + "(case-insensitive), got: \"" + abbreviate(input) + "\"");
        }
        String digits = m.group(1);
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seed number too large: \"" + digits + "\"");
        }
    }

    private static String abbreviate(String s) {
        if (s.length() <= 20) {
            return s;
        }
        return s.substring(0, 20) + "...";
    }


    /**
     * Used to tell the autograder which tiles are the floor/ground (including
     * any lights/items resting on the ground). Change this
     * method if you add additional tiles.
     */
    public static boolean isGroundTile(TETile t) {
        return t.character() == Tileset.FLOOR.character()
                || t.character() == Tileset.AVATAR.character()
                || t.character() == Tileset.FLOWER.character();
    }

    /**
     * Used to tell the autograder while tiles are the walls/boundaries. Change
     * this method if you add additional tiles.
     */
    public static boolean isBoundaryTile(TETile t) {
        return t.character() == Tileset.WALL.character()
                || t.character() == Tileset.LOCKED_DOOR.character()
                || t.character() == Tileset.UNLOCKED_DOOR.character();
    }
}
