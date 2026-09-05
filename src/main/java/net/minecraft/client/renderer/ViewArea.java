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
            final SectionOcclusionGraph sectionOcclusionGraph) {
        this.sectionOcclusionGraph = sectionOcclusionGraph;
        this.minY = minY;
        this.maxY = maxY;
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("createSections called from wrong thread: " + Thread.currentThread().getName());
        }

        this.sections = new RotatingSectionStorage<>(
        renderDistance, minSectionY, maxSectionY, (index, sectionNode) -> sectionRenderDispatcher
        .new RenderSection(index, sectionNode)
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
     * 🔧 MCRe P5 修复：repositionCamera 处理 X/Z/Y 三轴滑动（渲染网格跟随玩家移动）。
     *
     * <p>Y 轴采用与 ChunkAccess 完全一致的窗口大小（34 sections = 17下+16上+中心）， 窗口中心对齐玩家 sectionY，与
     * ChunkAccess.windowMinY 保持完全同步。 这保证 SectionCompiler 查询世界 Y 时，ChunkAccess 内部转换命中正确 section。
     *
     * <p>早退：cameraSectionPos 完全相同（含 Y）→ 零开销。
     */
    public boolean repositionCamera(final SectionPos cameraSectionPos) {
        // 只传 X/Z 变化，Y 锁死为当前中心 Y（构造时固定）
        SectionPos fixedYPos = SectionPos.of(cameraSectionPos.x(), this.sections.centerSectionPos().y(), cameraSectionPos.z());
        boolean changed = this.sections.repositionCenter(fixedYPos);
        if (changed) this.sectionOcclusionGraph.invalidate();
        return changed;
    }

    // 构造时锚定世界 Y=0
    int viewMinSectionY = -17;
    int viewMaxSectionY = 16;

    public SectionPos getCameraSectionPos() {
        return this.sections.centerSectionPos();
    }

    public SectionRenderDispatcher.@Nullable RenderSection getRenderSectionAt(final BlockPos pos) {
        return this.sections.getValueAt(pos);
    }

    protected SectionRenderDispatcher.@Nullable
            RenderSection getRenderSection(final SectionPos sectionNode) {
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