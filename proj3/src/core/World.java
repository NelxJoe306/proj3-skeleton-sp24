package core;

import tileengine.TETile;

/**
 * The world as a domain value: a fixed-size structure-layer grid derived purely
 * from its seed. The facade over {@link WorldGenerator}; exposes the tiles the
 * renderer / autograder consume. A world carries no presentation or runtime
 * state (ADR-0001, ADR-0002).
 */
public class World {

    public static final int DEFAULT_WIDTH = 80;
    public static final int DEFAULT_HEIGHT = 80;

    private final long seed;
    private final int width;
    private final int height;
    private final TETile[][] tiles;

    private World(long seed, int width, int height, TETile[][] tiles) {
        this.seed = seed;
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    public static World generate(long seed) {
        return new World(seed, DEFAULT_WIDTH, DEFAULT_HEIGHT,
                WorldGenerator.generate(seed, DEFAULT_WIDTH, DEFAULT_HEIGHT));
    }

    public long seed() {
        return seed;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The structure-layer grid; mutable arrays are shared by convention with
     *  the renderer/tests that draw it. */
    public TETile[][] tiles() {
        return tiles;
    }
}
