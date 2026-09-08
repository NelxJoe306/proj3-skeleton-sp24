package core;

import edu.princeton.cs.algs4.StdDraw;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import tileengine.TERenderer;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Interactive entry point: opens a window, shows the world, and lets you drive the
 * avatar with the keyboard exactly as the autograder's {@code AutograderBuddy}
 * headless seam would — this class only draws and forwards keys, it never decides
 * behaviour.
 *
 * <p>Starting states:
 * <ul>
 *   <li>{@code Main} — a fresh world on the default seed 42 ({@code N42S});</li>
 *   <li>{@code Main N<digits>S} — a fresh world on that seed (extra trailing keys
 *       are allowed and put the avatar where they would leave it);</li>
 *   <li>{@code Main L} — continue the world saved by a previous {@code :Q}
 *       (an empty void world if no save exists).</li>
 * </ul>
 *
 * <p>While the window is focused:
 * <ul>
 *   <li>{@code W/A/S/D} (upper or lower case) move one tile — up/left/down/right;</li>
 *   <li>{@code :Q} or {@code :q} saves the session to {@code save.txt} and quits;</li>
 *   <li>{@code q} or {@code Q} (not completing a {@code :Q}) quits without saving;</li>
 *   <li>{@code Esc} or the window close button (X) quits without saving.</li>
 * </ul>
 *
 * <p>State is never held in this class: every frame re-derives the world from the
 * cumulative key string through {@link AutograderBuddy#getWorldFromInput}, the one
 * headless seam. Rendering the world for a seed is cheap, so live keying stays
 * responsive, and Main can never drift from what the autograder would compute.
 *
 * <p>Not part of the autograder path. StdDraw's window grabs keyboard focus when it
 * opens and Windows may attach the default (e.g. Chinese) input method to it, so this
 * harness tries to switch that off and — crucially — never exits on a stray key; only
 * {@code :Q}/{@code :q}, {@code Esc}, or closing the window end the process.
 */
public class Main {
    private static final long DEFAULT_SEED = 42L;
    private static final int KEY_POLL_MS = 30;
    /** Screen margin (px) kept free for the taskbar/title bar so the map isn't cut off. */
    private static final int SCREEN_MARGIN = 90;
    /** Smallest usable tile size, as a floor even on very small windows. */
    private static final int MIN_TILE_SIZE = 4;

    public static void main(String[] args) {
        String start = args.length > 0 ? args[0] : ("n" + DEFAULT_SEED + "s");
        // Validate the starting string through the engine itself (throws IAE on a bad
        // seed prefix), so Main needs no parsing of its own.
        TETile[][] first;
        try {
            first = AutograderBuddy.getWorldFromInput(start);
        } catch (IllegalArgumentException e) {
            System.err.println("Bad starting input \"" + start + "\": " + e.getMessage());
            System.err.println("Expected N<digits>S (optionally followed by moves) for a new "
                    + "world, or L to load the last :Q save.");
            return;
        }

        TERenderer ter = new TERenderer();
        ter.setTileSize(fitTileSize(first.length, first[0].length));
        ter.initialize(first.length, first[0].length);

        // Best effort: don't let the focused window pull in a Chinese/IME composition
        // that would swallow W/A/S/D. Must run after the window exists; harmless if it
        // fails (keys still work when no IME engages).
        suppressInputMethodOnStdDrawWindow();

        session(start, ter);
        System.exit(0); // reached only on an explicit Esc / :Q quit; window X already exits
    }

    /**
     * Runs the live session: shows the world for {@code start}, then forwards every
     * typed key into the cumulative input string and re-renders. Returns on an
     * explicit quit ({@code :Q}/{@code :q} saves first, {@code Esc} does not).
     */
    private static void session(String start, TERenderer ter) {
        StringBuilder buf = new StringBuilder(start);
        TETile[][] g = AutograderBuddy.getWorldFromInput(buf.toString());
        ter.renderFrame(g);
        int[] pos = avatarPosition(g);
        if (pos != null) {
            System.out.println("World ready. Your avatar is the '@' at tile ("
                    + pos[0] + ", " + pos[1] + ") — x right, y up.");
        } else {
            System.out.println("Empty world (no save to load). Nothing to move yet.");
        }
        System.out.println("Click the map first so it has focus. W/A/S/D move ·");
        System.out.println("q quits · :Q saves then quits · Esc or window X also quits.");

        while (true) {
            boolean moved = false;
            while (StdDraw.hasNextKeyTyped()) {
                char c = StdDraw.nextKeyTyped();
                boolean colonQ = (c == 'q' || c == 'Q')
                        && buf.length() > 0
                        && buf.charAt(buf.length() - 1) == ':';
                if (colonQ) {
                    // The buffer now ends in ':Q': feed the whole string so the engine
                    // performs its real save side-effect, then leave.
                    buf.append(c);
                    AutograderBuddy.getWorldFromInput(buf.toString());
                    System.out.println("Saved to " + RuntimeWorld.SAVE_FILE_NAME + ". Quitting.");
                    return;
                }
                if (c == 'q' || c == 'Q') {
                    // A bare q, not completing ':Q': quit without saving. This harness
                    // only, so it never touches the engine's save semantics.
                    System.out.println("Quit without saving. (Type :Q to save first.)");
                    return;
                }
                buf.append(c);
                // Feed the full buffer so the engine is the single source of truth.
                AutograderBuddy.getWorldFromInput(buf.toString());
                moved = true;
            }
            if (moved) {
                ter.renderFrame(AutograderBuddy.getWorldFromInput(buf.toString()));
            }
            if (StdDraw.isKeyPressed(KeyEvent.VK_ESCAPE)) {
                System.out.println("Quit without saving.");
                return;
            }
            StdDraw.pause(KEY_POLL_MS);
        }
    }

    /** Tile coordinates of the single '@' in the grid, or null if absent. */
    private static int[] avatarPosition(TETile[][] g) {
        int[] pos = null;
        int count = 0;
        for (int x = 0; x < g.length; x++) {
            for (int y = 0; y < g[0].length; y++) {
                if (g[x][y] != null && g[x][y].character() == Tileset.AVATAR.character()) {
                    count++;
                    pos = new int[]{x, y};
                }
            }
        }
        return count == 1 ? pos : null;
    }

    /**
     * Pixel side length for one tile such that a {@code cols x rows} grid fits the
     * usable screen area (current screen size minus a margin). Falls back to the
     * renderer default if the screen can't be queried.
     */
    private static int fitTileSize(int cols, int rows) {
        try {
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int availW = Math.max(200, screen.width - SCREEN_MARGIN);
            int availH = Math.max(200, screen.height - SCREEN_MARGIN);
            int px = Math.min(availW / cols, availH / rows);
            return Math.max(MIN_TILE_SIZE, px);
        } catch (Throwable t) {
            return TERenderer.DEFAULT_TILE_SIZE;
        }
    }

    /**
     * Best-effort attempt to stop Windows from attaching an input method (e.g. the
     * Chinese IME) to the StdDraw window. StdDraw keeps its {@code JFrame} private,
     * so this reaches it via reflection and disables input methods on the frame and
     * every descendant. Purely cosmetic hardening — if it fails the window still works,
     * the keys are just read as typed characters.
     */
    private static void suppressInputMethodOnStdDrawWindow() {
        try {
            java.lang.reflect.Field field = StdDraw.class.getDeclaredField("frame");
            field.setAccessible(true);
            java.awt.Frame[] target = new java.awt.Frame[1];
            long deadline = System.currentTimeMillis() + 1500;
            while (target[0] == null && System.currentTimeMillis() < deadline) {
                Object value = field.get(null);
                if (value instanceof java.awt.Frame frame) {
                    target[0] = frame;
                } else {
                    StdDraw.pause(40); // StdDraw builds its frame lazily
                }
            }
            if (target[0] == null) {
                return;
            }
            java.awt.Frame frame = target[0];
            javax.swing.SwingUtilities.invokeLater(() -> disableInputMethods(frame));
        } catch (Throwable t) {
            // Reflection unavailable or StdDraw internals changed: no-op is fine.
        }
    }

    /** Recursively turns off input-method support on a component tree. */
    private static void disableInputMethods(java.awt.Component component) {
        try {
            component.enableInputMethods(false);
        } catch (Throwable t) {
            // ignore: best effort
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                disableInputMethods(child);
            }
        }
    }
}
