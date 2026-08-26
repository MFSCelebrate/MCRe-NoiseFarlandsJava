package net.minecraft.core;

public final class QuartPos {
    public static final int BITS = 2;
    public static final int SIZE = 4;
    public static final int MASK = 3;
    private static final int SECTION_TO_QUARTS_BITS = 2;

    private QuartPos() {
    }

    // MCRe NoiseFarlands: 全签名 Long 化（int 实参自动无损提升）
    public static long fromBlock(final long blockCoord) {
        return blockCoord >> 2;
    }

    public static int quartLocal(final long blockCoord) {
        return (int) (blockCoord & 3);
    }

    public static long toBlock(final long quart) {
        return quart << 2;
    }

    public static long fromSection(final long section) {
        return section << 2;
    }

    public static long toSection(final long quart) {
        return quart >> 2;
    }
}