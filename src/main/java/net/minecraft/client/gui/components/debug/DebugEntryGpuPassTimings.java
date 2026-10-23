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
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "GPU Frame Span", fact ->
            fact.text(Component.literal(formatMs(spanNs)).withColor(timingColor(spanNs)))
        );
        displayer.addFactToGroup(DebugGroups.GPU_PASS_TIMINGS, "Sum of Passes", fact ->
            fact.text(Component.literal(formatMs(totalNs)).withColor(timingColor(totalNs)))
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
