package proj3b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;
import utils.FileUtils;

/**
 * Ticket 06: 读档 {@code L} + 存档等价 / 缺档。sp24 判分核心形状：先把
 * {@code n<seed>sDDD:Q} 的存档写入磁盘，再喂 {@code LWWWDDD}，得到的世界必须等于
 * 从未存过档的整串 {@code n<seed>sDDDWWWDDD}。裸 {@code L}（无档）或版本高于 V1
 * 的档 → 全 NOTHING 的 80×80 空网格、不报错、不创建文件。
 */
public class LoadEquivTest {

    private static final String SAVE_FILE = "save.txt";

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    @AfterEach
    void deleteSaveFile() {
        new File(SAVE_FILE).delete();
    }

    private static boolean isNothing(TETile t) {
        return t != null && t.character() == Tileset.NOTHING.character();
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

    private static boolean allNothing(TETile[][] g) {
        if (g.length != 80 || g[0].length != 80) {
            return false;
        }
        for (TETile[] row : g) {
            for (TETile t : row) {
                if (!isNothing(t)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Test
    void loadContinuesExactlyWhereAnUninterruptedRunWouldBe() {
        for (long seed : new long[]{7, 42, 123, 314159}) {
            TETile[][] full = get("n" + seed + "sDDDWWWDDD");
            get("n" + seed + "sDDD:Q"); // write the save at the DDD state
            TETile[][] loaded = get("LWWWDDD");
            assertTrue(gridsEqual(full, loaded),
                    "seed " + seed + ": L-continued run must equal the uninterrupted run");
        }
    }

    @Test
    void lowercaseLoadPrefixBehavesTheSame() {
        long seed = 42;
        TETile[][] full = get("n" + seed + "sDDDWWWDDD");
        get("n" + seed + "sDDD:Q");
        assertTrue(gridsEqual(full, get("lWWWDDD")),
                "lowercase 'l' must load exactly as 'L' does");
    }

    @Test
    void repeatedLoadAndQuitRoundTripsAreStable() {
        long seed = 42;
        TETile[][] baseline = get("n" + seed + "sD"); // continuing after first save, no reload
        get("n" + seed + "sD:Q"); // save the after-D state

        TETile[][] r1 = get("L:Q"); // load after-D state, immediately re-save it
        TETile[][] r2 = get("L:Q"); // and again
        assertTrue(gridsEqual(r1, baseline), "round trip 1 must match continuing without reload");
        assertTrue(gridsEqual(r2, baseline), "round trip 2 must match continuing without reload");
        assertTrue(gridsEqual(r1, r2), "repeated L:Q must be stable");
    }

    @Test
    void bareLoadWithNoSaveReturnsAnEmptyVoidWorld() {
        new File(SAVE_FILE).delete(); // guarantee no save present
        TETile[][] g = get("L");
        assertTrue(allNothing(g), "bare L with no save must return an all-NOTHING 80x80 grid");
        assertTrue(!new File(SAVE_FILE).exists(),
                "bare L must not create a save file");
    }

    @Test
    void aVersionAboveV1SaveIsTreatedAsNoSave() {
        FileUtils.writeFile(SAVE_FILE, "V2\nseed=42\navatar=3,4\n");
        TETile[][] g = get("L");
        assertTrue(allNothing(g), "a V2+ save must be treated as no save (empty world)");
    }

    @Test
    void loadRebuildsStructureFromTheSeedNotFromAPastedMap() {
        // The loaded world continues the same seed's structure: a fresh L-only load
        // (no moves) must show the avatar exactly where the interrupted run left it,
        // which equals the plain continued seed run's avatar position.
        long seed = 7;
        TETile[][] afterDdd = get("n" + seed + "sDDD");
        get("n" + seed + "sDDD:Q");
        TETile[][] loaded = get("L");
        assertTrue(gridsEqual(afterDdd, loaded),
                "L with no following moves must reproduce the saved grid exactly");
    }
}
