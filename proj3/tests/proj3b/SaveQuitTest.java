package proj3b;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 05: 存档 {@code :Q}（纯副作用 + 截断）。{@code :Q}/{@code :q} 把当前
 * runtime state 写成 CWD 下固定名 {@code save.txt}（V1 格式），并忽略其后的所有
 * 字符——但返回网格与把 {@code :Q} 及后面内容整个去掉的输入完全相同。
 */
public class SaveQuitTest {

    private static final String SAVE_FILE = "save.txt";

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
    }

    @AfterEach
    void deleteSaveFile() {
        new File(SAVE_FILE).delete();
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
    void colonQIsAPureSideEffectOnTheGrid() {
        for (long seed : new long[]{7, 42, 123, 987654321}) {
            assertTrue(gridsEqual(get("n" + seed + "sDDD:Q"), get("n" + seed + "sDDD")),
                    "seed " + seed + ": :Q must not change the returned grid");
        }
    }

    @Test
    void colonQTruncatesEverythingAfterIt() {
        for (long seed : new long[]{7, 42, 123}) {
            // The W and AAA after :Q must never be interpreted as turns.
            assertTrue(gridsEqual(get("n" + seed + "sD:QW"), get("n" + seed + "sD")),
                    "seed " + seed + ": key after :Q must be truncated");
            assertTrue(gridsEqual(get("n" + seed + "s:QAAA"), get("n" + seed + "s")),
                    "seed " + seed + ": whole body after :Q must be truncated");
        }
    }

    @Test
    void lowercaseQQuitsToo() {
        for (long seed : new long[]{7, 42, 123}) {
            assertTrue(gridsEqual(get("n" + seed + "sW:qD"), get("n" + seed + "sW")),
                    "seed " + seed + ": :q must quit as :Q does");
        }
    }

    @Test
    void saveFileIsWrittenInV1Format() throws Exception {
        long seed = 42;
        TETile[][] g = get("n" + seed + "sW:Q");
        assertTrue(new File(SAVE_FILE).exists(), "expected " + SAVE_FILE + " to be written");

        List<String> lines = Files.readAllLines(Path.of(SAVE_FILE));
        assertEquals("V1", lines.get(0), "first line must be the save version");

        String seedLine = lines.stream().filter(l -> l.startsWith("seed=")).findFirst().orElse("");
        assertEquals("seed=" + seed, seedLine, "save must record the seed");

        int[] pos = avatarPosition(g);
        assertTrue(pos != null, "returned grid should still show one @");
        String avatarLine = lines.stream().filter(l -> l.startsWith("avatar=")).findFirst().orElse("");
        assertEquals("avatar=" + pos[0] + "," + pos[1], avatarLine,
                "save avatar must match the @ in the returned grid");
    }
}
