package proj3a;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/** Ticket 03: 内容随 seed 变化 — 随机只源自 seed RNG；不同 seed ⇒ 不同世界。 */
public class SeedVariabilityTest {

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static int floorCount(TETile[][] g) {
        int n = 0;
        for (TETile[] row : g) {
            for (TETile t : row) {
                if (t != null && t.character() == Tileset.FLOOR.character()) {
                    n++;
                }
            }
        }
        return n;
    }

    @Test
    void differentSeedsProduceDifferentWorlds() {
        TETile[][] a = get("n1s");
        TETile[][] b = get("n2s");
        TETile[][] c = get("n3s");
        TETile[][] d = get("n42s");
        assertFalse(DeterminismSkeletonTest.gridsEqual(a, b), "seeds 1 and 2 should differ");
        assertFalse(DeterminismSkeletonTest.gridsEqual(b, c), "seeds 2 and 3 should differ");
        assertFalse(DeterminismSkeletonTest.gridsEqual(c, d), "seeds 3 and 42 should differ");
        assertFalse(DeterminismSkeletonTest.gridsEqual(a, d), "seeds 1 and 42 should differ");
    }

    @Test
    void seedDrivesContentBeyondBlank() {
        for (String in : new String[]{"n1s", "n2s", "n42s", "n987654321s"}) {
            TETile[][] g = get(in);
            assertTrue(floorCount(g) > 0, "seed " + in + " should produce some floor content");
        }
    }

    @Test
    void sameSeedIsReproducibleWithContent() {
        TETile[][] a = get("n7s");
        TETile[][] b = get("n7s");
        assertTrue(DeterminismSkeletonTest.gridsEqual(a, b), "same seed must reproduce identical world");
    }
}
