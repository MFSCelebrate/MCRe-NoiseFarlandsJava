package com.mojang.blaze3d.systems;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * MCRe GPU Pass Profiler — per-RenderPass GPU 时间戳剖析器（后端无关，Vulkan/OpenGL 通用）。
 * <p>
 * 工作原理：
 * <ul>
 *   <li>每个 RenderPass 的 begin/submit 在 {@link CommandEncoder} 里各写一个 GPU timestamp
 *       （复用 {@link GpuDevice#createTimestampQueryPool} + {@link CommandEncoder#writeTimestamp}）；</li>
 *   <li>帧 F 记录，帧 F+2 由 {@link #tickAndPublish()} 读回（availability 位保证 GPU 已执行完，零阻塞）；</li>
 *   <li>快照按 pass 记录顺序发布（内含耗时），由调试屏幕条目降序展示定位瓶颈。</li>
 * </ul>
 * <p>
 * 开关：{@link #setEnabled(boolean)} 由调试屏幕条目每帧驱动；关闭时 beginPass/endPass 零开销、
 * tickAndPublish 直接跳过。query 池环形复用，未读回数据受溢出保护不被覆写。
 */
@OnlyIn(Dist.CLIENT)
public class GpuPassProfiler {
    private static final int QUERY_POOL_SIZE = 512;
    private static final int MAX_PENDING_FRAMES = 3;
    private static final int MAX_OPEN_PASSES = 16;
    private static final GpuPassProfiler INSTANCE = new GpuPassProfiler();

    private final Deque<OpenPass> openPasses = new ArrayDeque<>();
    private final Deque<FrameRecord> pendingFrames = new ArrayDeque<>();
    private List<PassTiming> lastFrameTimings = List.of();
    private long lastFrameTotalNs = 0L;
    private long lastFrameSpanNs = 0L;
    private @Nullable GpuQueryPool queryPool;
    private float timestampPeriod = 1.0F;
    private int head = 0;
    private boolean enabled = false;
    private @Nullable FrameRecord currentFrame;

    private GpuPassProfiler() {
    }

    public static GpuPassProfiler getInstance() {
        return INSTANCE;
    }

    /** 开关（由调试条目每帧驱动）；切换时重置全部状态，保证干净启停。 */
    public static void setEnabled(final boolean value) {
        if (INSTANCE.enabled != value) {
            INSTANCE.enabled = value;
            if (value) {
                INSTANCE.ensureInitialized();
            } else {
                INSTANCE.reset();
            }
        }
    }

    public static boolean isEnabled() {
        return INSTANCE.enabled;
    }

    /** RenderPass 开始（createRenderPass 收口点）。label 用 Supplier 延迟取值，关闭时零分配。 */
    public static void beginPass(final CommandEncoder encoder, final Supplier<String> label) {
        if (INSTANCE.enabled) {
            INSTANCE.begin(encoder, label);
        }
    }

    /** RenderPass 结束（submitRenderPass）。无配对 begin 时静默忽略。 */
    public static void endPass(final CommandEncoder encoder) {
        if (INSTANCE.enabled) {
            INSTANCE.end(encoder);
        }
    }

    /** 每帧调用一次（渲染线程，runTick render 段）：收上一帧 + 发布 2 帧前已完成的数据。 */
    public static void tickAndPublish() {
        if (INSTANCE.enabled) {
            INSTANCE.ensureInitialized();
            INSTANCE.tick();
        }
    }

    /** 最近一帧已发布的各 pass 耗时快照（记录顺序）。 */
    public static List<PassTiming> snapshot() {
        return INSTANCE.lastFrameTimings;
    }

    /** 最近一帧所有 pass 耗时之和。 */
    public static long totalNs() {
        return INSTANCE.lastFrameTotalNs;
    }

    /** 最近一帧墙钟跨度（首 pass begin → 末 pass end），含 pass 间隙。 */
    public static long spanNs() {
        return INSTANCE.lastFrameSpanNs;
    }

    private void ensureInitialized() {
        if (this.queryPool == null) {
            this.queryPool = RenderSystem.getDevice().createTimestampQueryPool(QUERY_POOL_SIZE);
            this.timestampPeriod = RenderSystem.getDevice().getDeviceInfo().timestampPeriod();
        }
    }

    private void begin(final CommandEncoder encoder, final Supplier<String> label) {
        GpuQueryPool pool = this.queryPool;
        if (pool == null) {
            return;
        }
        if (this.openPasses.size() >= MAX_OPEN_PASSES) {
            return; // 防御：异常路径泄漏的未闭合 pass 过多，停止记录避免错配
        }
        FrameRecord frame = this.currentFrame;
        if (frame == null) {
            frame = new FrameRecord();
            this.currentFrame = frame;
        }
        // 溢出保护：head 不许绕回覆写最老未读帧的数据
        FrameRecord oldest = this.pendingFrames.peekFirst();
        if (oldest != null && oldest.firstIdx >= 0) {
            int distance = (this.head - oldest.firstIdx + QUERY_POOL_SIZE) % QUERY_POOL_SIZE;
            if (distance >= QUERY_POOL_SIZE - 2) {
                return;
            }
        }
        int idx = this.head;
        this.head = (this.head + 1) % QUERY_POOL_SIZE;
        encoder.writeTimestamp(pool, idx);
        OpenPass pass = new OpenPass(label.get(), idx);
        this.openPasses.addLast(pass);
        frame.passes.add(pass);
    }

    private void end(final CommandEncoder encoder) {
        GpuQueryPool pool = this.queryPool;
        if (pool == null) {
            return;
        }
        OpenPass pass = this.openPasses.pollLast();
        if (pass == null || pass.endIdx >= 0) {
            return;
        }
        int idx = this.head;
        this.head = (this.head + 1) % QUERY_POOL_SIZE;
        encoder.writeTimestamp(pool, idx);
        pass.endIdx = idx;
    }

    private void tick() {
        // 1. 收上一帧：计算 query 区间，丢弃异常路径未闭合的 pass
        FrameRecord frame = this.currentFrame;
        if (frame != null) {
            this.currentFrame = null;
            this.openPasses.clear();
            if (!frame.passes.isEmpty()) {
                OpenPass last = frame.passes.getLast();
                frame.firstIdx = frame.passes.getFirst().beginIdx;
                frame.count = last.endIdx >= 0
                    ? (last.endIdx - frame.firstIdx + QUERY_POOL_SIZE) % QUERY_POOL_SIZE + 1
                    : (this.head - frame.firstIdx + QUERY_POOL_SIZE) % QUERY_POOL_SIZE;
                if (frame.count > 0) {
                    this.pendingFrames.addLast(frame);
                }
            }
        }
        // 2. 积压保护：GPU 严重落后时丢最老帧，防止断流
        while (this.pendingFrames.size() > MAX_PENDING_FRAMES) {
            this.pendingFrames.pollFirst();
        }
        // 3. 发布最老已完成的帧
        while (!this.pendingFrames.isEmpty()) {
            FrameRecord oldest = this.pendingFrames.peekFirst();
            List<PassTiming> timings = new ArrayList<>(oldest.passes.size());
            long[] totals = new long[2]; // [0]=sum, [1]=span
            if (this.tryReadFrame(oldest, timings, totals)) {
                this.lastFrameTimings = timings;
                this.lastFrameTotalNs = totals[0];
                this.lastFrameSpanNs = totals[1];
                this.pendingFrames.pollFirst();
                break; // 每帧只发布一帧数据
            }
            break; // GPU 尚未完成最老帧，等下一帧 tick
        }
    }

    /** 批量读回一帧的全部时间戳（处理环形回绕）；任一 availability=0 → false（保留待下帧）。 */
    private boolean tryReadFrame(final FrameRecord frame, final List<PassTiming> outTimings, final long[] outTotals) {
        GpuQueryPool pool = this.queryPool;
        if (pool == null || frame.count <= 0 || frame.firstIdx < 0) {
            return false;
        }
        int count = frame.count;
        OptionalLong[] values;
        if (frame.firstIdx + count <= QUERY_POOL_SIZE) {
            values = pool.getValues(frame.firstIdx, count);
        } else {
            int firstPart = QUERY_POOL_SIZE - frame.firstIdx;
            OptionalLong[] part1 = pool.getValues(frame.firstIdx, firstPart);
            OptionalLong[] part2 = pool.getValues(0, count - firstPart);
            values = new OptionalLong[count];
            System.arraycopy(part1, 0, values, 0, firstPart);
            System.arraycopy(part2, 0, values, firstPart, count - firstPart);
        }
        long total = 0L;
        long firstBegin = Long.MIN_VALUE;
        long lastEnd = Long.MIN_VALUE;
        for (OpenPass pass : frame.passes) {
            if (pass.endIdx < 0) {
                continue;
            }
            int beginPos = (pass.beginIdx - frame.firstIdx + QUERY_POOL_SIZE) % QUERY_POOL_SIZE;
            int endPos = (pass.endIdx - frame.firstIdx + QUERY_POOL_SIZE) % QUERY_POOL_SIZE;
            if (beginPos >= count || endPos >= count) {
                continue;
            }
            OptionalLong beginValue = values[beginPos];
            OptionalLong endValue = values[endPos];
            if (beginValue.isEmpty() || endValue.isEmpty()) {
                return false; // GPU 尚未跑完这帧
            }
            long delta = endValue.getAsLong() - beginValue.getAsLong();
            long ns = (long)((float)delta * this.timestampPeriod);
            if (ns < 0L) {
                ns = 0L; // 时间戳乱序防御
            }
            outTimings.add(new PassTiming(pass.name, ns));
            total += ns;
            if (firstBegin == Long.MIN_VALUE) {
                firstBegin = beginValue.getAsLong();
            }
            lastEnd = endValue.getAsLong();
        }
        if (firstBegin == Long.MIN_VALUE) {
            return false;
        }
        long span = lastEnd - firstBegin;
        outTotals[0] = total;
        outTotals[1] = span > 0L ? (long)((float)span * this.timestampPeriod) : 0L;
        return true;
    }

    private void reset() {
        this.openPasses.clear();
        this.pendingFrames.clear();
        this.currentFrame = null;
        this.lastFrameTimings = List.of();
        this.lastFrameTotalNs = 0L;
        this.lastFrameSpanNs = 0L;
        this.head = 0;
    }

    private static final class OpenPass {
        final String name;
        final int beginIdx;
        int endIdx = -1;

        OpenPass(final String name, final int beginIdx) {
            this.name = name;
            this.beginIdx = beginIdx;
        }
    }

    private static final class FrameRecord {
        final List<OpenPass> passes = new ArrayList<>();
        int firstIdx = -1;
        int count = 0;
    }

    /** 单个 pass 的 GPU 耗时（纳秒）。 */
    public record PassTiming(String name, long ns) {
    }
}
