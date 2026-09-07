package core;

/**
 * A session descriptor: everything needed to rebuild a 3C world from scratch.
 * Stateless replay (ADR-0004) — a browser holds one of these and resends the
 * whole descriptor (with an appended key) on every turn; the server/headless
 * caller derives the world from it as a pure function.
 *
 * <p>{@code kind} is {@code "new"} (build from {@code size}+{@code seed}) or
 * {@code "load"} (read the last {@code :Q} save and continue). For a load, the
 * size/seed come from the file, so those fields are ignored. {@code keys} is the
 * cumulative typed command string (WASD and {@code :}/{@code Q}).
 */
public class Session {

    public String kind = "new";
    public WorldSize size = WorldSize.medium;
    public long seed = 0L;
    public String keys = "";

    public Session() {
    }

    /** A brand-new session on a fresh world. */
    public static Session newGame(WorldSize size, long seed) {
        Session s = new Session();
        s.kind = "new";
        s.size = size;
        s.seed = seed;
        return s;
    }

    /** A load session continuing the last {@code :Q} save. */
    public static Session load() {
        Session s = new Session();
        s.kind = "load";
        return s;
    }

    /** A copy of this session with {@code more} appended to the key stream. */
    public Session withKeys(String more) {
        Session s = new Session();
        s.kind = kind;
        s.size = size;
        s.seed = seed;
        s.keys = keys + (more == null ? "" : more);
        return s;
    }
}
