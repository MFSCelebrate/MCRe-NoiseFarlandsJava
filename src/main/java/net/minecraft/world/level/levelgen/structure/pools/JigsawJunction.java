package net.minecraft.world.level.levelgen.structure.pools;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

public class JigsawJunction {
    // MCRe NoiseFarlands: junction 世界坐标 Long 化
    private final long sourceX;
    private final long sourceGroundY;
    private final long sourceZ;
    private final long deltaY;
    private final StructureTemplatePool.Projection destProjection;

    public JigsawJunction(
        final long sourceX, final long sourceGroundY, final long sourceZ, final long deltaY, final StructureTemplatePool.Projection destProjection
    ) {
        this.sourceX = sourceX;
        this.sourceGroundY = sourceGroundY;
        this.sourceZ = sourceZ;
        this.deltaY = deltaY;
        this.destProjection = destProjection;
    }

    public long getSourceX() {
        return this.sourceX;
    }

    public long getSourceGroundY() {
        return this.sourceGroundY;
    }

    public long getSourceZ() {
        return this.sourceZ;
    }

    public long getDeltaY() {
        return this.deltaY;
    }

    public StructureTemplatePool.Projection getDestProjection() {
        return this.destProjection;
    }

    public <T> Dynamic<T> serialize(final DynamicOps<T> ops) {
        Builder<T, T> builder = ImmutableMap.builder();
        // MCRe NoiseFarlands: NBT 序列化保持 IntTag（Decision 4 存档兼容）
        builder.put(ops.createString("source_x"), ops.createInt((int) this.sourceX))
            .put(ops.createString("source_ground_y"), ops.createInt((int) this.sourceGroundY))
            .put(ops.createString("source_z"), ops.createInt((int) this.sourceZ))
            .put(ops.createString("delta_y"), ops.createInt((int) this.deltaY))
            .put(ops.createString("dest_proj"), ops.createString(this.destProjection.getName()));
        return new Dynamic<>(ops, ops.createMap(builder.build()));
    }

    public static <T> JigsawJunction deserialize(final Dynamic<T> input) {
        return new JigsawJunction(
            input.get("source_x").asInt(0),
            input.get("source_ground_y").asInt(0),
            input.get("source_z").asInt(0),
            input.get("delta_y").asInt(0),
            StructureTemplatePool.Projection.byName(input.get("dest_proj").asString(""))
        );
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o != null && this.getClass() == o.getClass()) {
            JigsawJunction that = (JigsawJunction)o;
            if (this.sourceX != that.sourceX) {
                return false;
            } else if (this.sourceZ != that.sourceZ) {
                return false;
            } else {
                return this.deltaY != that.deltaY ? false : this.destProjection == that.destProjection;
            }
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // MCRe NoiseFarlands: Long.hashCode 混合防碰撞退化
        int result = Long.hashCode(this.sourceX);
        result = 31 * result + Long.hashCode(this.sourceGroundY);
        result = 31 * result + Long.hashCode(this.sourceZ);
        result = 31 * result + Long.hashCode(this.deltaY);
        return 31 * result + this.destProjection.hashCode();
    }

    @Override
    public String toString() {
        return "JigsawJunction{sourceX="
            + this.sourceX
            + ", sourceGroundY="
            + this.sourceGroundY
            + ", sourceZ="
            + this.sourceZ
            + ", deltaY="
            + this.deltaY
            + ", destProjection="
            + this.destProjection
            + "}";
    }
}