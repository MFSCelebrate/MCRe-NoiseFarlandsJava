package net.minecraft.world.level.chunk;



import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

public class BulkSectionAccess implements AutoCloseable {
    private final LevelAccessor level;
    private final Map<SectionPos, LevelChunkSection> acquiredSections = new HashMap<>();
    private @Nullable LevelChunkSection lastSection;
    private SectionPos lastSectionKey;

    public BulkSectionAccess(final LevelAccessor level) {
        this.level = level;
    }

    public @Nullable LevelChunkSection getSection(final BlockPos pos) {
        int sectionIndex = this.level.getSectionIndex(pos.getY());
        if (sectionIndex >= 0 && sectionIndex < this.level.getSectionsCount()) {
            SectionPos sectionKey = SectionPos.of(pos);
            if (this.lastSection == null || !sectionKey.equals(this.lastSectionKey)) {
                this.lastSection = this.acquiredSections.computeIfAbsent(sectionKey, key -> {
                    // MCRe NoiseFarlands: getChunk 尚为 int 域 API（区块管理模块待 Long 化），此处一次性边界强转
                    ChunkAccess chunk = this.level.getChunk((int) SectionPos.blockToSectionCoord(pos.getX()), (int) SectionPos.blockToSectionCoord(pos.getZ()));
                    LevelChunkSection result = chunk.getSection(sectionIndex);
                    result.acquire();
                    return result;
                });
                this.lastSectionKey = sectionKey;
            }

            return this.lastSection;
        } else {
            return null;
        }
    }

    public BlockState getBlockState(final BlockPos pos) {
        LevelChunkSection section = this.getSection(pos);
        if (section == null) {
            return Blocks.AIR.defaultBlockState();
        }

        // MCRe NoiseFarlands: 0-15 局部坐标保持 int，一次性边界强转
        int sectionRelativeX = (int) SectionPos.sectionRelative(pos.getX());
        int sectionRelativeY = (int) SectionPos.sectionRelative(pos.getY());
        int sectionRelativeZ = (int) SectionPos.sectionRelative(pos.getZ());
        return section.getBlockState(sectionRelativeX, sectionRelativeY, sectionRelativeZ);
    }

    @Override
    public void close() {
        for (LevelChunkSection section : this.acquiredSections.values()) {
            section.release();
        }
    }
}