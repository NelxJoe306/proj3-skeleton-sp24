package proj3a;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import core.AutograderBuddy;
import org.junit.jupiter.api.Test;

/** Ticket 01: 入参校验 — 非法 seed 字符串抛明确异常（IAE），不静默。 */
public class InputValidationTest {

    private static void assertRejectedWithMessage(String input, String... needles) {
        IllegalArgumentException e = assertThrows(
                IllegalArgumentException.class,
                () -> AutograderBuddy.getWorldFromInput(input));
        for (String n : needles) {
            assertTrue(e.getMessage().toLowerCase().contains(n.toLowerCase()),
                    "expected message to mention '" + n + "' but was: " + e.getMessage());
        }
    }

    @Test
    void rejectsNull() {
        assertRejectedWithMessage(null, "null");
    }

    @Test
    void rejectsEmptyAndWhitespace() {
        assertRejectedWithMessage("", "seed");
        assertRejectedWithMessage("   ", "seed");
    }

    @Test
    void rejectsInputMissingLeadingN() {
        assertRejectedWithMessage("12345s", "seed", "N");
    }

    @Test
    void rejectsInputMissingClosingS() {
        assertRejectedWithMessage("n12345", "seed", "S");
    }

    @Test
    void rejectsNonNumericSeed() {
        assertRejectedWithMessage("nabc12s", "seed");
        assertRejectedWithMessage("n123x45s", "seed");
    }

    @Test
    void rejectsEmptyDigitSpan() {
        assertRejectedWithMessage("ns", "seed");
    }
}
