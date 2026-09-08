package proj3b;

import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 02: WASD 移动 + 足迹还原。单步测试全部基于一个保证：avatar 出生在房间
 * 内部格（4 邻皆 FLOOR），所以从出生点朝任一方向走一步必然落在 FLOOR 上、与具体
 * seed 无关——这让移动测试可以只用黑盒断言、不必预知哪条路是通的。
 *
 * <p>坐标映射（TERenderer 以 StdDraw y 向上绘制）: W:+y, A:−x, S:−y, D:+x。
 * 「结构不变、只有 avatar 移位」用构造期望网格比对来断言：把出生网格里旧格还原成
 * FLOOR、新格盖上 @，应与返回网格逐格相等。
 */
public class WASDMovementTest {

    private static final long[] SEEDS = {1, 7, 42, 123, 314159, 987654321};

    /** Direction deltas per key, in (dx, dy). */
    private static final int[][] DELTA = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isFloor(TETile t) {
        return t != null && t.character() == Tileset.FLOOR.character();
    }

    private static boolean isAvatar(TETile t) {
        return t != null && t.character() == Tileset.AVATAR.character();
    }

    private static int[] avatarPosition(TETile[][] g) {
        int[] pos = null;
        int count = 0;
        for (int x = 0; x < g.length; x++) {
            for (int y = 0; y < g[0].length; y++) {
                if (isAvatar(g[x][y])) {
                    count++;
                    pos = new int[]{x, y};
                }
            }
        }
        return count == 1 ? pos : null;
    }

    /** Copies the grid (structure + current avatar) so a test can edit the copy. */
    private static TETile[][] copy(TETile[][] g) {
        TETile[][] c = new TETile[g.length][g[0].length];
        for (int x = 0; x < g.length; x++) {
            System.arraycopy(g[x], 0, c[x], 0, g[0].length);
        }
        return c;
    }

    private static boolean gridsEqual(TETile[][] a, TETile[][] b) {
        if (a.length != b.length || a[0].length != b[0].length) {
            return false;
        }
        for (int x = 0; x < a.length; x++) {
            for (int y = 0; y < a[0].length; y++) {
                if (a[x][y].character() != b[x][y].character()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Applies one {@code dir}-index step to grid {@code g}: restore old to FLOOR, stamp new. */
    private static void applyStep(TETile[][] g, int[] pos, int dir) {
        int nx = pos[0] + DELTA[dir][0];
        int ny = pos[1] + DELTA[dir][1];
        g[pos[0]][pos[1]] = Tileset.FLOOR;
        g[nx][ny] = Tileset.AVATAR;
    }

    @Test
    void eachWASDKeyMovesOneStepInItsDirection() {
        char[] keys = {'W', 'A', 'S', 'D'};
        for (long seed : SEEDS) {
            int[] spawn = avatarPosition(get("n" + seed + "s"));
            for (int dir = 0; dir < 4; dir++) {
                TETile[][] moved = get("n" + seed + "s" + keys[dir]);
                int[] pos = avatarPosition(moved);
                assertTrue(pos != null, "seed " + seed + " + " + keys[dir] + ": one @ expected");
                assertTrue(pos[0] == spawn[0] + DELTA[dir][0]
                                && pos[1] == spawn[1] + DELTA[dir][1],
                        "seed " + seed + " + " + keys[dir] + ": avatar at " + pos[0] + "," + pos[1]
                                + " expected " + (spawn[0] + DELTA[dir][0]) + ","
                                + (spawn[1] + DELTA[dir][1]));
                assertTrue(isFloor(moved[spawn[0]][spawn[1]]),
                        "seed " + seed + " + " + keys[dir] + ": footprint not restored to FLOOR");
            }
        }
    }

    @Test
    void aStepLeavesStructureUntouchedAsideFromTheFootprintSwap() {
        for (long seed : SEEDS) {
            TETile[][] seedGrid = get("n" + seed + "s");
            int[] spawn = avatarPosition(seedGrid);
            char[] keys = {'W', 'A', 'S', 'D'};
            for (int dir = 0; dir < 4; dir++) {
                TETile[][] moved = get("n" + seed + "s" + keys[dir]);
                TETile[][] expected = copy(seedGrid);
                applyStep(expected, spawn, dir);
                assertTrue(gridsEqual(moved, expected),
                        "seed " + seed + " + " + keys[dir]
                                + ": grid differs from a pure footprint swap");
            }
        }
    }

    @Test
    void lowercaseKeysBehaveIdentically() {
        for (long seed : SEEDS) {
            TETile[][] upper = get("n" + seed + "sWASD");
            TETile[][] lower = get("n" + seed + "swasd");
            assertTrue(gridsEqual(upper, lower), "seed " + seed + ": WASD != wasd");
        }
    }

    @Test
    void twoStepsForwardRestoreBothFootprints() {
        for (long seed : SEEDS) {
            TETile[][] seedGrid = get("n" + seed + "s");
            int[] spawn = avatarPosition(seedGrid);
            TETile[][] moved = get("n" + seed + "sWW");
            int[] pos = avatarPosition(moved);
            assertTrue(pos != null, "seed " + seed + ": one @ expected after WW");
            assertTrue(pos[1] == spawn[1] + 2,
                    "seed " + seed + ": WW should move two up (avatar at " + pos[0] + "," + pos[1] + ")");
            TETile[][] expected = copy(seedGrid);
            applyStep(expected, spawn, 0); // first W
            int[] mid = {spawn[0], spawn[1] + 1};
            applyStep(expected, mid, 0); // second W
            assertTrue(gridsEqual(moved, expected), "seed " + seed + ": WW footprint swap mismatch");
        }
    }

    @Test
    void movementIsDeterministic() {
        for (long seed : SEEDS) {
            TETile[][] a = get("n" + seed + "sWDSA");
            TETile[][] b = get("n" + seed + "sWDSA");
            assertTrue(gridsEqual(a, b), "seed " + seed + ": same keys must reproduce identical grid");
        }
    }
}
