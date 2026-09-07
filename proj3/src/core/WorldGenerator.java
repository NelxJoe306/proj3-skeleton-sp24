package core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import tileengine.TETile;
import tileengine.Tileset;

/**
 * Owns the structure-layer layout of a world. Everything here must be a pure
 * function of the seed — the RNG used must derive solely from {@code seed} so
 * that the same seed always reproduces the same world (ADR-0002).
 *
 * Layout strategy (tickets 05–07): a recursive binary-space partition (BSP)
 * divides the canvas into rectangular leaves. Every leaf becomes one room — a
 * solid rectangle of FLOOR wrapped in a 1-tile WALL ring — with a thin NOTHING
 * gutter left between rooms. A spanning tree over the leaf-adjacency graph then
 * joins the rooms with 1-tile-wide corridors carved through the gutters and the
 * two intervening wall rings (ticket 06), so every floor tile is reachable from
 * every other and no corridor dead-ends.
 *
 * <p>Rooms never touch the outer canvas edge: an all-NOTHING border ring is
 * reserved first, and each room is inset inside its leaf, so no FLOOR sits on
 * the boundary.
 */
public final class WorldGenerator {

    /** Width of the all-NOTHING border around the whole grid. */
    private static final int OUTER_VOID = 2;
    /** Smallest side length a leaf may keep after a split. */
    private static final int MIN_LEAF = 20;
    /** An axis is split further only while it is at least this long, so leaves end up in [MIN_LEAF, SPLIT_DIM]. */
    private static final int SPLIT_DIM = 2 * MIN_LEAF + 1;
    /** Room floor side is never smaller than this. */
    private static final int MIN_ROOM = 5;
    /** Gutter (NOTHING) width left on each side of a room inside its leaf. */
    private static final int MAX_GUTTER = 3;

    private WorldGenerator() {
    }

    /** Axis-aligned rectangle in grid coordinates (x, y are the bottom-left corner). */
    static final class Rect {
        final int x;
        final int y;
        final int w;
        final int h;

        Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        /** True if this rect and {@code o} share a boundary segment at least one cell long. */
        boolean sharesSeamWith(Rect o) {
            if (x + w == o.x || o.x + o.w == x) {
                return Math.min(y + h, o.y + o.h) - Math.max(y, o.y) >= 1;
            }
            if (y + h == o.y || o.y + o.h == y) {
                return Math.min(x + w, o.x + o.w) - Math.max(x, o.x) >= 1;
            }
            return false;
        }
    }

    /** The result of a layout pass: the structure-layer grid plus every leaf and room carved. */
    static final class Layout {
        final TETile[][] tiles;
        final List<Rect> leaves;
        final List<Rect> rooms;

        Layout(TETile[][] tiles, List<Rect> leaves, List<Rect> rooms) {
            this.tiles = tiles;
            this.leaves = leaves;
            this.rooms = rooms;
        }
    }

    public static TETile[][] generate(long seed, int width, int height) {
        return layout(seed, width, height).tiles;
    }

    static Layout layout(long seed, int width, int height) {
        TETile[][] tiles = new TETile[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = Tileset.NOTHING;
            }
        }
        Random rng = new Random(seed);
        List<Rect> leaves = new ArrayList<>();
        List<Rect> rooms = new ArrayList<>();
        split(rng, tiles, leaves, rooms, OUTER_VOID, OUTER_VOID,
                width - 2 * OUTER_VOID, height - 2 * OUTER_VOID);
        connectRooms(tiles, leaves, rooms, rng);
        return new Layout(tiles, leaves, rooms);
    }

    /**
     * Recursively partitions the half-open rect {@code (x, y, w, h)} until no
     * axis is splittable, then stamps a room into the leaf. Each cut runs along
     * one axis at a random offset, leaving children no smaller than MIN_LEAF.
     */
    private static void split(Random rng, TETile[][] tiles, List<Rect> leaves, List<Rect> rooms,
                              int x, int y, int w, int h) {
        boolean canVertical = w >= SPLIT_DIM;
        boolean canHorizontal = h >= SPLIT_DIM;

        boolean vertical;
        if (canVertical && canHorizontal) {
            vertical = rng.nextBoolean();
        } else if (canVertical) {
            vertical = true;
        } else if (canHorizontal) {
            vertical = false;
        } else {
            placeRoom(rng, tiles, leaves, rooms, x, y, w, h);
            return;
        }

        if (vertical) {
            int cut = MIN_LEAF + rng.nextInt(w - 2 * MIN_LEAF);
            split(rng, tiles, leaves, rooms, x, y, cut, h);
            split(rng, tiles, leaves, rooms, x + cut, y, w - cut, h);
        } else {
            int cut = MIN_LEAF + rng.nextInt(h - 2 * MIN_LEAF);
            split(rng, tiles, leaves, rooms, x, y, w, cut);
            split(rng, tiles, leaves, rooms, x, y + cut, w, h - cut);
        }
    }

    /** Stamps one walled room into the leaf; the random inset leaves a NOTHING gutter around it. */
    private static void placeRoom(Random rng, TETile[][] tiles, List<Rect> leaves, List<Rect> rooms,
                                  int x, int y, int w, int h) {
        int left = 2 + rng.nextInt(MAX_GUTTER - 1);  // 2..3 tiles of gutter
        int right = 2 + rng.nextInt(MAX_GUTTER - 1);
        int bottom = 2 + rng.nextInt(MAX_GUTTER - 1);
        int top = 2 + rng.nextInt(MAX_GUTTER - 1);
        int fw = w - left - right;
        int fh = h - bottom - top;
        if (fw < MIN_ROOM || fh < MIN_ROOM) {
            return; // degenerate leaf — leave it void; never reached with current dims
        }
        int fx = x + left;
        int fy = y + bottom;
        carveRoom(tiles, fx, fy, fw, fh);
        leaves.add(new Rect(x, y, w, h));
        rooms.add(new Rect(fx, fy, fw, fh));
    }

    /** Draws a FLOOR rect plus a 1-tile WALL ring one cell around it, clipped to the grid. */
    private static void carveRoom(TETile[][] tiles, int fx, int fy, int fw, int fh) {
        int width = tiles.length;
        int height = tiles[0].length;
        for (int px = fx - 1; px <= fx + fw; px++) {
            for (int py = fy - 1; py <= fy + fh; py++) {
                if (px < 0 || py < 0 || px >= width || py >= height) {
                    continue;
                }
                boolean wall = px == fx - 1 || px == fx + fw || py == fy - 1 || py == fy + fh;
                tiles[px][py] = wall ? Tileset.WALL : Tileset.FLOOR;
            }
        }
    }

    // ------------------------------------------------------------------ corridor

    /**
     * Walks the leaf-adjacency graph from leaf 0 in deterministic DFS order and
     * carves one corridor for every tree edge, so the rooms form a spanning tree:
     * all floor connected, no corridor between two rooms can dead-end.
     *
     * <p>Each edge may be carved straight (when the two rooms line up) or as a
     * deliberate L with a guaranteed corner — ticket 07 wants a healthy share of
     * worlds to contain a turning corridor.
     */
    private static void connectRooms(TETile[][] tiles, List<Rect> leaves, List<Rect> rooms, Random rng) {
        int n = leaves.size();
        if (n < 2) {
            return;
        }
        boolean[] visited = new boolean[n];
        java.util.ArrayDeque<Integer> stack = new java.util.ArrayDeque<>();
        visited[0] = true;
        stack.push(0);
        while (!stack.isEmpty()) {
            int cur = stack.pop();
            for (int k = 0; k < n; k++) {
                if (!visited[k] && leaves.get(cur).sharesSeamWith(leaves.get(k))) {
                    visited[k] = true;
                    stack.push(k);
                    connect(tiles, rng, leaves.get(cur), rooms.get(cur), leaves.get(k), rooms.get(k));
                }
            }
        }
    }

    /** Carves a 1-wide corridor between two rooms across the seam their leaves share. */
    private static void connect(TETile[][] tiles, Random rng,
                                Rect leafA, Rect roomA, Rect leafB, Rect roomB) {
        if (leafA.x + leafA.w == leafB.x) {
            connectAcrossVerticalSeam(tiles, rng, roomA, roomB, leafA.x + leafA.w);
        } else if (leafB.x + leafB.w == leafA.x) {
            connectAcrossVerticalSeam(tiles, rng, roomB, roomA, leafB.x + leafB.w);
        } else if (leafA.y + leafA.h == leafB.y) {
            connectAcrossHorizontalSeam(tiles, rng, roomA, roomB, leafA.y + leafA.h);
        } else {
            connectAcrossHorizontalSeam(tiles, rng, roomB, roomA, leafB.y + leafB.h);
        }
    }

    /**
     * Joins a west room to an east room across a vertical seam at column {@code xs}.
     * Straight when the rooms' rows line up, unless the RNG asks for a turn; an
     * L-shaped path otherwise.
     */
    private static void connectAcrossVerticalSeam(TETile[][] tiles, Random rng, Rect west, Rect east, int xs) {
        int lo = Math.max(west.y, east.y);
        int hi = Math.min(west.y + west.h - 1, east.y + east.h - 1);
        if (lo <= hi && !rng.nextBoolean()) {
            carveRow(tiles, west.x + west.w - 1, east.x, (lo + hi) / 2);
        } else {
            carveTurningVertical(tiles, west, east, xs);
        }
    }

    /**
     * Joins a room below to a room above across a horizontal seam at row {@code ys}.
     * Mirrors the vertical case.
     */
    private static void connectAcrossHorizontalSeam(TETile[][] tiles, Random rng, Rect below, Rect above, int ys) {
        int lo = Math.max(below.x, above.x);
        int hi = Math.min(below.x + below.w - 1, above.x + above.w - 1);
        if (lo <= hi && !rng.nextBoolean()) {
            carveCol(tiles, (lo + hi) / 2, below.y + below.h - 1, above.y);
        } else {
            carveTurningHorizontal(tiles, below, above, ys);
        }
    }

    /**
     * An L-shaped corridor across a vertical seam: out of the west room near one
     * extreme of its height, along the seam lane (the NOTHING column at {@code xs - 1}),
     * then back into the east room at the opposite extreme — a guaranteed corner.
     */
    private static void carveTurningVertical(TETile[][] tiles, Rect west, Rect east, int xs) {
        int lane = xs - 1;
        int yW = west.y + west.h / 2;
        int yE = east.y + east.h / 2;
        if (yW == yE) {
            yE = Math.max(east.y, Math.min(east.y + east.h - 1, yW + 2));
        }
        carveRow(tiles, west.x + west.w - 1, lane, yW);
        carveCol(tiles, lane, Math.min(yW, yE), Math.max(yW, yE));
        carveRow(tiles, lane, east.x, yE);
    }

    /** Mirror of {@link #carveTurningVertical} for a horizontal seam at row {@code ys}. */
    private static void carveTurningHorizontal(TETile[][] tiles, Rect below, Rect above, int ys) {
        int lane = ys - 1;
        int xW = below.x + below.w / 2;
        int xE = above.x + above.w / 2;
        if (xW == xE) {
            xE = Math.max(above.x, Math.min(above.x + above.w - 1, xW + 2));
        }
        carveCol(tiles, xW, below.y + below.h - 1, lane);
        carveRow(tiles, Math.min(xW, xE), Math.max(xW, xE), lane);
        carveCol(tiles, xE, lane, above.y);
    }

    private static void carveRow(TETile[][] tiles, int x1, int x2, int y) {
        int lo = Math.min(x1, x2);
        int hi = Math.max(x1, x2);
        for (int x = lo; x <= hi; x++) {
            tiles[x][y] = Tileset.FLOOR;
        }
    }

    private static void carveCol(TETile[][] tiles, int x, int y1, int y2) {
        int lo = Math.min(y1, y2);
        int hi = Math.max(y1, y2);
        for (int y = lo; y <= hi; y++) {
            tiles[x][y] = Tileset.FLOOR;
        }
    }
}
