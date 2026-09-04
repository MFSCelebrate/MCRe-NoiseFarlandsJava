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
     * 🔧 MCRe P5 修复：repositionCamera 只处理 X/Z 滑动（渲染网格跟随玩家水平移动）。
     * <p>Y 轴**不跟随玩家**——渲染网格固定在世界坐标（构造时确定的 34 layers）。
     * ChunkAccess 内部会用 windowMinY（跟随玩家）把世界 Y 转窗口索引，自动拿到正确 section 数据。
     * <p>早退：cameraSectionPos X/Z 相同 → 零开销。
     */
    public boolean repositionCamera(final SectionPos cameraSectionPos) {
        // 只更新 X/Z 中心，Y 保持构造时固定范围
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