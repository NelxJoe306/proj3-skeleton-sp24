package proj3c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import core.GameSnapshot;
import core.Replay;
import core.RuntimeWorld;
import core.Session;
import core.WorldSize;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.Tileset;

/**
 * Ticket 03 — 存档带尺寸行与读档续局（ADR-0005）。3C 的 {@code :Q} 存档头恒 {@code V1}，
 * 仅非 80×80 世界追加 {@code size=WxH} 行（中档文件与遗留 seam 逐字节相同）；goal 永不落盘，
 * 每次 {@code load} 按 (seed, size) 重推导。无档/坏档 ⇒ {@code saveMissing=true} 的中档空世界。
 */
public class SaveLoadSizeReplayTest {

    private static final int FLOOR = Tileset.FLOOR.id();
    private static final int AVATAR = Tileset.AVATAR.id();

    @AfterEach
    void deleteSaveFile() {
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
    }

    @Test
    void loadWithNoSaveReportsMissingAndGivesAnEmptyMediumWorld() {
        new File(RuntimeWorld.SAVE_FILE_NAME).delete(); // guarantee no stale save
        GameSnapshot s = Replay.replay(Session.load());
        assertTrue(s.saveMissing, "no save must be reported missing");
        assertNull(s.avatar, "no avatar in the empty world");
        assertNull(s.goal, "no goal in the empty world");
        assertEquals(WorldSize.medium.width(), s.width, "missing load defaults to MEDIUM width");
        assertEquals(WorldSize.medium.height(), s.height, "missing load defaults to MEDIUM height");
        assertFalse(hasAny(s.rows, FLOOR), "empty world has no floor");
        assertFalse(hasAny(s.rows, AVATAR), "empty world has no avatar tile");
    }

    @Test
    void roundTripContinuesIdenticallyOnSmallAndLarge() {
        long seed = 7;
        String more = "SDWWAA";
        for (WorldSize size : new WorldSize[]{WorldSize.small, WorldSize.large}) {
            new File(RuntimeWorld.SAVE_FILE_NAME).delete();
            GameSnapshot spawn = Replay.replay(Session.newGame(size, seed));
            GameSnapshot paused = Replay.replay(Session.newGame(size, seed).withKeys("W:Q"));
            assertFalse(paused.won, "setup move must not win on " + size);
            assertFalse(Arrays.equals(spawn.avatar, paused.avatar),
                    "one step must leave the spawn on " + size);
            assertNotNull(paused.avatar);

            GameSnapshot cont = Replay.replay(Session.load().withKeys(more));
            GameSnapshot uninterrupted = Replay.replay(Session.newGame(size, seed)
                    .withKeys("W" + more));

            assertFalse(cont.saveMissing, "a real save loads fine on " + size);
            assertFalse(cont.won, "continue is not a win on " + size);
            assertArrayEquals(uninterrupted.avatar, cont.avatar,
                    "avatar after continue matches uninterrupted replay on " + size);
            assertTrue(rowsEqual(uninterrupted.rows, cont.rows),
                    "frame after continue matches uninterrupted replay on " + size);
            assertArrayEquals(spawn.goal, cont.goal,
                    "goal is re-derived to the same cell on " + size);
            assertEquals(GOAL_ID, cont.rows[cont.goal[1]][cont.goal[0]],
                    "goal tile is stamped on the loaded world (" + size + ")");
        }
    }

    @Test
    void mediumSaveMatchesTheLegacyBytesAndLargeCarriesASizeLine() {
        long seed = 42;
        // Medium: the 3C seam writes exactly the bytes the legacy seam writes (no size line).
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
        AutograderBuddy.getWorldFromInput("n" + seed + "sW:Q");
        String legacyText = readSave();
        assertTrue(legacyText.startsWith("V1\n"), "header must stay V1");

        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
        Replay.replay(Session.newGame(WorldSize.medium, seed).withKeys("W:Q"));
        String replayText = readSave();
        assertEquals(legacyText, replayText,
                "an 80x80 save must be byte-identical to the legacy seam");
        assertFalse(replayText.contains("size="), "medium save carries no size line");

        // Large: the file carries size=120x120, and a later load rebuilds at that size.
        new File(RuntimeWorld.SAVE_FILE_NAME).delete();
        GameSnapshot large = Replay.replay(Session.newGame(WorldSize.large, seed).withKeys("W:Q"));
        String bigText = readSave();
        assertTrue(bigText.contains("size=120x120"), "large save must carry size=120x120");
        GameSnapshot reloaded = Replay.replay(Session.load());
        assertEquals(large.width, reloaded.width, "reload keeps the large width");
        assertEquals(large.height, reloaded.height, "reload keeps the large height");
        assertArrayEquals(large.avatar, reloaded.avatar, "reload restores the avatar position");
    }

    // ------------------------------------------------------------- helpers

    private static final int GOAL_ID = Tileset.GOAL.id();

    private static boolean rowsEqual(int[][] a, int[][] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int y = 0; y < a.length; y++) {
            if (!Arrays.equals(a[y], b[y])) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAny(int[][] rows, int id) {
        for (int[] row : rows) {
            for (int cell : row) {
                if (cell == id) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String readSave() {
        try {
            return Files.readString(Path.of(RuntimeWorld.SAVE_FILE_NAME));
        } catch (Exception e) {
            return "";
        }
    }
}
