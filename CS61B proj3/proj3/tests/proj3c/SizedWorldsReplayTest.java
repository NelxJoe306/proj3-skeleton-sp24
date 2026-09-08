package proj3c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import core.GameSnapshot;
import core.Replay;
import core.Session;
import core.WorldSize;
import java.util.ArrayDeque;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 01 — 三档尺寸世界：任一档都能经 3C seam 重建，结构自洽；中档与遗留 seam 逐格一致。
 *
 * <p>只经 {@link Replay#replay(Session)} 断言外部行为（ADR-0004）：快照的 id 网格上做
 * 确定性 / 连通 / 边界 / 多房间代理检验。
 */
public class SizedWorldsReplayTest {

    /** A seed spread wide enough to smoke out BSP pathologies per tier. */
    private static final long[] SEEDS = {
        1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 42, 123, 314159, 987654321L, 1234567890123L
    };

    private static final int FLOOR = Tileset.FLOOR.id();
    private static final int AVATAR = Tileset.AVATAR.id();
    private static final int GOAL = Tileset.GOAL.id();

    @Test
    void everyTierBuildsStructuredDeterministicWorlds() {
        for (WorldSize size : WorldSize.values()) {
            for (long seed : SEEDS) {
                GameSnapshot a = Replay.replay(Session.newGame(size, seed));
                GameSnapshot b = Replay.replay(Session.newGame(size, seed));
                assertEquals(size.width(), a.width, "dims for " + size);
                assertEquals(size.height(), a.height, "dims for " + size);
                assertNotNull(a.avatar, "avatar spawns for " + size + " seed " + seed);
                assertTrue(rowsEqual(a.rows, b.rows), "deterministic for " + size + " seed " + seed);
                assertTrue(hasFloor(a.rows), "has floor for " + size + " seed " + seed);
                assertFalse(floorOnOuterBorder(a), "no floor on outer border for " + size + " seed " + seed);
                assertTrue(floorSingleRegion(a.rows), "floor connected for " + size + " seed " + seed);
                assertTrue(hasInteriorNonFloor(a), "more than one room (proxy) for " + size + " seed " + seed);
            }
        }
    }

    @Test
    void mediumMatchesTheLegacySeamByteForByte() {
        for (long seed : SEEDS) {
            GameSnapshot snap = Replay.replay(Session.newGame(WorldSize.medium, seed));
            TETile[][] legacy = AutograderBuddy.getWorldFromInput("n" + seed + "s");
            assertTrue(sameGridAsLegacy(snap, legacy),
                    "medium seed " + seed + " differs from the legacy seam");
        }
    }

    // ------------------------------------------------------------- helpers

    /** The legacy grid and the replay snapshot must describe the same cells, incl. avatar.
     *  The one allowed divergence: the 3C GOAL is a runtime entity overlaid on a legacy
     *  FLOOR cell (it never sits on a wall/void), so a replay GOAL where legacy has FLOOR
     *  still counts as structurally identical. */
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

    private static boolean rowsEqual(int[][] a, int[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int y = 0; y < a.length; y++) {
            if (!java.util.Arrays.equals(a[y], b[y])) {
                return false;
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

    private static boolean floorOnOuterBorder(GameSnapshot snap) {
        for (int y = 0; y < snap.height; y++) {
            if (snap.rows[y][0] == FLOOR || snap.rows[y][snap.width - 1] == FLOOR) {
                return true;
            }
        }
        for (int x = 0; x < snap.width; x++) {
            if (snap.rows[0][x] == FLOOR || snap.rows[snap.height - 1][x] == FLOOR) {
                return true;
            }
        }
        return false;
    }

    /** Every floor tile is reachable from any floor tile (BFS over 4-neighbours). */
    private static boolean floorSingleRegion(int[][] rows) {
        int h = rows.length;
        int w = rows[0].length;
        boolean[][] seen = new boolean[h][w];
        int total = 0;
        int startY = -1;
        int startX = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (isFloorId(rows[y][x])) {
                    total++;
                    if (startY < 0) {
                        startY = y;
                        startX = x;
                    }
                }
            }
        }
        if (total == 0) {
            return false;
        }
        ArrayDeque<int[]> q = new ArrayDeque<>();
        seen[startY][startX] = true;
        q.add(new int[]{startY, startX});
        int reached = 0;
        int[] dx = {0, 0, -1, 1};
        int[] dy = {-1, 1, 0, 0};
        while (!q.isEmpty()) {
            int[] c = q.poll();
            reached++;
            for (int k = 0; k < 4; k++) {
                int ny = c[0] + dy[k];
                int nx = c[1] + dx[k];
                if (ny >= 0 && ny < h && nx >= 0 && nx < w
                        && !seen[ny][nx] && isFloorId(rows[ny][nx])) {
                    seen[ny][nx] = true;
                    q.add(new int[]{ny, nx});
                }
            }
        }
        return reached == total;
    }

    /**
     * Proxy for "more than one room": inside the bounding box of all floors there is a
     * non-floor cell. A single filled room would have an all-floor interior; BSP worlds
     * keep NOTHING gutters / wall rings between rooms inside the span.
     */
    private static boolean hasInteriorNonFloor(GameSnapshot snap) {
        int minX = snap.width, maxX = -1, minY = snap.height, maxY = -1;
        for (int y = 0; y < snap.height; y++) {
            for (int x = 0; x < snap.width; x++) {
                if (isFloorId(snap.rows[y][x])) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        for (int y = minY + 1; y < maxY; y++) {
            for (int x = minX + 1; x < maxX; x++) {
                if (!isFloorId(snap.rows[y][x])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isFloorId(int id) {
        return id == FLOOR || id == AVATAR || id == GOAL; // GOAL is ground the avatar can traverse
    }
}
