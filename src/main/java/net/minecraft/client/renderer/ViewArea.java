package net.minecraft.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.RotatingSectionStorage;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ViewArea {
    private final SectionOcclusionGraph sectionOcclusionGraph;
    private final RotatingSectionStorage<SectionRenderDispatcher.RenderSection> sections;
    private final int minY;
    private final int maxY;

    public ViewArea(
        final SectionRenderDispatcher sectionRenderDispatcher,
        final int minY,
        final int maxY,
        // MCRe NoiseFarlands: section Y 坐标 Long 化
        final long minSectionY,
        final long maxSectionY,
        final int renderDistance,
        final SectionOcclusionGraph sectionOcclusionGraph
    ) {
        this.sectionOcclusionGraph = sectionOcclusionGraph;
        this.minY = minY;
        this.maxY = maxY;
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createSections called from wrong thread: " + Thread.currentThread().getName());
        }

        this.sections = new RotatingSectionStorage<>(
            renderDistance, minSectionY, maxSectionY, (index, sectionNode) -> sectionRenderDispatcher.new RenderSection(index, sectionNode)
        );
    }

    public void releaseAllBuffers() {
        for (SectionRenderDispatcher.RenderSection section : this.sections) {
            section.reset();
        }
    }

    public int size() {
        return this.sections.size();
    }

    // MCRe NoiseFarlands: section Y Long 化
    public long minY() {
        return this.sections.minY();
    }

    public long maxY() {
        return this.sections.maxY();
    }

    // MCRe NoiseFarlands: section Y Long 化
    public long minSectionY() {
        return this.sections.minY();
    }

    public long maxSectionY() {
        return this.sections.maxY();
    }

    public int sectionCount() {
        return this.sections.height();
    }

    public int getViewDistance() {
        return this.sections.radius();
    }

    public boolean repositionCamera(final SectionPos cameraSectionPos) {
        boolean result = this.sections.repositionCenter(cameraSectionPos);
        if (result) {
            this.sectionOcclusionGraph.invalidate();
        }

        return result;
    }

    public SectionPos getCameraSectionPos() {
        return this.sections.centerSectionPos();
    }

    public SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(final BlockPos pos) {
        return this.sections.getValueAt(pos);
    }

    protected SectionRenderDispatcher.@Nullable RenderSection getRenderSection(final SectionPos sectionNode) {
        return this.sections.getValue(sectionNode);
    }
}