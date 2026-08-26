package net.minecraft.core;

public class Cursor3D {
    public static final int TYPE_INSIDE = 0;
    public static final int TYPE_FACE = 1;
    public static final int TYPE_EDGE = 2;
    public static final int TYPE_CORNER = 3;
    // MCRe NoiseFarlands: 世界坐标 Long 化
    private final long originX;
    private final long originY;
    private final long originZ;
    private final long width;
    private final long height;
    private final long depth;
    private final long end;
    private long index;
    private long x;
    private long y;
    private long z;

    public Cursor3D(final long minX, final long minY, final long minZ, final long maxX, final long maxY, final long maxZ) {
        this.originX = minX;
        this.originY = minY;
        this.originZ = minZ;
        this.width = maxX - minX + 1;
        this.height = maxY - minY + 1;
        this.depth = maxZ - minZ + 1;
        this.end = this.width * this.height * this.depth;
    }

    public boolean advance() {
        if (this.index == this.end) {
            return false;
        }

        this.x = this.index % this.width;
        long slice = this.index / this.width;
        this.y = slice % this.height;
        this.z = slice / this.height;
        this.index++;
        return true;
    }

    public long nextX() {
        return this.originX + this.x;
    }

    public long nextY() {
        return this.originY + this.y;
    }

    public long nextZ() {
        return this.originZ + this.z;
    }

    public int getNextType() {
        int type = 0;
        if (this.x == 0 || this.x == this.width - 1) {
            type++;
        }

        if (this.y == 0 || this.y == this.height - 1) {
            type++;
        }

        if (this.z == 0 || this.z == this.depth - 1) {
            type++;
        }

        return type;
    }
}