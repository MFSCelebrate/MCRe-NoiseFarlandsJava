package net.minecraft.client.renderer.chunk;



import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RenderRegionCache {
    private final Map<SectionPos, SectionCopy> sectionCopyCache = new HashMap<>();

    public RenderSectionRegion createRegion(final ClientLevel level, final SectionPos sectionNode) {
        long sectionX = sectionNode.x();
        long sectionY = sectionNode.y();
        long sectionZ = sectionNode.z();
        long minSectionX = sectionX - 1;
        long minSectionY = sectionY - 1;
        long minSectionZ = sectionZ - 1;
        long maxSectionX = sectionX + 1;
        long maxSectionY = sectionY + 1;
        long maxSectionZ = sectionZ + 1;
        SectionCopy[] regionSections = new SectionCopy[27];

        for (long regionSectionZ = minSectionZ; regionSectionZ <= maxSectionZ; regionSectionZ++) {
            for (long regionSectionY = minSectionY; regionSectionY <= maxSectionY; regionSectionY++) {
                for (long regionSectionX = minSectionX; regionSectionX <= maxSectionX; regionSectionX++) {
                    int index = RenderSectionRegion.index(minSectionX, minSectionY, minSectionZ, regionSectionX, regionSectionY, regionSectionZ);
                    regionSections[index] = this.getSectionDataCopy(level, regionSectionX, regionSectionY, regionSectionZ);
                }
            }
        }

        return new RenderSectionRegion(level, minSectionX, minSectionY, minSectionZ, regionSections);
    }

    private SectionCopy getSectionDataCopy(final Level level, final long sectionX, final long sectionY, final long sectionZ) {
        return this.sectionCopyCache.computeIfAbsent(SectionPos.of(sectionX, sectionY, sectionZ), k -> {
            LevelChunk chunk = level.getChunk(sectionX, sectionZ);
            return new SectionCopy(chunk, chunk.getSectionIndexFromSectionY(sectionY));
        });
    }
}