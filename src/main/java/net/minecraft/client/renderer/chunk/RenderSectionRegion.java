package net.minecraft.client.renderer.chunk;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class RenderSectionRegion implements BlockAndTintGetter {
    public static final int RADIUS = 1;
    public static final int SIZE = 3;
    private final long minSectionX;
    private final long minSectionY;
    private final long minSectionZ;
    private final SectionCopy[] sections;
    private final ClientLevel level;
    private final CardinalLighting cardinalLighting;
    private final LevelLightEngine lightEngine;

    public RenderSectionRegion(final ClientLevel level, final long minSectionX, final long minSectionY, final long minSectionZ, final SectionCopy[] sections) {
        this.level = level;
        this.minSectionX = minSectionX;
        this.minSectionY = minSectionY;
        this.minSectionZ = minSectionZ;
        this.sections = sections;
        this.cardinalLighting = level.cardinalLighting();
        this.lightEngine = level.getLightEngine();
    }

    @Override
    public BlockState getBlockState(final BlockPos pos) {
        return this.getSectionRelative(
                this.relativeSection(pos.getX(), this.minSectionX),
                this.relativeSection(pos.getY(), this.minSectionY),
                this.relativeSection(pos.getZ(), this.minSectionZ)
            )
            .getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(final BlockPos pos) {
        return this.getSectionRelative(
                this.relativeSection(pos.getX(), this.minSectionX),
                this.relativeSection(pos.getY(), this.minSectionY),
                this.relativeSection(pos.getZ(), this.minSectionZ)
            )
            .getBlockState(pos)
            .getFluidState();
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return this.cardinalLighting;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.lightEngine;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(final BlockPos pos) {
        return this.getSectionRelative(
                this.relativeSection(pos.getX(), this.minSectionX),
                this.relativeSection(pos.getY(), this.minSectionY),
                this.relativeSection(pos.getZ(), this.minSectionZ)
            )
            .getBlockEntity(pos);
    }

    private SectionCopy getSectionRelative(final int relSectionX, final int relSectionY, final int relSectionZ) {
        return this.sections[relSectionX + relSectionY * 3 + relSectionZ * 9];
    }

    // MCRe NoiseFarlands: getX() 已真 Long 化，原 mod 2^32 恢复 hack 不再需要，直接算相对偏移（region 3×3 范围内）
    private int relativeSection(final long posCoord, final long minSectionCoord) {
        return (int) ((posCoord >> 4) - minSectionCoord);
    }

    @Override
    public int getBlockTint(final BlockPos pos, final ColorResolver resolver) {
        return this.level.getBlockTint(pos, resolver);
    }

    @Override
    public int getMinY() {
        return this.level.getMinY();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    public static int index(final long minSectionX, final long minSectionY, final long minSectionZ, final long sectionX, final long sectionY, final long sectionZ) {
        // MCRe NoiseFarlands: 相对索引 0~26，int 域边界
        return (int) (sectionX - minSectionX + (sectionY - minSectionY) * 3 + (sectionZ - minSectionZ) * 3 * 3);
    }
}