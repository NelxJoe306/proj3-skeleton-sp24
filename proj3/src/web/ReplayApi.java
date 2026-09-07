package web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import core.GameSnapshot;
import core.Replay;
import core.Session;
import core.WorldSize;

/**
 * The thin HTTP-facing adapter for the 3C replay seam (ADR-0004): one pure method
 * turning a request body into a response. Validation lives here so the exact same
 * behaviour is unit-testable without a socket; the Spark route is a 3-line passthrough.
 * No session is held — each request carries its whole {@link Session} descriptor.
 */
public final class ReplayApi {

    // serializeNulls keeps every snapshot key present (avatar/goal become explicit null in
    // an empty world) — a stable envelope the browser client can rely on (tickets 05+).
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ReplayApi() {
    }

    /** A response: HTTP status plus the body to send. */
    public static final class Result {
        public final int status;
        public final String body;

        Result(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    /**
     * Parses and validates a Session JSON body, replays it, and returns the GameSnapshot
     * JSON on 200. Malformed JSON, unknown kind/size, a bad seed, or a missing seed for a
     * new game all yield a readable 4xx without ever touching the engine.
     */
    public static Result handle(String body) {
        if (body == null || body.isBlank()) {
            return error(400, "empty request body: send a Session JSON object");
        }
        JsonObject obj;
        try {
            obj = JsonParser.parseString(body).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            return error(400, "body is not a JSON object: " + brief(e));
        }

        Session session = new Session();
        if (obj.has("kind")) {
            String kind;
            try {
                kind = obj.get("kind").getAsString();
            } catch (IllegalStateException e) {
                return error(400, "\"kind\" must be a string");
            }
            if (!"new".equalsIgnoreCase(kind) && !"load".equalsIgnoreCase(kind)) {
                return error(400, "unknown kind \"" + kind + "\" (expected \"new\" or \"load\")");
            }
            session.kind = kind.toLowerCase();
        }
        if ("load".equals(session.kind)) {
            // size/seed come from the file; anything the client sent is ignored.
        } else {
            if (obj.has("size")) {
                WorldSize size = parseSize(obj.get("size"));
                if (size == null) {
                    return error(400, "unknown size (expected small | medium | large)");
                }
                session.size = size;
            }
            if (!obj.has("seed")) {
                return error(400, "\"seed\" is required for a new game");
            }
            try {
                session.seed = obj.get("seed").getAsLong();
            } catch (IllegalStateException | NumberFormatException e) {
                return error(400, "\"seed\" must be an integer");
            }
        }
        if (obj.has("keys")) {
            try {
                session.keys = obj.get("keys").getAsString();
            } catch (IllegalStateException e) {
                return error(400, "\"keys\" must be a string");
            }
        }

        GameSnapshot snap = Replay.replay(session);
        return new Result(200, GSON.toJson(snap));
    }

    private static WorldSize parseSize(com.google.gson.JsonElement el) {
        String raw;
        try {
            raw = el.getAsString();
        } catch (IllegalStateException e) {
            return null;
        }
        for (WorldSize size : WorldSize.values()) {
            if (size.name().equalsIgnoreCase(raw)) {
                return size;
            }
        }
        return null;
    }

    private static Result error(int status, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return new Result(status, GSON.toJson(o));
    }

    private static String brief(Exception e) {
        String m = e.getMessage();
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
}
