package net.minecraft.client.gui.components.debug;

import com.mojang.blaze3d.systems.GpuPassProfiler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * MCRe: GPU Pass Timings — 每个 RenderPass 的 GPU 耗时（timeline query 时间戳实测）。
 * 按耗时降序排列定位瓶颈；>8ms 红、>4ms 黄。数据延迟约 2 帧（避免 GPU 读回阻塞）。
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryGpuPassTimings implements DebugScreenEntry {
    private static final long RED_THRESHOLD_NS = 8_000_000L;
    private static final long YELLOW_THRESHOLD_NS = 4_000_000L;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_YELLOW = 0xFFFFFF55;
    private static final int COLOR_NORMAL = -1;

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean enabled = minecraft.debugEntries.isCurrentlyEnabled(DebugScreenEntries.GPU_PASS_TIMINGS);
        GpuPassProfiler.setEnabled(enabled);
        if (!enabled) {
            return;
        }

        List<GpuPassProfiler.PassTiming> timings = GpuPassProfiler.snapshot();
        if (timings.isEmpty()) {
            return;
        }

        long spanNs = GpuPassProfiler.spanNs();
        long totalNs = GpuPassProfiler.totalNs();
        long gapNs = Math.max(0L, spanNs - totalNs);
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "GPU Frame Span", fact ->
            fact.text(Component.literal(formatMs(spanNs)).withColor(timingColor(spanNs)))
        );
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "Sum of Passes", fact ->
            fact.text(Component.literal(formatMs(totalNs)).withColor(timingColor(totalNs)))
        );
        // Pass 间空隙 = GPU 饿等 CPU 提交的时间；Gap 大 → CPU-bound（提交断档），Gap 小 → GPU-bound
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "Pass Gap", fact ->
            fact.text(Component.literal(formatMs(gapNs)).withColor(timingColor(gapNs)))
        );

        // CPU Timeline 三段（渲染线程）：Game=world tick/pick，Extract=提取，Render=渲染+present
        // 与 GPU 三行对照：卡顿时看 CPU 哪段吃掉时间（worldgen 抢占 → Game 大；mesh 上传 → Render 大）
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "CPU Game", fact ->
            fact.text(Component.literal(formatMs(minecraft.frameGameNs)).withColor(timingColor(minecraft.frameGameNs)))
        );
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "CPU Extract", fact ->
            fact.text(Component.literal(formatMs(minecraft.frameExtractNs)).withColor(timingColor(minecraft.frameExtractNs)))
        );
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "CPU Render", fact ->
            fact.text(Component.literal(formatMs(minecraft.frameRenderNs)).withColor(timingColor(minecraft.frameRenderNs)))
        );

        List<GpuPassProfiler.PassTiming> sorted = new ArrayList<>(timings);
        sorted.sort((a, b) -> Long.compare(b.ns(), a.ns()));
        for (GpuPassProfiler.PassTiming timing : sorted) {
            long ns = timing.ns();
            int color = timingColor(ns);
            displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, timing.name(), fact ->
                fact.text(Component.literal(formatMs(ns)).withColor(color))
            );
        }
    }

    private static int timingColor(final long ns) {
        if (ns > RED_THRESHOLD_NS) {
            return COLOR_RED;
        }
        return ns > YELLOW_THRESHOLD_NS ? COLOR_YELLOW : COLOR_NORMAL;
    }

    private static String formatMs(final long ns) {
        return String.format(Locale.ROOT, "%.2f ms", ns / 1.0E6);
    }

    @Override
    public boolean isAllowed(final boolean reducedDebugInfo) {
        return true;
    }
}
