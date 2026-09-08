package proj3b;

import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 03: 碰撞 — 墙/虚空/越界静默挡下，avatar 原地不动、不报错，后续合法键照常。
 *
 * <p>每个方向朝同一边连按远超过世界尺寸的次数，avatar 必然被顶到那一侧的边界：
 * 若它正前方是不可走格（WALL/NOTHING）或已越界，就被挡住。黑盒断言最终 avatar 唯一、
 * 站在可走格上，且它朝该方向的邻格不可走（撞墙/虚空）或已出界——不需要预知房间布局。
 */
public class CollisionTest {

    private static final long[] SEEDS = {1, 7, 42, 123, 314159, 987654321};

    /** Movement deltas per direction key. */
    private static final int[][] DELTA = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isAvatar(TETile t) {
        return t != null && t.character() == Tileset.AVATAR.character();
    }

    private static boolean isGround(TETile t) {
        return t != null && AutograderBuddy.isGroundTile(t);
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

    @Test
    void pressingIntoABoundManyTimesLeavesAvatarPinnedAgainstNonGround() {
        char[] keys = {'W', 'A', 'S', 'D'};
        for (long seed : SEEDS) {
            for (int dir = 0; dir < 4; dir++) {
                StringBuilder sb = new StringBuilder("n").append(seed).append('s');
                for (int i = 0; i < 200; i++) {
                    sb.append(keys[dir]);
                }
                TETile[][] g = get(sb.toString());
                int[] pos = avatarPosition(g);
                assertTrue(pos != null, "seed " + seed + " dir " + keys[dir] + ": one @ expected");
                int px = pos[0];
                int py = pos[1];
                int nx = px + DELTA[dir][0];
                int ny = py + DELTA[dir][1];
                boolean out = nx < 0 || ny < 0 || nx >= g.length || ny >= g[0].length;
                assertTrue(out || !isGround(g[nx][ny]),
                        "seed " + seed + " dir " + keys[dir] + ": avatar at " + px + "," + py
                                + " not blocked (neighbour " + nx + "," + ny + " is ground)");
            }
        }
    }

    @Test
    void aBlockedMoveIsSilentAndTheNextValidKeyStillActs() {
        for (long seed : SEEDS) {
            String up = "n" + seed + "s" + "W".repeat(200);      // avatar now pinned up
            TETile[][] pinned = get(up);
            int[] p = avatarPosition(pinned);
            assertTrue(p != null, "seed " + seed + ": expected one @ after the wall run");

            // One S afterwards must move the avatar straight back down one cell.
            TETile[][] stepped = get(up + "S");
            int[] q = avatarPosition(stepped);
            assertTrue(q != null, "seed " + seed + ": one @ expected after blocked-run + S");
            assertTrue(q[0] == p[0] && q[1] == p[1] - 1,
                    "seed " + seed + ": after blocked W-run, S should drop one ("
                            + p[0] + "," + p[1] + " -> " + q[0] + "," + q[1] + ")");
            assertTrue(isGround(pinned[p[0]][p[1]]),
                    "seed " + seed + ": blocked avatar must still stand on ground");
        }
    }

    @Test
    void collisionRunIsDeterministic() {
        for (long seed : SEEDS) {
            String in = "n" + seed + "s" + "W".repeat(300) + "D".repeat(300);
            assertTrue(gridsEqual(get(in), get(in)),
                    "seed " + seed + ": repeated blocked keys must reproduce identical grid");
        }
    }
}
