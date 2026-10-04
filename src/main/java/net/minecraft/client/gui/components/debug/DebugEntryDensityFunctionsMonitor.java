package net.minecraft.client.gui.components.debug;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：Density Functions Monitor 条目 —— 监测密度函数（DensityFunctions 全部类型）计算的每个步骤、
 * 所有节点的返回值（"看看到底是到哪一步算出了 NaN" 的调试工具）。
 * <p>监测机制：NoiseChunk.wrapNew 的返回处包 {@link MonitoringDensityFunction}（mapAll 后序遍历使全树每节点
 * 均被包装），每次根 compute 完成时按需快照"最近一次计算链"，条目显示快照。每行 = 计算步骤N [类型名]: 值
 * （Double.toString 科学记数法）；NaN/Infinity 整行标红（§c）。</p>
 * <p>记录窗口：条目开启时 display 更新时间戳，1 秒窗口外自动停止记录（平时仅一次 volatile 读，零开销）。</p>
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryDensityFunctionsMonitor implements DebugScreenEntry {
    private static volatile long lastRecordWindowMs = 0L;
    private static volatile boolean snapshotRequested = false;
    private static volatile List<String> lastSnapshot = List.of();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<List<String>> STEPS = ThreadLocal.withInitial(ArrayList::new);

    /** 记录窗口：display 后 1 秒内有效（条目关闭/屏幕关闭自动失效，无需显式清理） */
    public static boolean recording() {
        return Util.getMillis() - lastRecordWindowMs < 1000L;
    }

    /** 计算链进入：根（DEPTH 0→1）清空重记，只保留最近一次计算链 */
    public static void begin() {
        if (DEPTH.get() == 0) {
            STEPS.get().clear();
        }

        DEPTH.set(DEPTH.get() + 1);
    }

    /** 计算链返回：根完成时按需快照（跨线程：生成线程 copy → volatile 发布 → 渲染线程读） */
    public static void end() {
        int depth = DEPTH.get() - 1;
        DEPTH.set(depth);
        if (depth == 0 && snapshotRequested) {
            lastSnapshot = List.copyOf(STEPS.get());
            snapshotRequested = false;
        }
    }

    /** 记录一步：类型名 + 返回值（科学记数法；NaN/Inf 整行红） */
    public static void record(final String name, final double value) {
        if (!recording()) {
            return;
        }

        List<String> steps = STEPS.get();
        String formatted = String.valueOf(value);
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            steps.add("§cDFM/Steps [-" + (steps.size() + 1) + "-] (" + name + "): " + formatted);
        } else {
            steps.add("DFM/Steps [-" + (steps.size() + 1) + "-] (" + name + "): " + formatted);
        }
    }

    /** 🔧 MCRe：包装节点（NoiseChunk.wrapNew 返回处调用） */
    public static DensityFunction monitor(final DensityFunction function) {
        return new MonitoringDensityFunction(function);
    }

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        if (serverOrClientLevel != null) {
            lastRecordWindowMs = Util.getMillis();
            snapshotRequested = true;
        }

        displayer.addToGroup(DebugGroups.DENSITY_FUNCTIONS_MONITOR, lastSnapshot);
    }

    /**
     * 🔧 MCRe：密度函数监测包装——包住 wrap 后的每个节点，compute 时记录类型名 + 返回值。
     * 类型名：二元（add/mul/min/max）与 Marker（interpolated/flat_cache/...）取类型串，其余取类简名。
     * fillArray 透传（内部子节点各自带监测，cache 类的值与其子节点同值不重复记录）。
     */
    public static class MonitoringDensityFunction implements DensityFunction {
        private final DensityFunction delegate;
        private final String name;

        public MonitoringDensityFunction(final DensityFunction delegate) {
            this.delegate = delegate;
            this.name = nameOf(delegate);
        }

        private static String nameOf(final DensityFunction f) {
            if (f instanceof DensityFunctions.TwoArgumentSimpleFunction two) {
                return two.type().getSerializedName();
            }

            if (f instanceof DensityFunctions.MarkerOrMarked marker) {
                return marker.type().getSerializedName();
            }

            String s = f.getClass().getSimpleName();
            return s.isEmpty() ? "anonymous" : s;
        }

        @Override
        public double compute(final DensityFunction.FunctionContext context) {
            double value = this.delegate.compute(context);
            if (recording()) {
                record(this.name, value);
            }

            return value;
        }

        @Override
        public void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider) {
            this.delegate.fillArray(output, contextProvider);
        }

        @Override
        public DensityFunction mapChildren(final DensityFunction.Visitor visitor) {
            return this.delegate.mapChildren(visitor);
        }

        @Override
        public double minValue() {
            return this.delegate.minValue();
        }

        @Override
        public double maxValue() {
            return this.delegate.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.delegate.codec();
        }
    }
}