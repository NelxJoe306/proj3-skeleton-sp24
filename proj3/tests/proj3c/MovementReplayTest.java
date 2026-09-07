package proj3c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import core.GameSnapshot;
import core.Replay;
import core.Session;
import core.WorldSize;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Ticket 01 — 按键重放：WASD 一格一格动、撞墙/越界停住、未知字符忽略；中档移动语义与
 * 遗留 seam 完全一致（同一引擎内核、不同入口）。
 */
public class MovementReplayTest {

    @Test
    void mediumMovementMatchesTheLegacySeam() {
        long seed = 42;
        String[] runs = {"W", "A", "S", "D", "WWAA", "SSDDWW", "WzD", "AAAZZZ", "WASDWASD"};
        for (String keys : runs) {
            GameSnapshot snap = Replay.replay(Session.newGame(WorldSize.medium, seed).withKeys(keys));
            int[] expected = legacyAvatar("n" + seed + "s" + keys);
            assertArrayEquals(expected, snap.avatar, "keys \"" + keys + "\"");
        }
    }

    @Test
    void unknownCharactersDoNotMove() {
        long seed = 42;
        int[] at = Replay.replay(Session.newGame(WorldSize.medium, seed)).avatar;
        int[] after = Replay.replay(Session.newGame(WorldSize.medium, seed)
                .withKeys("zzzz!@#1234567 ")).avatar;
        assertArrayEquals(at, after, "junk keys must not take a turn");
    }

    @Test
    void movementWorksOnEveryTier() {
        for (WorldSize size : WorldSize.values()) {
            long seed = 7;
            int[] before = Replay.replay(Session.newGame(size, seed)).avatar;
            int[] after = Replay.replay(Session.newGame(size, seed).withKeys("DDWW")).avatar;
            assertNotNull(after, "avatar moves on " + size);
            assertFalse(Arrays.equals(before, after), "DDWW must move on " + size);
        }
    }

    @Test
    void wallsBlockInsteadOfMovingOutOfBounds() {
        long seed = 314159;
        int[] at200 = Replay.replay(Session.newGame(WorldSize.small, seed)
                .withKeys(repeat('A', 200))).avatar;
        int[] at201 = Replay.replay(Session.newGame(WorldSize.small, seed)
                .withKeys(repeat('A', 201))).avatar;
        assertArrayEquals(at200, at201, "once pinned at the left wall, more A does nothing");
        assertTrue(at200[0] >= 0 && at200[1] >= 0);
    }

    // ------------------------------------------------------------- helpers

    private static int[] legacyAvatar(String input) {
        TETile[][] g = AutograderBuddy.getWorldFromInput(input);
        for (int x = 0; x < g.length; x++) {
            for (int y = 0; y < g[0].length; y++) {
                if (g[x][y].id() == Tileset.AVATAR.id()) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static String repeat(char c, int n) {
        char[] buf = new char[n];
        Arrays.fill(buf, c);
        return new String(buf);
    }
}
