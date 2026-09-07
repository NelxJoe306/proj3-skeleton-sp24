package proj3a;

import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 05/06: BSP 房间 — 边界无地板、墙/地板层级清晰。
 * 房间的连通性由 ConnectivityTest 覆盖（06 之后全地板是一块连通区域）。
 */
public class RoomStructureTest {

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    private static boolean isFloor(TETile t) {
        return t != null && t.character() == Tileset.FLOOR.character();
    }

    @Test
    void roomsFillTheGridWithNoFloorOnTheBoundary() {
        for (int seed : new int[]{7, 42, 123, 987654321}) {
            TETile[][] g = get("n" + seed + "s");
            int w = g.length;
            int h = g[0].length;
            for (int x = 0; x < w; x++) {
                assertTrue(!isFloor(g[x][0]), "seed " + seed + ": floor on bottom edge");
                assertTrue(!isFloor(g[x][h - 1]), "seed " + seed + ": floor on top edge");
            }
            for (int y = 0; y < h; y++) {
                assertTrue(!isFloor(g[0][y]), "seed " + seed + ": floor on left edge");
                assertTrue(!isFloor(g[w - 1][y]), "seed " + seed + ": floor on right edge");
            }
        }
    }

    @Test
    void roomsAreWalledDistinctFromVoid() {
        TETile[][] g = get("n42s");
        int wall = 0;
        int floor = 0;
        for (TETile[] row : g) {
            for (TETile t : row) {
                if (t != null && t.character() == Tileset.WALL.character()) {
                    wall++;
                } else if (isFloor(t)) {
                    floor++;
                }
            }
        }
        assertTrue(wall > 0, "expected some WALL tiles");
        assertTrue(floor > 0, "expected some FLOOR tiles");
        assertTrue(wall < floor, "walls should not outnumber floors (expected a floor-dominated dungeon)");
    }
}
