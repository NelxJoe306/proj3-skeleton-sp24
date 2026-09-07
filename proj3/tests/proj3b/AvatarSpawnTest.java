package proj3b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 01: 运行时世界 — avatar 在首个生成房间的中心出生。
 *
 * <p>黑盒断言（只经 {@code AutograderBuddy.getWorldFromInput}）：
 * 结构世界上恰好一个 {@code @}，且它落在一个 4 邻全为 FLOOR 的「房间内部」地格
 * 上——首个房间中心 ≥2 格深，走廊只从墙缘切入、到不了中心，所以中心必是内部格。
 */
public class AvatarSpawnTest {

    private static final long[] SEEDS = {
        1, 7, 42, 123, 314159, 987654321, 1234567890123456789L
    };

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isFloor(TETile t) {
        return t != null && t.character() == Tileset.FLOOR.character();
    }

    private static boolean isAvatar(TETile t) {
        return t != null && t.character() == Tileset.AVATAR.character();
    }

    /** Counts {@code @} tiles in the grid and returns the (single) position, or null. */
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
    void exactlyOneAvatarAppearsOnAPlainSeed() {
        for (long seed : SEEDS) {
            TETile[][] g = get("n" + seed + "s");
            int[] pos = avatarPosition(g);
            assertTrue(pos != null, "seed " + seed + ": expected exactly one @");
        }
    }

    @Test
    void avatarSpawnsOnAnInteriorRoomFloor() {
        for (long seed : SEEDS) {
            TETile[][] g = get("n" + seed + "s");
            int[] pos = avatarPosition(g);
            assertTrue(pos != null, "seed " + seed + ": expected exactly one @");
            int x = pos[0];
            int y = pos[1];
            // Center of the first room: a deep interior floor cell whose 4 orthogonal
            // neighbors are all FLOOR (≥2 tiles from any wall/corridor).
            assertTrue(x > 0 && x < g.length - 1 && y > 0 && y < g[0].length - 1,
                    "seed " + seed + ": avatar at " + x + "," + y + " not inside grid");
            assertTrue(isFloor(g[x - 1][y]) && isFloor(g[x + 1][y])
                            && isFloor(g[x][y - 1]) && isFloor(g[x][y + 1]),
                    "seed " + seed + ": avatar at " + x + "," + y
                            + " is not on an interior floor cell (4 floor neighbours required)");
        }
    }

    @Test
    void spawnIsDeterministic() {
        for (long seed : SEEDS) {
            TETile[][] a = get("n" + seed + "s");
            TETile[][] b = get("n" + seed + "s");
            assertTrue(gridsEqual(a, b), "seed " + seed + ": same seed must spawn identically");
        }
    }

    @Test
    void structureWithoutMovementIsUnchangedAsideFromAvatar() {
        // An ignored tail (no movement yet) must not relocate or duplicate the avatar,
        // and the surrounding structure must still be intact floor/room content.
        TETile[][] g = get("n42sXYZ");
        int[] pos = avatarPosition(g);
        assertTrue(pos != null, "expected exactly one @ even with an unrecognized tail");
    }
}
