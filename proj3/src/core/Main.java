package core;

import edu.princeton.cs.algs4.StdDraw;
import tileengine.TERenderer;
import tileengine.TETile;

/**
 * Interactive eyeball entry point (Ticket 04): renders the world for a seed so
 * a human can look at it. Not part of the autograder path — {@link
 * AutograderBuddy#getWorldFromInput} stays headless.
 *
 * Usage: {@code Main n<seed>s}  (defaults to a fixed seed if no argument given)
 */
public class Main {
    private static final long DEFAULT_SEED = 42L;

    public static void main(String[] args) {
        String input = args.length > 0 ? args[0] : ("n" + DEFAULT_SEED + "s");
        long seed = AutograderBuddy.parseSeed(input);
        World world = World.generate(seed);
        System.out.println("Rendering world for seed " + seed + " (" + input + ") ...");
        render(world);
    }

    private static void render(World world) {
        TETile[][] tiles = world.tiles();
        TERenderer ter = new TERenderer();
        ter.initialize(world.width(), world.height());
        ter.renderFrame(tiles);
        StdDraw.pause(3000);
        // StdDraw keeps an AWT event thread alive, so exit explicitly — this is a
        // short-lived eyeball harness, not an interactive app.
        System.exit(0);
    }
}
