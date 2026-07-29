package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    // ===== 改为使用 SectionPos 作为键 =====
    private final Object2ObjectMap<SectionPos, SectionCopy> sectionCopyCache = new Object2ObjectOpenHashMap<>();

    public RenderSectionRegion createRegion(final ClientLevel level, final long sectionNode) {
        // 将 long 解包为 int 坐标（原版 32 位兼容，但我们的 SectionPos 支持 64 位）
        int sectionX = SectionPos.x(sectionNode);
        int sectionY = SectionPos.y(sectionNode);
        int sectionZ = SectionPos.z(sectionNode);
        int minSectionX = sectionX - 1;
        int minSectionY = sectionY - 1;
        int minSectionZ = sectionZ - 1;
        int maxSectionX = sectionX + 1;
        int maxSectionY = sectionY + 1;
        int maxSectionZ = sectionZ + 1;
        SectionCopy[] regionSections = new SectionCopy[27];

        for (int regionSectionZ = minSectionZ; regionSectionZ <= maxSectionZ; regionSectionZ++) {
            for (int regionSectionY = minSectionY; regionSectionY <= maxSectionY; regionSectionY++) {
                for (int regionSectionX = minSectionX; regionSectionX <= maxSectionX; regionSectionX++) {
                    int index = RenderSectionRegion.index(minSectionX, minSectionY, minSectionZ, regionSectionX, regionSectionY, regionSectionZ);
                    regionSections[index] = this.getSectionDataCopy(level, regionSectionX, regionSectionY, regionSectionZ);
                }
            }
        }

        return new RenderSectionRegion(level, minSectionX, minSectionY, minSectionZ, regionSections);
    }

    private SectionCopy getSectionDataCopy(final Level level, final int sectionX, final int sectionY, final int sectionZ) {
        // ===== 使用 SectionPos 对象作为键 =====
        SectionPos key = SectionPos.of(sectionX, sectionY, sectionZ);
        return this.sectionCopyCache.computeIfAbsent(key, k -> {
            LevelChunk chunk = level.getChunk(sectionX, sectionZ);
            return new SectionCopy(chunk, chunk.getSectionIndexFromSectionY(sectionY));
        });
    }
}