package proj3c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import core.GameSnapshot;
import core.Replay;
import core.RuntimeWorld;
import core.Session;
import core.WorldSize;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 10 — 3C 常跑收口套件，镜像 3A {@code CriterionSuiteTest} / 3B
 * {@code Consolidated3BSuiteTest} 的角色：把 3C 判分形状一次性复验一遍。全部只经 3C seam
 * ({@link Replay#replay(Session)}，ADR-0004) 从外部快照断言，不触碰内部结构：
 *
 * <ul>
 *   <li>三档×小 seed：确定性、尺寸、avatar/goal 落地与可及性、goal 内地且与出生点异格、无越界;</li>
 *   <li>中档与遗留 seam 逐格同构（唯一允许差异：3C GOAL 覆于遗留 FLOOR 上）;</li>
 *   <li>一串 WASD 走到 goal ⇒ {@code won=true} 且之后移动冻结（快照与坐标逐格不变）;</li>
 *   <li>{@code :Q} 存档往返（small/medium/large）与不间断重放逐格一致；无档 ⇒ {@code saveMissing} 空世界;</li>
 *   <li>快照信封：rows 维度、legend 覆盖所有出现的 id、won 时 avatar==goal 且 goal 仍被上报。</li>
 * </ul>
 *
 * <p>深层分主题覆盖见 {@code SizedWorldsReplayTest} / {@code MovementReplayTest} /
 * {@code GoalReplayTest} / {@code SaveLoadSizeReplayTest}——本套件只做收口复验，不替代它们。
 */
public class Consolidated3CSuiteTest {

    /** A compact seed spread proven (by the topic tests) to reach the goal on every tier. */
    private static final long[] SEEDS = {1L, 2L, 3L, 42L};

    private static final int FLOOR = Tileset.FLOOR.id();
    private static final int AVATAR = Tileset.AVATAR.id();
    private static final int GOAL = Tileset.GOAL.id();

    @AfterEach
    void deleteSaveFile() {
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
    }

    @Test
    void threeTiersRebuildDeterministicStructuredWorldsWithAGoal() {
        for (WorldSize size : WorldSize.values()) {
            for (long seed : SEEDS) {
                GameSnapshot a = Replay.replay(Session.newGame(size, seed));
                GameSnapshot b = Replay.replay(Session.newGame(size, seed));
                assertEquals(size.width(), a.width, "width for " + size + " seed " + seed);
                assertEquals(size.height(), a.height, "height for " + size + " seed " + seed);
                assertNotNull(a.avatar, "avatar spawns for " + size + " seed " + seed);
                assertNotNull(a.goal, "goal stamped for " + size + " seed " + seed);
                assertFalse(a.goal[0] == a.avatar[0] && a.goal[1] == a.avatar[1],
                        "goal differs from spawn for " + size + " seed " + seed);
                assertFalse(a.goal[0] == 0 || a.goal[1] == 0
                                || a.goal[0] == a.width - 1 || a.goal[1] == a.height - 1,
                        "goal is interior for " + size + " seed " + seed);
                assertEquals(GOAL, a.rows[a.goal[1]][a.goal[0]],
                        "goal tile stamped in rows for " + size + " seed " + seed);
                assertTrue(rowsEqual(a.rows, b.rows), "deterministic for " + size + " seed " + seed);
                assertArrayEquals(a.goal, b.goal, "goal deterministic for " + size + " seed " + seed);
                assertTrue(hasFloor(a.rows), "has floor for " + size + " seed " + seed);
                assertFalse(floorOnOuterBorder(a), "no floor on the outer border for "
                        + size + " seed " + seed);
            }
        }
    }

    @Test
    void mediumWorldsStayIsomorphicToTheLegacySeam() {
        for (long seed : SEEDS) {
            GameSnapshot snap = Replay.replay(Session.newGame(WorldSize.medium, seed));
            TETile[][] legacy = AutograderBuddy.getWorldFromInput("n" + seed + "s");
            assertTrue(sameGridAsLegacy(snap, legacy),
                    "medium seed " + seed + " must match the legacy seam cell for cell");
        }
    }

    @Test
    void everyGoalIsReachableAndWinningFreezesMovement() {
        for (WorldSize size : WorldSize.values()) {
            for (long seed : SEEDS) {
                GameSnapshot fresh = Replay.replay(Session.newGame(size, seed));
                String path = pathToGoal(fresh);
                assertNotNull(path, "goal reachable for " + size + " seed " + seed);

                GameSnapshot won = Replay.replay(Session.newGame(size, seed).withKeys(path));
                assertTrue(won.won, "won after the path for " + size + " seed " + seed);
                assertArrayEquals(fresh.goal, won.avatar,
                        "avatar ends on the goal for " + size + " seed " + seed);
                assertArrayEquals(fresh.goal, won.goal, "goal coord unchanged after win");

                GameSnapshot afterMore = Replay.replay(Session.newGame(size, seed)
                        .withKeys(path + "WASDWASDSSAADD"));
                assertTrue(afterMore.won, "still won after extra keys for " + size + " seed " + seed);
                assertArrayEquals(won.avatar, afterMore.avatar,
                        "avatar frozen on the goal for " + size + " seed " + seed);
                assertTrue(rowsEqual(won.rows, afterMore.rows),
                        "frame frozen after extra keys for " + size + " seed " + seed);
            }
        }
    }

    @Test
    void saveRoundTripsMatchUninterruptedReplayAndMissingIsAnEmptyWorld() {
        for (WorldSize size : WorldSize.values()) {
            long seed = 7;
            String more = "WAASSDD";

            GameSnapshot spawn = Replay.replay(Session.newGame(size, seed));
            String setup = stepFromSpawnNotOntoGoal(spawn);
            assertNotNull(setup, "spawn has an open neighbour away from the goal on "
                    + size + " seed " + seed);
            GameSnapshot paused = Replay.replay(Session.newGame(size, seed).withKeys(setup + ":Q"));
            assertFalse(paused.won, "a one-step pause must not win on " + size);
            assertFalse(paused.avatar[0] == spawn.avatar[0] && paused.avatar[1] == spawn.avatar[1],
                    "the :Q setup move really moved on " + size);

            // Continue-from-save must equal the uninterrupted session: the engine saved the
            // frame reached by `setup`, and loading replays the same world from that spot, so
            // replay(load, more) == replay(new, setup + more) (session-continuation invariant).
            GameSnapshot cont = Replay.replay(Session.load().withKeys(more));
            GameSnapshot uninterrupted = Replay.replay(Session.newGame(size, seed)
                    .withKeys(setup + more));
            assertFalse(cont.saveMissing, "a real save loads on " + size);
            assertFalse(cont.won, "resume is not a win on " + size);
            assertArrayEquals(uninterrupted.avatar, cont.avatar,
                    "resume equals uninterrupted on " + size);
            assertTrue(rowsEqual(uninterrupted.rows, cont.rows),
                    "resumed frame equals uninterrupted on " + size);
            assertArrayEquals(spawn.goal, cont.goal,
                    "goal re-derives to the same cell on " + size);
        }

        // No save at all: saveMissing=true, an empty MEDIUM void world with no avatar/goal.
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
        GameSnapshot missing = Replay.replay(Session.load());
        assertTrue(missing.saveMissing, "no save must be reported missing");
        assertEquals(WorldSize.medium.width(), missing.width, "missing load defaults to MEDIUM width");
        assertEquals(WorldSize.medium.height(), missing.height, "missing load defaults to MEDIUM height");
        assertNull(missing.avatar, "no avatar in the empty world");
        assertNull(missing.goal, "no goal in the empty world");
        assertFalse(hasAny(missing.rows, FLOOR), "empty world has no floor");
        assertFalse(hasAny(missing.rows, AVATAR), "empty world has no avatar tile");
    }

    @Test
    void snapshotEnvelopeIsSelfConsistent() {
        for (WorldSize size : WorldSize.values()) {
            for (long seed : new long[]{1L, 2L}) {
                GameSnapshot snap = Replay.replay(Session.newGame(size, seed));
                assertEquals(snap.height, snap.rows.length, "rows.length is the height for "
                        + size + " seed " + seed);
                for (int[] row : snap.rows) {
                    assertEquals(snap.width, row.length, "every row is the width for "
                            + size + " seed " + seed);
                }
                boolean[] legendIds = legendIds(snap);
                for (int y = 0; y < snap.height; y++) {
                    for (int x = 0; x < snap.width; x++) {
                        int id = snap.rows[y][x];
                        assertTrue(id >= 0 && id < legendIds.length && legendIds[id],
                                "legend explains tile id " + id + " (" + size + " seed " + seed + ")");
                    }
                }
                assertNotNull(snap.goal);
                assertTrue(legendIds[GOAL], "legend always carries the goal glyph when a goal exists");
                assertTrue(legendIds[AVATAR], "legend always carries the avatar glyph");
            }
        }

        // A won frame keeps reporting the goal entity even though the avatar tile covers it.
        GameSnapshot fresh = Replay.replay(Session.newGame(WorldSize.medium, 1L));
        GameSnapshot won = Replay.replay(Session.newGame(WorldSize.medium, 1L)
                .withKeys(pathToGoal(fresh)));
        assertTrue(won.won, "medium seed 1 must win");
        assertArrayEquals(won.goal, won.avatar, "won avatar sits on the reported goal");
    }

    // ------------------------------------------------------------- helpers

    private static boolean rowsEqual(int[][] a, int[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int y = 0; y < a.length; y++) {
            if (a[y].length != b[y].length) {
                return false;
            }
            for (int x = 0; x < a[y].length; x++) {
                if (a[y][x] != b[y][x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasFloor(int[][] rows) {
        for (int[] row : rows) {
            for (int id : row) {
                if (id == FLOOR) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasAny(int[][] rows, int id) {
        for (int[] row : rows) {
            for (int cell : row) {
                if (cell == id) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The 3C goal must never sit on the map's outer border (walls always ring the map). */
    private static boolean floorOnOuterBorder(GameSnapshot snap) {
        for (int y = 0; y < snap.height; y++) {
            for (int x = 0; x < snap.width; x++) {
                boolean onBorder = x == 0 || y == 0 || x == snap.width - 1 || y == snap.height - 1;
                if (onBorder && snap.rows[y][x] == FLOOR) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A one-key move that provably displaces the avatar yet does not step onto the goal:
     * the goal is never the spawn cell, the spawn sits on a connected floor region, and the
     * goal is a ground tile — so unless the goal is the spawn's only open neighbour (which
     * would make every seed trivial) some cardinal step lands on plain ground, not the win.
     */
    private static String stepFromSpawnNotOntoGoal(GameSnapshot s) {
        int x = s.avatar[0];
        int y = s.avatar[1];
        for (int k = 0; k < 4; k++) {
            int nx = x + DX[k];
            int ny = y + DY[k];
            if (nx == s.goal[0] && ny == s.goal[1]) {
                continue; // this direction would win on the very first step
            }
            if (nx < 0 || ny < 0 || nx >= s.width || ny >= s.height) {
                continue; // map edge
            }
            if (isWalkable(s.rows[ny][nx])) {
                return String.valueOf(MOVE_KEYS[k]);
            }
        }
        return null;
    }

    /**
     * The replay grid and the legacy grid must describe the same world. The one allowed
     * divergence: the 3C GOAL is a runtime entity overlaid on a legacy FLOOR cell (it never
     * sits on a wall/void), so a replay GOAL where legacy has FLOOR still counts as identical.
     */
    private static boolean sameGridAsLegacy(GameSnapshot snap, TETile[][] legacy) {
        assertEquals(legacy.length, snap.width);
        assertEquals(legacy[0].length, snap.height);
        for (int y = 0; y < snap.height; y++) {
            for (int x = 0; x < snap.width; x++) {
                int id = snap.rows[y][x];
                int legacyId = legacy[x][y].id();
                boolean goalOverlay = id == GOAL && legacyId == FLOOR;
                if (id != legacyId && !goalOverlay) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Which tile ids the snapshot's legend explains. */
    private static boolean[] legendIds(GameSnapshot snap) {
        boolean[] seen = new boolean[64]; // tile ids are tiny (0..~20); generous ceiling
        for (GameSnapshot.Legend l : snap.legend) {
            if (l.id >= 0 && l.id < seen.length) {
                seen[l.id] = true;
            }
        }
        return seen;
    }

    // ------------------------------------------------------------- movement BFS

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
        int[][] parent = new int[h][w];
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

    private static boolean isWalkable(int id) {
        return id == FLOOR || id == AVATAR || id == GOAL;
    }
}
