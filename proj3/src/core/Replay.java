package core;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * The single 3C seam (ADR-0004): {@code replay(session)} rebuilds the world a
 * session describes and returns a {@link GameSnapshot}. Every input source — the
 * headless 3C tests, the future HTTP adapter, and (through it) the browser —
 * drives the game through exactly this method, so their replay semantics can
 * never diverge. The legacy {@link AutograderBuddy#getWorldFromInput} path is
 * untouched and has no goal/size semantics.
 */
public final class Replay {

    private Replay() {
    }

    /** The one public behaviour of the 3C world: session in, snapshot out. */
    public static GameSnapshot replay(Session session) {
        boolean load = session != null && "load".equalsIgnoreCase(session.kind);
        RuntimeWorld world;
        long seed;
        boolean saveMissing = false;
        if (load) {
            world = RuntimeWorld.load(true); // sized rebuild + goal re-stamped (ADR-0005)
            seed = world.seed();
            saveMissing = isEmpty(world.snapshot());
        } else {
            WorldSize size = session == null || session.size == null ? WorldSize.medium : session.size;
            seed = session == null ? 0L : session.seed;
            world = RuntimeWorld.start(seed, size.width(), size.height());
        }
        String keys = session == null || session.keys == null ? "" : session.keys;
        world.type(keys);

        TETile[][] grid = world.snapshot();
        int width = grid.length;
        int height = grid[0].length;
        int[][] rows = new int[height][width];
        Map<Integer, TETile> seen = new HashMap<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TETile tile = grid[x][y];
                int id = tile.id();
                rows[y][x] = id;
                seen.putIfAbsent(id, tile);
            }
        }
        // The avatar/goal are world entities, not grid tiles: once the avatar steps
        // onto the goal its AVATAR tile covers the GOAL tile, yet the goal must stay
        // reported (and stay in the legend) so the client keeps drawing the objective.
        int[] avatar = world.avatar();
        int[] goal = world.goal();
        boolean won = world.won();
        if (goal != null) {
            seen.putIfAbsent(Tileset.GOAL.id(), Tileset.GOAL);
        }

        GameSnapshot.Legend[] legend = legendFor(seen.values());
        return new GameSnapshot(width, height, seed, won, saveMissing, avatar, goal, rows, legend);
    }

    /** True when the loaded world has no floor and no avatar — the "no save" result. */
    private static boolean isEmpty(TETile[][] grid) {
        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[0].length; y++) {
                int id = grid[x][y].id();
                if (id == Tileset.FLOOR.id() || id == Tileset.AVATAR.id()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The palette for every tile id present, sorted by id for stable output. */
    private static GameSnapshot.Legend[] legendFor(Iterable<TETile> tiles) {
        List<GameSnapshot.Legend> out = new ArrayList<>();
        for (TETile t : tiles) {
            out.add(new GameSnapshot.Legend(t.id(), t.description(),
                    hex(t.textColor()), hex(t.backgroundColor())));
        }
        out.sort(Comparator.comparingInt(l -> l.id));
        return out.toArray(new GameSnapshot.Legend[0]);
    }

    private static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
