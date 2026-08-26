package net.minecraft.world.level;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

public interface LevelHeightAccessor {
    int getHeight();

    int getMinY();

    default int getMaxY() {
        return this.getMinY() + this.getHeight() - 1;
    }

    // MCRe NoiseFarlands: section 数量为高度配置域 int，差值运算后强转（安全）
    default int getSectionsCount() {
        return (int) (this.getMaxSectionY() - this.getMinSectionY() + 1);
    }

    // MCRe NoiseFarlands: section Y 坐标为世界域，Long 化
    default long getMinSectionY() {
        return SectionPos.blockToSectionCoord(this.getMinY());
    }

    default long getMaxSectionY() {
        return SectionPos.blockToSectionCoord(this.getMaxY());
    }

    default boolean isInsideBuildHeight(final BlockPos pos) {
        return this.isInsideBuildHeight(pos.getY());
    }

    default boolean isInsideBuildHeight(final long blockY) {
        return blockY >= this.getMinY() && blockY <= this.getMaxY();
    }

    default boolean isInsideBuildHeight(final int blockY) {
        return this.isInsideBuildHeight((long) blockY);
    }

    default boolean isOutsideBuildHeight(final BlockPos pos) {
        return this.isOutsideBuildHeight(pos.getY());
    }

    default boolean isOutsideBuildHeight(final long blockY) {
        return blockY < this.getMinY() || blockY > this.getMaxY();
    }

    default boolean isOutsideBuildHeight(final int blockY) {
        return this.isOutsideBuildHeight((long) blockY);
    }

    default int getSectionIndex(final int blockY) {
        return this.getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(blockY));
    }

    default int getSectionIndex(final long blockY) {
        return this.getSectionIndexFromSectionY(SectionPos.blockToSectionCoord(blockY));
    }

    default int getSectionIndexFromSectionY(final int sectionY) {
        // MCRe NoiseFarlands: section 数组索引 int 域边界
        return (int) (sectionY - this.getMinSectionY());
    }

    default int getSectionIndexFromSectionY(final long sectionY) {
        // MCRe NoiseFarlands: sectionY Long 化入参，返回值为 0-24 数组索引保持 int
        return Math.toIntExact(sectionY - this.getMinSectionY());
    }

    // MCRe NoiseFarlands: section Y 坐标 Long 化
    default long getSectionYFromSectionIndex(final int sectionIndex) {
        return sectionIndex + this.getMinSectionY();
    }

    static LevelHeightAccessor create(final int minY, final int height) {
        return new LevelHeightAccessor() {
            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public int getMinY() {
                return minY;
            }
        };
    }
}