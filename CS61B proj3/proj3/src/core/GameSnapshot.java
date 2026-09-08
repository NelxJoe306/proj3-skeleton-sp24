package core;

import java.util.Arrays;

/**
 * The serializable result of replaying a {@link Session}: everything the browser
 * needs to draw one frame and show the HUD. Pure data — built by the engine from
 * the current runtime grid, never carrying structure objects (ADR-0001).
 *
 * <p>{@code rows[y][x]} is the tile id of the cell at grid column {@code x}, row
 * {@code y} (screen up = higher {@code y}, as in the engine). {@code legend}
 * explains every id that can appear, with its display name and palette, so the
 * web client never hard-codes tiles. {@code avatar}/{@code goal} are {@code [x, y]}
 * or {@code null} (an empty/load-missing world has no avatar).
 */
public final class GameSnapshot {

    public final int width;
    public final int height;
    public final long seed;
    public final boolean won;
    public final boolean saveMissing;
    public final int[] avatar;
    public final int[] goal;
    public final int[][] rows;
    public final Legend[] legend;

    public GameSnapshot(int width, int height, long seed, boolean won, boolean saveMissing,
                        int[] avatar, int[] goal, int[][] rows, Legend[] legend) {
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.won = won;
        this.saveMissing = saveMissing;
        this.avatar = avatar;
        this.goal = goal;
        this.rows = rows;
        this.legend = legend;
    }

    /** One legend entry: how to draw the tile with this id. Colors are CSS hex. */
    public static final class Legend {
        public final int id;
        public final String name;
        /** The exact character the engine renders for this tile (e.g. '@', '#', '·';
         *  the void tile's glyph is a single space), so the client never hard-codes tiles. */
        public final String glyph;
        public final String fg;
        public final String bg;

        public Legend(int id, String name, String glyph, String fg, String bg) {
            this.id = id;
            this.name = name;
            this.glyph = glyph;
            this.fg = fg;
            this.bg = bg;
        }
    }

    @Override
    public String toString() {
        return "GameSnapshot{" + width + "x" + height + ", seed=" + seed
                + ", avatar=" + Arrays.toString(avatar) + ", goal=" + Arrays.toString(goal)
                + ", won=" + won + ", saveMissing=" + saveMissing + "}";
    }
}
