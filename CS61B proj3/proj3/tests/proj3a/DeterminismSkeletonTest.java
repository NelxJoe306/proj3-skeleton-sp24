package proj3a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;

/** Ticket 02: 确定性最小管道 — 同 seed ⇒ 同网格、大小写等价、固定尺寸、headless。 */
public class DeterminismSkeletonTest {

    static final String LOWER = "n1234567890123456789s";

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    static boolean gridsEqual(TETile[][] a, TETile[][] b) {
        if (a == null || b == null) {
            return a == b;
        }
        if (a.length != b.length || a[0].length != b[0].length) {
            return false;
        }
        for (int x = 0; x < a.length; x++) {
            for (int y = 0; y < a[0].length; y++) {
                if (a[x][y] == null || b[x][y] == null) {
                    if (a[x][y] != b[x][y]) {
                        return false;
                    }
                } else if (a[x][y].character() != b[x][y].character()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void sameSeedProducesIdenticalWorld() {
        assertTrue(gridsEqual(get(LOWER), get(LOWER)), "same seed must reproduce the same world");
    }

    @Test
    void upperAndLowerCaseSeedReachSameWorld() {
        assertTrue(gridsEqual(
                get("N1234567890123456789S"),
                get("n1234567890123456789s")),
                "N…S and n…s must give the same world");
    }

    @Test
    void worldHasFixedDefaultDimensions() {
        TETile[][] g = get(LOWER);
        assertEquals(80, g.length, "expected default width 80");
        assertEquals(80, g[0].length, "expected default height 80");
    }

    @Test
    void gridIsFullyPopulatedNoNullTiles() {
        TETile[][] g = get(LOWER);
        for (TETile[] row : g) {
            for (TETile t : row) {
                assertTrue(t != null, "no cell may be null");
            }
        }
    }
}
