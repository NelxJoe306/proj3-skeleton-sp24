package core;

import java.util.List;
import tileengine.TETile;
import tileengine.Tileset;
import utils.FileUtils;

/**
 * The runtime world of one play-through: the seed's structure-layer grid plus a
 * live avatar entity, advanced one recognized command at a time. Consumes no
 * runtime RNG (ADR-0003) — the entire state is a pure function of the seed and
 * the command stream, so save/load cannot diverge and {@code :Q} is a pure side
 * effect.
 *
 * <p>This is the replay kernel behind {@link AutograderBuddy#getWorldFromInput}.
 * A new session {@code N<seed>S...} starts one from its seed — the avatar spawns
 * at the first generated room's center — and each following key is one turn. The
 * grid handed to callers is this world's live snapshot: structure layer plus the
 * {@code @} stamped in.
 */
public final class RuntimeWorld {

    /** Fixed save filename in the process CWD, written by {@code :Q} and read by {@code L}. */
    public static final String SAVE_FILE_NAME = "save.txt";

    /** Save format version. Reader treats anything above V1 as "no save". */
    public static final String SAVE_VERSION = "V1";

    private final long seed;
    private final TETile[][] grid;
    private int avatarX = -1;
    private int avatarY = -1;
    private int goalX = -1;
    private int goalY = -1;
    private boolean won = false;

    private RuntimeWorld(long seed, TETile[][] grid) {
        this.seed = seed;
        this.grid = grid;
    }

    /** Builds a fresh runtime world for a seed at the default (MEDIUM) size.
     *  This is the legacy seam entry: it installs <em>no</em> goal, so the frozen
     *  {@link AutograderBuddy#getWorldFromInput} grid never contains a GOAL tile. */
    public static RuntimeWorld start(long seed) {
        return build(seed, World.DEFAULT_WIDTH, World.DEFAULT_HEIGHT, false);
    }

    /**
     * Builds a fresh runtime world for a seed at explicit dimensions (3C size tiers),
     * spawning the avatar in the first generated room and stamping the 3C goal in the
     * last. Replay semantics are identical to {@link #start(long)}; the structure
     * layer is a pure function of (seed, width, height). Only this sized entry —
     * reached exclusively through the 3C {@link Replay} seam — carries the goal.
     */
    public static RuntimeWorld start(long seed, int width, int height) {
        return build(seed, width, height, true);
    }

    private static RuntimeWorld build(long seed, int width, int height, boolean withGoal) {
        WorldGenerator.Layout layout = WorldGenerator.layout(seed, width, height);
        RuntimeWorld world = new RuntimeWorld(seed, layout.tiles);
        world.spawnAtFirstRoomCenter(layout.rooms);
        if (withGoal) {
            world.stampGoal(layout.rooms);
        }
        return world;
    }

    /** The seed this world was built from (a loaded session re-derives structure from it). */
    public long seed() {
        return seed;
    }

    /**
     * Loads a V1 save from {@link #SAVE_FILE_NAME} and returns the world it describes:
     * the seed's structure rebuilt from the seed (never a pasted map), sized by the
     * optional {@code size=WxH} line (absent ⇒ MEDIUM 80×80), with the avatar placed at
     * the recorded position, ready to accept further commands. A missing file, a version
     * above V1, or unreadable/malformed contents all yield an empty world (all NOTHING,
     * no avatar) rather than an error.
     *
     * <p>The no-argument form is the legacy {@code L} seam and installs <em>no</em>
     * goal, keeping its output byte-identical to before. The 3C {@link Replay} seam
     * uses {@link #load(boolean)} with {@code withGoal=true} so a continued game still
     * has its objective (re-derived from seed+size — ADR-0005).
     */
    public static RuntimeWorld load() {
        return load(false);
    }

    /** {@link #load()} with an explicit choice of whether to re-stamp the 3C goal. */
    static RuntimeWorld load(boolean withGoal) {
        if (!FileUtils.fileExists(SAVE_FILE_NAME)) {
            return emptyWorld();
        }
        String content;
        try {
            content = FileUtils.readFile(SAVE_FILE_NAME);
        } catch (RuntimeException e) {
            return emptyWorld();
        }
        String[] lines = content.split("\r?\n");

        String firstMeaningful = null;
        for (String line : lines) {
            if (!line.isBlank()) {
                firstMeaningful = line.trim();
                break;
            }
        }
        if (!SAVE_VERSION.equals(firstMeaningful)) {
            return emptyWorld(); // unknown / future version: no save
        }

        Long seed = null;
        int[] avatar = null;
        int width = World.DEFAULT_WIDTH;
        int height = World.DEFAULT_HEIGHT;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("seed=")) {
                try {
                    seed = Long.parseLong(t.substring("seed=".length()).trim());
                } catch (NumberFormatException e) {
                    // malformed seed: treated as no save below
                }
            } else if (t.startsWith("avatar=")) {
                avatar = parseCoordinate(t.substring("avatar=".length()).trim());
            } else if (t.startsWith("size=")) {
                int[] dims = parseSize(t.substring("size=".length()).trim());
                if (dims != null) {
                    width = dims[0];
                    height = dims[1];
                }
            }
        }
        if (seed == null) {
            return emptyWorld();
        }

        RuntimeWorld world = build(seed, width, height, withGoal);
        if (avatar != null) {
            world.teleport(avatar[0], avatar[1]);
        }
        return world;
    }

    /** Parses {@code WxH} (e.g. {@code 120x120}) into {width, height}, or {@code null} if malformed. */
    private static int[] parseSize(String s) {
        int x = s.indexOf('x');
        if (x < 0) {
            x = s.indexOf('X');
        }
        if (x < 0) {
            return null;
        }
        try {
            int w = Integer.parseInt(s.substring(0, x).trim());
            int h = Integer.parseInt(s.substring(x + 1).trim());
            if (w <= 0 || h <= 0 || w > 1000 || h > 1000) {
                return null; // absurd dims are treated as absent
            }
            return new int[]{w, h};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int[] parseCoordinate(String s) {
        int comma = s.indexOf(',');
        if (comma < 0) {
            return null;
        }
        try {
            int x = Integer.parseInt(s.substring(0, comma).trim());
            int y = Integer.parseInt(s.substring(comma + 1).trim());
            return new int[]{x, y};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Moves the just-spawned avatar to a saved position, if that spot is still ground. */
    private void teleport(int x, int y) {
        if (avatarX < 0 || (x == avatarX && y == avatarY)) {
            return;
        }
        if (!walkable(x, y)) {
            return; // stale coordinates: keep the fresh spawn placement
        }
        grid[avatarX][avatarY] = Tileset.FLOOR;
        avatarX = x;
        avatarY = y;
        grid[avatarX][avatarY] = Tileset.AVATAR;
        if (avatarX == goalX && avatarY == goalY) {
            won = true; // a loaded position on the goal is a finished game
        }
    }

    /** An 80×80 world of NOTHING with no avatar — the "no save" result. */
    private static RuntimeWorld emptyWorld() {
        TETile[][] tiles = new TETile[World.DEFAULT_WIDTH][World.DEFAULT_HEIGHT];
        for (int x = 0; x < tiles.length; x++) {
            for (int y = 0; y < tiles[0].length; y++) {
                tiles[x][y] = Tileset.NOTHING;
            }
        }
        return new RuntimeWorld(0L, tiles);
    }

    /** The current {@code @} position, or {@code null} if no avatar has been placed. */
    int[] avatar() {
        return avatarX < 0 ? null : new int[]{avatarX, avatarY};
    }

    /** The stamped goal's position, or {@code null} on worlds without a goal (legacy path). */
    int[] goal() {
        return goalX < 0 ? null : new int[]{goalX, goalY};
    }

    /** True once the avatar has stepped onto the goal; movement is frozen from then on. */
    boolean won() {
        return won;
    }

    /** The world snapshot as callers see it: structure + live avatar. */
    public TETile[][] snapshot() {
        return grid;
    }

    /**
     * Feeds the command stream's post-prefix characters to the world, one key per
     * turn. {@code :Q}/{@code :q} saves the world (a pure side effect — the returned
     * grid is unchanged) and truncates everything that follows. A colon not followed
     * by q does nothing on its own and the next character is still processed. The
     * per-turn dispatch switch is the single place new letter semantics are added;
     * unrecognized characters are silently ignored.
     */
    public void type(String keys) {
        for (int i = 0; i < keys.length(); i++) {
            char c = keys.charAt(i);
            if (c == ':') {
                if (i + 1 < keys.length()
                        && (keys.charAt(i + 1) == 'q' || keys.charAt(i + 1) == 'Q')) {
                    save();
                    return; // quit: truncate every character after :q
                }
                continue; // ':' with nothing/non-q after: does nothing, next key still acts
            }
            step(c);
        }
    }

    /**
     * Writes the current state as a V1 save file (a pure side effect). The header is
     * always {@code V1}; a {@code size=WxH} line is added only when the world is not
     * the MEDIUM 80×80 default, so 80×80 files are byte-identical to the 3B era. The
     * goal never persists — each load re-derives it from seed+size (ADR-0005).
     */
    private void save() {
        StringBuilder sb = new StringBuilder();
        sb.append(SAVE_VERSION).append('\n');
        if (grid.length != World.DEFAULT_WIDTH || grid[0].length != World.DEFAULT_HEIGHT) {
            sb.append("size=").append(grid.length).append('x').append(grid[0].length).append('\n');
        }
        sb.append("seed=").append(seed).append('\n');
        sb.append("avatar=").append(avatarX).append(',').append(avatarY).append('\n');
        FileUtils.writeFile(SAVE_FILE_NAME, sb.toString());
    }

    /** One turn for one key. Every recognized key maps to exactly one action. */
    private void step(char key) {
        if (won) {
            return; // the avatar reached the goal: movement keys are frozen (':Q' still saves)
        }
        switch (key) {
            case 'w':
            case 'W':
                tryMove(0, +1);
                break;
            case 'a':
            case 'A':
                tryMove(-1, 0);
                break;
            case 's':
            case 'S':
                tryMove(0, -1);
                break;
            case 'd':
            case 'D':
                tryMove(+1, 0);
                break;
            default:
                // Unknown character: silently ignored (grammar tolerance), no turn taken.
                break;
        }
    }

    /**
     * Moves the avatar by ({@code dx}, {@code dy}) grid deltas (screen up = array
     * +y, so W is +y). A move only lands on ground; anything else silently blocks —
     * the avatar stays put and no error is raised, and later keys still act.
     */
    private void tryMove(int dx, int dy) {
        if (avatarX < 0) {
            return; // no avatar placed yet
        }
        int nx = avatarX + dx;
        int ny = avatarY + dy;
        if (!walkable(nx, ny)) {
            return; // blocked by a wall / void / out-of-bounds: silently stay
        }
        grid[avatarX][avatarY] = Tileset.FLOOR; // restore the footprint
        avatarX = nx;
        avatarY = ny;
        grid[avatarX][avatarY] = Tileset.AVATAR;
        if (avatarX == goalX && avatarY == goalY) {
            won = true; // stepping onto the goal finishes the game
        }
    }

    private void spawnAtFirstRoomCenter(List<WorldGenerator.Rect> rooms) {
        if (rooms.isEmpty()) {
            return;
        }
        WorldGenerator.Rect room = rooms.get(0);
        int cx = room.x + room.w / 2;
        int cy = room.y + room.h / 2;
        if (walkable(cx, cy)) {
            avatarX = cx;
            avatarY = cy;
            grid[cx][cy] = Tileset.AVATAR;
        }
    }

    /**
     * Stamps the GOAL entity at the most recently generated room's centre. The spot is
     * a pure, deterministic function of (seed, size) — the goal is a runtime entity,
     * never part of the structure layer and never persisted (it re-derives on load).
     * When the centre is the avatar's spawn or not a bare FLOOR (e.g. a single room
     * whose centre the avatar already covers), it relocates to the nearest bare FLOOR
     * by expanding square rings, so the goal always ends up a ground tile distinct
     * from the avatar.
     */
    private void stampGoal(List<WorldGenerator.Rect> rooms) {
        if (rooms.isEmpty()) {
            return;
        }
        WorldGenerator.Rect room = rooms.get(rooms.size() - 1);
        int cx = room.x + room.w / 2;
        int cy = room.y + room.h / 2;
        if (!isBareFloor(cx, cy)) {
            int[] near = nearestBareFloor(cx, cy);
            if (near == null) {
                return; // no ground anywhere: leave the world goal-less (never with real layouts)
            }
            cx = near[0];
            cy = near[1];
        }
        goalX = cx;
        goalY = cy;
        grid[cx][cy] = Tileset.GOAL;
    }

    /** True if (x, y) is in-bounds and an untouched FLOOR cell (no entity over it). */
    private boolean isBareFloor(int x, int y) {
        if (x < 0 || y < 0 || x >= grid.length || y >= grid[0].length) {
            return false;
        }
        return grid[x][y].id() == Tileset.FLOOR.id();
    }

    /**
     * The nearest bare FLOOR to (cx, cy), searched as expanding square rings in a fixed
     * row-major order — a deterministic relocation for the goal, independent of any
     * runtime RNG (ADR-0003). The avatar's own cell is not bare FLOOR, so it is skipped.
     */
    private int[] nearestBareFloor(int cx, int cy) {
        int maxR = Math.max(grid.length, grid[0].length);
        for (int r = 0; r <= maxR; r++) {
            for (int y = cy - r; y <= cy + r; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(y - cy)) == r && isBareFloor(x, y)) {
                        return new int[]{x, y};
                    }
                }
            }
        }
        return null;
    }

    /** True if (x, y) is an in-bounds ground tile an entity may stand on.
     *  The runtime treats the 3C GOAL as ground so the avatar can step onto it —
     *  without touching the frozen {@link AutograderBuddy#isGroundTile} list. */
    private boolean walkable(int x, int y) {
        if (x < 0 || y < 0 || x >= grid.length || y >= grid[0].length) {
            return false;
        }
        TETile tile = grid[x][y];
        return AutograderBuddy.isGroundTile(tile) || tile.id() == Tileset.GOAL.id();
    }
}
