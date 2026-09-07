package proj3b;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;
import tileengine.TETile;

/**
 * Ticket 04: 文法宽容 — 未知/非法字符静默忽略，网格与去掉它们的输入串逐格全等。
 * 前缀校验严、正文宽容：坏的 {@code N…S} 前缀仍抛 IAE。文法字母大小写等价。
 */
public class GrammarToleranceTest {

    private static final long[] SEEDS = {7, 42, 123, 987654321};

    private static TETile[][] get(String input) {
        return AutograderBuddy.getWorldFromInput(input);
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

    /** Asserts a junked input produces the same grid as the same keys without the junk. */
    private static void assertJunkEquivalent(long seed, String cleanTail, String junkedTail) {
        String clean = "n" + seed + "s" + cleanTail;
        String junked = "n" + seed + "s" + junkedTail;
        assertTrue(gridsEqual(get(clean), get(junked)),
                "seed " + seed + ": " + junkedTail + " should equal clean " + cleanTail);
    }

    @Test
    void unknownMoveKeysAreSilentlyIgnored() {
        for (long seed : SEEDS) {
            assertJunkEquivalent(seed, "W", "Wb");       // unknown letter after a real move
            assertJunkEquivalent(seed, "WA", "WbAz");    // junk sprinkled between moves
        }
    }

    @Test
    void loneColonAndNonQColonSequencesAreIgnored() {
        for (long seed : SEEDS) {
            assertJunkEquivalent(seed, "W", "W:");      // trailing lone colon
            assertJunkEquivalent(seed, "W", "W:x");     // colon + non-q letter
            assertJunkEquivalent(seed, "WA", "W:xA");   // colon + non-q between two moves
        }
    }

    @Test
    void bareQNotAfterColonIsIgnored() {
        for (long seed : SEEDS) {
            assertJunkEquivalent(seed, "W", "Wq");
            assertJunkEquivalent(seed, "W", "WQ");
        }
    }

    @Test
    void whitespaceAndPunctuationAreIgnored() {
        for (long seed : SEEDS) {
            assertJunkEquivalent(seed, "WA", "W A?");
            assertJunkEquivalent(seed, "WA", "W:;A#");
        }
    }

    @Test
    void unknownCharactersCauseNoErrorAndLaterKeysStillAct() {
        // Same final avatar position whether or not junk interrupts.
        TETile[][] a = get("n7sW:?zbA");
        TETile[][] b = get("n7sWA");
        assertTrue(gridsEqual(a, b), "junk between W and A must not break the second move");
    }

    @Test
    void badSeedPrefixStillThrows() {
        // Tolerance is for the command body only — the N<digits>S prefix stays strict.
        assertThrows(IllegalArgumentException.class, () -> get("5WASD"));
        assertThrows(IllegalArgumentException.class, () -> get("n12"));
        assertThrows(IllegalArgumentException.class, () -> get("WASD"));
    }
}
