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

    /**
     * 🔧 MCRe P5 修复：重写 repositionCamera，同时处理 X/Z 和 Y 滑动。
     * <p>关键修复：先调用 setYRange 更新 RotatingSectionStorage 的 Y 边界，
     * 然后调用 repositionCenter —— repositionCenter 内部会用更新后的 minY
     * 计算 newSectionY = minY + gridY，从而正确设置所有 RenderSection 的 Y 坐标。
     * <p>早退：cameraSectionPos 完全相同（含 Y）→ 零开销。
     * <p>重编译：窗口 Y 滑动后，所有 RenderSection 的数据源(ChunkAccess section)变了，
     * 必须标记重编译，否则 mesh 还是旧 section 的方块数据。
     */
    public boolean repositionCamera(final SectionPos cameraSectionPos) {
        // 先计算新的 Y 基准（窗口中心 = cameraSectionY - 17）
        int sectionGridSizeY = this.sections.height();
        int verticalHalfSpan = sectionGridSizeY / 2; // 17
        int newBaseSectionY = cameraSectionPos.y() - verticalHalfSpan;

        // 先更新 Y 边界，让后续 repositionCenter 用新的 minY 计算 Y 坐标
        this.sections.setYRange(newBaseSectionY, newBaseSectionY + sectionGridSizeY - 1);

        // 记录旧中心用于判断是否真正滑动
        SectionPos oldCenter = this.sections.centerSectionPos();
        boolean yActuallyChanged = oldCenter.y() != cameraSectionPos.y();

        // 调用原版 repositionCenter（处理 X/Z 滑动，同时用新 minY 正确设置 Y）
        boolean result = this.sections.repositionCenter(cameraSectionPos);
        if (result) {
            this.sectionOcclusionGraph.invalidate();
        }

        // 🔧 关键修复：如果 Y 实际滑动了，所有 RenderSection 的数据源变了，必须强制重编译
        if (yActuallyChanged) {
            for (SectionRenderDispatcher.RenderSection section : this.sections) {
                section.markForRecompile();
            }
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
        // 🔧 MCRe P5 修复：窗口滑动时，render state 里的 sectionNode 可能携带旧 Y 坐标。
        // 策略：以当前窗口中心为基准，保持 X/Z 不变，将请求的 Y 映射到当前窗口对应的 gridY。
        int currentMinY = this.sections.minY();
        int sectionGridSizeY = this.sections.height();
        int requestedY = sectionNode.y();
        
        // 快速路径：已在窗口内直接命中
        if (requestedY >= currentMinY && requestedY < currentMinY + sectionGridSizeY) {
            return this.sections.getValue(sectionNode);
        }
        
        // 映射路径：计算相对于窗口中心的偏移，投影到当前窗口
        int windowCenterY = currentMinY + sectionGridSizeY / 2;
        int offsetFromCenter = requestedY - windowCenterY;
        int mappedY = windowCenterY + offsetFromCenter;
        
        // 钳制到窗口边界（防止极端偏移越界）
        mappedY = Math.max(currentMinY, Math.min(currentMinY + sectionGridSizeY - 1, mappedY));
        
        SectionPos mappedNode = SectionPos.of(sectionNode.x(), mappedY, sectionNode.z());
        SectionRenderDispatcher.RenderSection section = this.sections.getValue(mappedNode);
        
        // 兜底：如果映射后仍为 null（极少见），遍历当前窗口所有 section 找同 X/Z 最近的
        if (section == null) {
            for (SectionRenderDispatcher.RenderSection s : this.sections) {
                SectionPos sn = s.getSectionNode();
                if (sn.x() == sectionNode.x() && sn.z() == sectionNode.z()) {
                    return s;
                }
            }
        }
        return section;
    }
}