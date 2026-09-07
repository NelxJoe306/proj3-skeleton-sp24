package proj3a;

import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 06: 走廊与连通 — 所有 floor 单连通、无死胡同盲端。
 *
 * <p>房间是实心 ≥5×5 矩形：内格 4 邻居、边格 3、角格 2，所以任何地板格只要拥有
 * 恰好 1 个 floor 邻居，就必然是一条走进走不出的 corridor 盲端。
 */
public class ConnectivityTest {

    private static final int[] SEEDS = {1, 2, 7, 42, 123, 314159, 987654321};

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isFloor(TETile t) {
        return t != null && t.character() == Tileset.FLOOR.character();
    }

    @Test
    void allFloorIsOneConnectedComponent() {
        for (int seed : SEEDS) {
            TETile[][] g = get("n" + seed + "s");
            int w = g.length;
            int h = g[0].length;

            int total = 0;
            int sx = -1;
            int sy = -1;
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (isFloor(g[x][y])) {
                        total++;
                        if (sx < 0) {
                            sx = x;
                            sy = y;
                        }
                    }
                }
            }
            assertTrue(total > 0, "seed " + seed + ": world has no floor");

            boolean[][] seen = new boolean[w][h];
            java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
            stack.push(new int[]{sx, sy});
            seen[sx][sy] = true;
            int reached = 0;
            int[] dx = {1, -1, 0, 0};
            int[] dy = {0, 0, 1, -1};
            while (!stack.isEmpty()) {
                int[] c = stack.pop();
                reached++;
                for (int i = 0; i < 4; i++) {
                    int nx = c[0] + dx[i];
                    int ny = c[1] + dy[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h
                            && isFloor(g[nx][ny]) && !seen[nx][ny]) {
                        seen[nx][ny] = true;
                        stack.push(new int[]{nx, ny});
                    }
                }
            }
            assertTrue(reached == total,
                    "seed " + seed + ": floor is not fully connected (reached " + reached
                            + " of " + total + ")");
        }
    }

    @Test
    void noFloorCellIsADeadEnd() {
        for (int seed : SEEDS) {
            TETile[][] g = get("n" + seed + "s");
            int w = g.length;
            int h = g[0].length;
            int deadEnds = 0;
            int[] dx = {1, -1, 0, 0};
            int[] dy = {0, 0, 1, -1};
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (!isFloor(g[x][y])) {
                        continue;
                    }
                    int n = 0;
                    for (int i = 0; i < 4; i++) {
                        int nx = x + dx[i];
                        int ny = y + dy[i];
                        if (nx >= 0 && nx < w && ny >= 0 && ny < h && isFloor(g[nx][ny])) {
                            n++;
                        }
                    }
                    if (n == 1) {
                        deadEnds++;
                    }
                }
            }
            assertTrue(deadEnds == 0,
                    "seed " + seed + ": found " + deadEnds + " dead-end floor cell(s)");
        }
    }
}
