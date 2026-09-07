package proj3c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import core.GameSnapshot;
import core.Replay;
import core.RuntimeWorld;
import core.Session;
import core.WorldSize;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 02 — goal 与胜利冻结：三档世界的 replay 都把 GOAL 立在最晚生成房间中心
 * （(seed,size) 纯函数、内地 FLOOR、与出生点异格、可达）；存在一串 WASD 走到 goal ⇒
 * {@code won=true} 且之后移动键被冻结；冻结后 {@code :Q} 仍存档；goal 只活在 3C seam，
 * 遗留 {@link AutograderBuddy#getWorldFromInput} 网格永无 GOAL（两 seam 互不污染）。
 */
public class GoalReplayTest {

    /** A seed spread wide enough to smoke out per-tier goal pathologies. */
    private static final long[] SEEDS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 42, 123, 314159, 987654321L, 1234567890123L
    };

    private static final int FLOOR = Tileset.FLOOR.id();
    private static final int AVATAR = Tileset.AVATAR.id();
    private static final int GOAL = Tileset.GOAL.id();

    @AfterEach
    void deleteSaveFile() {
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
    }

    @Test
    void goalIsDeterministicDistinctOnEveryTierAndReachable() {
        for (WorldSize size : WorldSize.values()) {
            for (long seed : SEEDS) {
                GameSnapshot a = Replay.replay(Session.newGame(size, seed));
                GameSnapshot b = Replay.replay(Session.newGame(size, seed));
                assertNotNull(a.avatar, "avatar spawns for " + size + " seed " + seed);
                assertNotNull(a.goal, "goal exists for " + size + " seed " + seed);
                assertFalse(a.goal[0] == a.avatar[0] && a.goal[1] == a.avatar[1],
                        "goal differs from spawn for " + size + " seed " + seed);
                assertArrayEquals(a.goal, b.goal, "goal deterministic for " + size + " seed " + seed);
                assertEquals(GOAL, a.rows[a.goal[1]][a.goal[0]],
                        "goal tile stamped for " + size + " seed " + seed);
                assertFalse(a.goal[0] == 0 || a.goal[1] == 0
                                || a.goal[0] == a.width - 1 || a.goal[1] == a.height - 1,
                        "goal is interior floor for " + size + " seed " + seed);
                assertTrue(pathToGoal(a) != null,
                        "goal reachable from spawn for " + size + " seed " + seed);
            }
        }
    }

    @Test
    void aKeyRunReachesTheGoalAndWinFreezesMovement() {
        for (WorldSize size : WorldSize.values()) {
            long[] seeds = size == WorldSize.large
                    ? new long[]{1, 2, 3, 42}
                    : new long[]{1, 7, 42, 314159};
            for (long seed : seeds) {
                GameSnapshot fresh = Replay.replay(Session.newGame(size, seed));
                String path = pathToGoal(fresh);
                assertNotNull(path, "found a path on " + size + " seed " + seed);

                GameSnapshot won = Replay.replay(Session.newGame(size, seed).withKeys(path));
                assertTrue(won.won, "won after the path on " + size + " seed " + seed);
                assertArrayEquals(fresh.goal, won.avatar,
                        "avatar ends on the goal for " + size + " seed " + seed);
                assertArrayEquals(fresh.goal, won.goal, "goal coord unchanged after win");

                GameSnapshot afterMore = Replay.replay(Session.newGame(size, seed)
                        .withKeys(path + "WASDWASDSSAADDAA"));
                assertTrue(afterMore.won, "still won after extra keys on " + size + " seed " + seed);
                assertArrayEquals(won.avatar, afterMore.avatar,
                        "avatar frozen on the goal for " + size + " seed " + seed);
                assertTrue(rowsEqual(won.rows, afterMore.rows),
                        "frame frozen after extra keys for " + size + " seed " + seed);
            }
        }
    }

    @Test
    void quitAfterWinningStillSaves() {
        long seed = 42;
        GameSnapshot fresh = Replay.replay(Session.newGame(WorldSize.medium, seed));
        String path = pathToGoal(fresh);
        assertNotNull(path);

        new File(RuntimeWorld.SAVE_FILE_NAME).delete(); // guarantee no stale save
        GameSnapshot quit = Replay.replay(Session.newGame(WorldSize.medium, seed)
                .withKeys(path + ":Q"));
        assertTrue(quit.won, "freeze must not block the :Q save");
        assertArrayEquals(fresh.goal, quit.avatar, "avatar still parked on the goal");
        assertTrue(saveHolds(quit.avatar), ":Q wrote the avatar sitting on the goal");
    }

    @Test
    void goalLivesOnlyOnTheThreeCcPath() {
        for (long seed : new long[]{1, 2, 3}) {
            GameSnapshot snap = Replay.replay(Session.newGame(WorldSize.medium, seed));
            assertNotNull(snap.goal, "3C medium carries a goal for seed " + seed);
            assertEquals(1, countInRows(snap, GOAL),
                    "exactly one GOAL tile for seed " + seed);

            TETile[][] legacy = AutograderBuddy.getWorldFromInput("n" + seed + "s");
            assertEquals(0, countInLegacy(legacy, GOAL),
                    "legacy seam grid must never contain GOAL (seed " + seed + ")");
        }
    }

    // ------------------------------------------------------------- movement BFS

    /** Movement-key semantics of the engine, in grid coordinates. */
    private static final int[] DX = {0, -1, 0, 1};
    private static final int[] DY = {1, 0, -1, 0};
    private static final char[] MOVE_KEYS = {'W', 'A', 'S', 'D'};

    /**
     * A shortest WASD run from the avatar to the goal over walkable cells, or {@code null}
     * if the goal is unreachable. Replays cleanly because every step lands on ground and
     * each move exactly reproduces one engine turn.
     */
    private static String pathToGoal(GameSnapshot s) {
        int w = s.width;
        int h = s.height;
        int sx = s.avatar[0];
        int sy = s.avatar[1];
        int tx = s.goal[0];
        int ty = s.goal[1];
        if (sx == tx && sy == ty) {
            return "";
        }
        boolean[][] visited = new boolean[h][w];
        int[][] parent = new int[h][w]; // previous cell index (y * w + x), -1 = none
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                parent[y][x] = -1;
            }
        }
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        visited[sy][sx] = true;
        queue.add(new int[]{sx, sy});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            for (int k = 0; k < 4; k++) {
                int nx = c[0] + DX[k];
                int ny = c[1] + DY[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || visited[ny][nx]
                        || !isWalkable(s.rows[ny][nx])) {
                    continue;
                }
                visited[ny][nx] = true;
                parent[ny][nx] = c[1] * w + c[0];
                if (nx == tx && ny == ty) {
                    return reconstruct(w, sx, sy, tx, ty, parent);
                }
                queue.add(new int[]{nx, ny});
            }
        }
        return null;
    }

    private static String reconstruct(int w, int sx, int sy, int tx, int ty, int[][] parent) {
        List<int[]> back = new ArrayList<>();
        int x = tx;
        int y = ty;
        while (x != sx || y != sy) {
            back.add(new int[]{x, y});
            int p = parent[y][x];
            x = p % w;
            y = p / w;
        }
        StringBuilder keys = new StringBuilder();
        for (int i = back.size() - 1; i >= 0; i--) {
            int[] next = back.get(i);
            int dx = next[0] - x;
            int dy = next[1] - y;
            for (int k = 0; k < 4; k++) {
                if (DX[k] == dx && DY[k] == dy) {
                    keys.append(MOVE_KEYS[k]);
                    break;
                }
            }
            x = next[0];
            y = next[1];
        }
        return keys.toString();
    }

    // ------------------------------------------------------------- helpers

    private static boolean isWalkable(int id) {
        return id == FLOOR || id == AVATAR || id == GOAL;
    }

    private static boolean rowsEqual(int[][] a, int[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int y = 0; y < a.length; y++) {
            for (int x = 0; x < a[y].length; x++) {
                if (a[y][x] != b[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int countInRows(GameSnapshot s, int id) {
        int count = 0;
        for (int y = 0; y < s.height; y++) {
            for (int x = 0; x < s.width; x++) {
                if (s.rows[y][x] == id) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int countInLegacy(TETile[][] grid, int id) {
        int count = 0;
        for (int x = 0; x < grid.length; x++) {
            for (int y = 0; y < grid[0].length; y++) {
                if (grid[x][y].id() == id) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean saveHolds(int[] avatar) {
        try {
            List<String> lines = Files.readAllLines(Path.of(RuntimeWorld.SAVE_FILE_NAME));
            for (String line : lines) {
                String t = line.trim();
                if (t.startsWith("avatar=")) {
                    String xy = t.substring("avatar=".length()).trim();
                    int comma = xy.indexOf(',');
                    if (comma < 0) {
                        return false;
                    }
                    int x = Integer.parseInt(xy.substring(0, comma).trim());
                    int y = Integer.parseInt(xy.substring(comma + 1).trim());
                    return x == avatar[0] && y == avatar[1];
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }
}
