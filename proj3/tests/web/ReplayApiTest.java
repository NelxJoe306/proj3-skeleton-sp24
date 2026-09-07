package web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import core.GameSnapshot;
import core.Replay;
import core.Session;
import core.WorldSize;
import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tileengine.Tileset;

/**
 * Ticket 04 — the HTTP adapter's pure logic ({@link ReplayApi}), tested without a socket:
 * valid requests produce the snapshot envelope; malformed ones produce readable 4xx. The
 * live HTTP wiring is exercised end-to-end by {@link ServerSmoke} (separate process).
 */
public class ReplayApiTest {

    @AfterEach
    void deleteSaveFile() {
        new File("save.txt").delete();
    }

    @Test
    void aValidNewGameReturnsTheSnapshotEnvelope() {
        JsonObject out = JsonParser.parseString(ReplayApi.handle(
                "{\"kind\":\"new\",\"size\":\"medium\",\"seed\":42,\"keys\":\"DD\"}").body)
                .getAsJsonObject();
        assertEquals(80, out.get("width").getAsInt());
        assertEquals(80, out.get("height").getAsInt());
        assertEquals(42L, out.get("seed").getAsLong());
        assertTrue(out.get("avatar").isJsonArray(), "avatar present after DD");

        GameSnapshot expected = Replay.replay(Session.newGame(WorldSize.medium, 42)
                .withKeys("DD"));
        assertEquals(expected.avatar[0], out.getAsJsonArray("avatar").get(0).getAsInt());
        assertEquals(expected.avatar[1], out.getAsJsonArray("avatar").get(1).getAsInt());

        JsonArray rows = out.getAsJsonArray("rows");
        assertEquals(80, rows.size(), "rows has one array per grid row");
        assertEquals(80, rows.get(0).getAsJsonArray().size(), "each row spans the full width");
        assertEquals(Tileset.AVATAR.id(), rows.get(expected.avatar[1]).getAsJsonArray()
                .get(expected.avatar[0]).getAsInt(), "the grid shows the avatar tile");

        JsonArray legend = out.getAsJsonArray("legend");
        assertTrue(legend.size() > 0, "legend covers the tiles present");
        for (var entry : legend) {
            JsonObject l = entry.getAsJsonObject();
            assertTrue(l.get("fg").getAsString().matches("#[0-9a-f]{6}"), "fg is #rrggbb");
            assertTrue(l.get("bg").getAsString().matches("#[0-9a-f]{6}"), "bg is #rrggbb");
        }
    }

    @Test
    void aReplayPostNeedsASeedForANewGame() {
        ReplayApi.Result r = ReplayApi.handle("{\"kind\":\"new\",\"size\":\"large\"}");
        assertEquals(400, r.status);
        assertTrue(r.body.contains("seed"), "message explains the missing seed");
    }

    @Test
    void anUnknownSizeIsAReadable400() {
        ReplayApi.Result r = ReplayApi.handle("{\"kind\":\"new\",\"size\":\"huge\",\"seed\":1}");
        assertEquals(400, r.status);
        assertTrue(r.body.contains("size"), "message names the bad field");
    }

    @Test
    void aBadSeedAndBadJsonAreReadable400s() {
        assertEquals(400, ReplayApi.handle("{\"kind\":\"new\",\"size\":\"medium\",\"seed\":\"abc\"}").status);
        assertEquals(400, ReplayApi.handle("this is not json").status);
        assertEquals(400, ReplayApi.handle("{\"kind\":\"resume\"}").status);
    }

    @Test
    void loadWithNoSaveReportsSaveMissing() {
        new File("save.txt").delete();
        JsonObject out = JsonParser.parseString(ReplayApi.handle("{\"kind\":\"load\"}").body)
                .getAsJsonObject();
        assertTrue(out.get("saveMissing").getAsBoolean(), "no save means saveMissing=true");
        assertTrue(out.get("avatar").isJsonNull(), "no avatar in the empty world");
    }
}
