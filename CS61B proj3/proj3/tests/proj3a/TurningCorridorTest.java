package proj3a;

import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 07: 转弯走廊 — 抽样 seed 中 ≥20% 的世界含至少一条带转弯的 corridor。
 *
 * <p>判定启发式（thin elbow）：一块地板若恰有 2 个互相垂直的 floor 邻居、且至少一
 * 侧的邻居仍只有 2 个 floor 邻居（细走廊延伸 ≥2 格），就是一条转弯走廊的拐角。房间
 * 角格（实心区角）的内延邻居都有 ≥3 个 floor 邻居，不会被误判。
 */
public class TurningCorridorTest {

    private static final int SAMPLES = 40;
    private static final int MIN_WORLDS_WITH_TURN = (int) Math.ceil(SAMPLES * 0.20);

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isFloor(TETile t) {
        return t != null && t.character() == Tileset.FLOOR.character();
    }

    private static boolean hasTurningCorridor(TETile[][] g) {
        int w = g.length;
        int h = g[0].length;
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        boolean[][] floor = new boolean[w][h];
        int[][] count = new int[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                floor[x][y] = isFloor(g[x][y]);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!floor[x][y]) {
                    continue;
                }
                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int ny = y + dy[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && floor[nx][ny]) {
                        count[x][y]++;
                    }
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (!floor[x][y] || count[x][y] != 2) {
                    continue;
                }
                int a = -1;
                int b = -1;
                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int ny = y + dy[i];
                    if (nx >= 0 && nx < w && ny >= 0 && ny < h && floor[nx][ny]) {
                        if (a < 0) {
                            a = i;
                        } else {
                            b = i;
                        }
                    }
                }
                boolean perpendicular = (a == 0 && b == 2) || (a == 2 && b == 0)
                        || (a == 1 && b == 3) || (a == 3 && b == 1);
                if (!perpendicular) {
                    continue;
                }
                // A real corridor corner keeps at least one thin (width-1) arm.
                for (int i : new int[]{a, b}) {
                    int nx = x + dx[i];
                    int ny = y + dy[i];
                    if (count[nx][ny] == 2) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Test
    void atLeastTwentyPercentOfWorldsContainATurningCorridor() {
        int withTurn = 0;
        for (int seed = 1; seed <= SAMPLES; seed++) {
            if (hasTurningCorridor(get("n" + seed + "s"))) {
                withTurn++;
            }
        }
        assertTrue(withTurn >= MIN_WORLDS_WITH_TURN,
                "expected >= " + MIN_WORLDS_WITH_TURN + " of " + SAMPLES
                        + " worlds to contain a turning corridor, got " + withTurn);
    }
}
