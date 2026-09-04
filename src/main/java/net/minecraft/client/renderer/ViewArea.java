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
    /** 🔧 MCRe P5 修复：上次更新的相机 sectionY（repositionCamera 早退用，相机不动时零开销） */
    private int lastUpdatedSectionY = Integer.MIN_VALUE;

    public ViewArea(
        final SectionRenderDispatcher sectionRenderDispatcher,
        final int minY,
        final int maxY,
        final int minSectionY,
        final int maxSectionY,
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

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    public int minSectionY() {
        return this.sections.minY();
    }

    public int maxSectionY() {
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

    /**
     * 🔧 MCRe P5 修复：让 ViewArea 跟随相机 Y 滑动（超高世界下玩家可探索范围跟随相机，不再钉世界底部）。
     * <p>机制：每帧根据 cameraSectionY 重新计算每个 RenderSection 的 sectionNode.y()，
     * 让渲染网格的 Y 中心对齐 ChunkAccess.windowMinY 中心（同为 camSecY - 17 附近）。
     * <p>早退：cameraSectionY 未变 → 零开销（普通游戏相机不动时 = 99% 命中）。
     * <p>轻量级：只更新 sectionNode.y() 和 renderOrigin[1]，不 reset mesh（mesh 标脏让下一帧重编译）。
     * @return true 如果 Y 实际滑动（需 invalidate sectionOcclusionGraph）
     */
    public boolean updateYOrigins(final int cameraSectionY) {
        if (cameraSectionY == this.lastUpdatedSectionY) {
            return false;
        }
        // sections.minY() 是构造时的 baseSectionY = windowMinY 基准
        int oldBaseSectionY = this.sections.minY();
        int sectionGridSizeY = this.sections.height();
        int verticalHalfSpan = sectionGridSizeY / 2;
        int newBaseSectionY = cameraSectionY - verticalHalfSpan;
        int delta = newBaseSectionY - oldBaseSectionY;

        // 同步更新 RotatingSectionStorage 的 Y 边界（containsSection 检查用，否则 getRenderSection 在窗口外返回 null）
        this.sections.setYRange(newBaseSectionY, newBaseSectionY + sectionGridSizeY - 1);

        for (SectionRenderDispatcher.RenderSection section : this.sections) {
            int currentSectionY = section.getSectionNode().y();
            section.setSectionNodeY(currentSectionY + delta);
        }

        this.lastUpdatedSectionY = cameraSectionY;
        return true;
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