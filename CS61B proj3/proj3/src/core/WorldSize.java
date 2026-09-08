package core;

/**
 * The player-selectable map sizes (CONTEXT: "尺寸档"). Constant names are
 * lowercase on purpose — they are the wire form of the session descriptor, so a
 * JSON round-trip keeps the friendly {@code small|medium|large} strings verbatim.
 */
public enum WorldSize {
    small(40, 40),
    medium(80, 80),
    large(120, 120);

    private final int width;
    private final int height;

    WorldSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The tier for exact dimensions, or {@code medium} if they match no tier. */
    public static WorldSize forDimensions(int width, int height) {
        for (WorldSize s : values()) {
            if (s.width == width && s.height == height) {
                return s;
            }
        }
        return medium;
    }
}
