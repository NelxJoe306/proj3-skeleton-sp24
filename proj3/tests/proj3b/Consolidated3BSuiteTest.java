package proj3b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 08: 3B 判分点的常跑收口套件（镜像 3A {@code CriterionSuiteTest} 的角色）。
 * 只从外部行为（{@code AutograderBuddy#getWorldFromInput} 返回的网格）断言：
 * <ul>
 *   <li>瓦片守恒：一次含移动/撞墙的 run 从不增删结构瓦片——wall/void 数不变，
 *       FLOOR 恰被一个 {@code @} 顶替（足迹全还原）；</li>
 *   <li>混合 run 确定性：同 seed + 同按键串 ⇒ 逐格全等；</li>
 *   <li>判分形状跨批 seed 复验：{@code :Q} 纯副作用、存→读等价、往返 {@code L:Q}、
 *       裸 {@code L} 空世界、墙挡后下一合法键照常。</li>
 * </ul>
 */
public class Consolidated3BSuiteTest {

    private static final long[] SEEDS = {7, 42, 123, 314159, 987654321};

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    @AfterEach
    void deleteSaveFile() {
        new File("save.txt").delete();
    }

    private static boolean is(TETile t, TETile kind) {
        return t != null && t.character() == kind.character();
    }

    private static boolean isAvatar(TETile t) {
        return is(t, Tileset.AVATAR);
    }

    private static long count(TETile[][] g, TETile kind) {
        long n = 0;
        for (TETile[] row : g) {
            for (TETile t : row) {
                if (is(t, kind)) {
                    n++;
                }
            }
        }
        return n;
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

    /** A long command body guaranteed to move, collide with bounds, and meet junk. */
    private static String workoutTail() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            sb.append("WASD".charAt(i % 4));
            if (i % 5 == 0) {
                sb.append(';'); // junk that must be ignored
            }
        }
        return sb.toString();
    }

    @Test
    void movementNeverGainsOrLosesStructureTiles() {
        for (long seed : SEEDS) {
            // The returned seed grid already carries the avatar on one floor cell, and
            // movement only permutes which floor cell holds it: every footprint restores
            // to FLOOR and nothing is ever overwritten — so per-character counts are
            // identical between the fresh world and the post-run world.
            TETile[][] seedGrid = get("n" + seed + "s");
            TETile[][] moved = get("n" + seed + "s" + workoutTail());
            for (TETile kind : new TETile[]{Tileset.FLOOR, Tileset.WALL,
                    Tileset.NOTHING, Tileset.AVATAR}) {
                assertEquals(count(seedGrid, kind), count(moved, kind),
                        "seed " + seed + ": '" + kind.character()
                                + "' count changed after movement");
            }
        }
    }

    @Test
    void mixedWorkoutRunsAreDeterministic() {
        for (long seed : SEEDS) {
            String in = "n" + seed + "s" + workoutTail() + "::z";
            assertTrue(gridsEqual(get(in), get(in)),
                    "seed " + seed + ": identical workout string must reproduce the grid");
        }
    }

    @Test
    void colonQEquivalenceHoldsAcrossSeeds() {
        for (long seed : SEEDS) {
            assertTrue(gridsEqual(get("n" + seed + "sDDD:Q"), get("n" + seed + "sDDD")),
                    "seed " + seed + ": :Q must be a pure side effect on the grid");
        }
    }

    @Test
    void saveThenLoadMatchesTheUninterruptedRunAcrossSeeds() {
        for (long seed : SEEDS) {
            TETile[][] full = get("n" + seed + "sDDDWWWDDD");
            get("n" + seed + "sDDD:Q");
            TETile[][] loaded = get("LWWWDDD");
            assertTrue(gridsEqual(full, loaded),
                    "seed " + seed + ": L-continued run must equal the uninterrupted run");
            deleteSaveFile();
        }
    }

    @Test
    void loadQuitRoundTripsStayStableAcrossSeeds() {
        for (long seed : SEEDS) {
            TETile[][] baseline = get("n" + seed + "sD");
            get("n" + seed + "sD:Q");
            assertTrue(gridsEqual(get("L:Q"), baseline),
                    "seed " + seed + ": L:Q round trip must match continuing without reload");
            assertTrue(gridsEqual(get("L:Q"), baseline),
                    "seed " + seed + ": second L:Q must stay stable");
            deleteSaveFile();
        }
    }

    @Test
    void bareLoadWithNoSaveIsAnEmptyVoidWorldAcrossSeeds() {
        deleteSaveFile();
        for (long seed : SEEDS) {
            TETile[][] g = get("L");
            assertEquals(80, g.length, "seed " + seed + ": empty world must be 80 wide");
            assertEquals(80, g[0].length, "seed " + seed + ": empty world must be 80 tall");
            assertTrue(count(g, Tileset.NOTHING) == 80L * 80L,
                    "seed " + seed + ": bare L must be all NOTHING");
        }
        assertTrue(!new File("save.txt").exists(), "bare L must not create a save file");
    }

    @Test
    void aWallBlockedKeyDoesNotStopLaterMovementAcrossSeeds() {
        for (long seed : SEEDS) {
            // Walk far up until the avatar is pinned under a wall/bound, then step back
            // down — the cell it just vacated is always FLOOR, so S is guaranteed to act.
            TETile[][] pinned = get("n" + seed + "s" + "W".repeat(200));
            TETile[][] freed = get("n" + seed + "s" + "W".repeat(200) + "S");
            assertTrue(!gridsEqual(pinned, freed),
                    "seed " + seed + ": keys after a wall block must still act");
            assertTrue(count(freed, Tileset.AVATAR) == 1,
                    "seed " + seed + ": still exactly one @ after blocked + freed run");
        }
    }
}
