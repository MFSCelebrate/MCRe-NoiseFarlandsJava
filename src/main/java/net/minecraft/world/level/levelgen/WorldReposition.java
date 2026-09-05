package net.minecraft.world.level.levelgen;

import java.math.BigDecimal;
import java.math.BigInteger;
import net.MinecraftTools.Math._256Bit.Float256;
import net.minecraft.core.Direction;

/**
 * 🔧 MCRe NoiseFarlands —— 世界生成器偏移/缩放工具类
 *
 * <p>移植自 UltimateScaler (MIT, inf32768) 的 mixin 偏移逻辑，
 * 用我们的 Float256 工具替代 BigDecimal 实现"始终高精度"。
 *
 * <p>核心公式（一维）：{@code newPos = pos * scale + shift}
 *
 * <p>接入点：DensityFunctions.Noise/ShiftedNoise/Shift/ShiftA/ShiftB/YClampedGradient.compute，
 * BlendedNoise.compute，NoiseBasedChunkGenerator.createFluidPicker。
 *
 * <p>缓存策略：开世界时（WorldMainSettingScreen.onDone）调用 {@link #refresh} 一次性解析为 Float256，
 * 运行期直接读内存数组，避免每个方块解析字符串。
 *
 * <p>本类位于 shared 包（levelgen），不依赖 client 包，避免循环依赖。
 * 调用方（client 端 WorldMainSettingScreen）负责把 String → Float256，装进 {@link RepositionConfig} 再传入。
 */
public final class WorldReposition {
    /** 缩放因子（X, Y, Z），默认全 1（无缩放） */
    private static final Float256[] SCALE = {Float256.ONE, Float256.ONE, Float256.ONE};
    /** 偏移量（X, Y, Z），默认全 0（无偏移） */
    private static final Float256[] SHIFT = {Float256.ZERO, Float256.ZERO, Float256.ZERO};
    /** YClampedGradient 独立开关——默认 false（保持原版 Y 轴海拔梯度，避免边境之地消失） */
    private static volatile boolean yClampedGradientOffsetEnabled = false;

    private WorldReposition() {
    }

    // ═════════════════ 同步入口 ═════════════════

    /**
     * 🔧 从 {@link RepositionConfig} 刷新内存缓存（开世界时调用一次）。
     * 调用方负责把 String 解析为 Float256（用 {@link #parseOrFallback}），本类不接触字符串。
     */
    public static void refresh(final RepositionConfig config) {
        SCALE[0] = config.scaleX();
        SCALE[1] = config.scaleY();
        SCALE[2] = config.scaleZ();
        SHIFT[0] = config.shiftX();
        SHIFT[1] = config.shiftY();
        SHIFT[2] = config.shiftZ();
        yClampedGradientOffsetEnabled = config.yClampedGradientOffset();
    }

    // ═════════════════ 一维变换 ═════════════════

    /** 一维变换 → double（公式：{@code newPos = pos * scale + shift}） */
    public static double reposition(final double pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        return Float256.of(pos).multiply(SCALE[i]).add(SHIFT[i]).doubleValue();
    }

    /** 一维变换 → long */
    public static long reposition(final long pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        return Float256.of(pos).multiply(SCALE[i]).add(SHIFT[i]).longValue();
    }

    /** 一维变换 → int（EndIsland 等需要整数坐标的场景） */
    public static int reposition(final int pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        return Float256.of(pos).multiply(SCALE[i]).add(SHIFT[i]).intValue();
    }

    /**
     * 一维变换 → BigInteger（EndIsland 末地环修复，需要精确整数算 sqrt(x²+z²)）。
     * 注意：这是「前向变换」（pos → 新坐标），不是逆运算。
     */
    public static BigInteger repositionToBigInteger(final int pos, final Direction.Axis axis) {
        final int i = axis.ordinal();
        return Float256.of(pos).multiply(SCALE[i]).add(SHIFT[i]).toBigInteger();
    }

    // ═════════════════ 开关访问 ═════════════════

    /**
     * YClampedGradient 是否启用偏移——控制 Y 轴 base stone 海拔梯度。
     * 启用后 Y 轴不会出现任何边境之地，作为可选项让用户自己权衡。
     */
    public static boolean isYClampedGradientOffsetEnabled() {
        return yClampedGradientOffsetEnabled;
    }

    /**
     * 🔧 MCRe：读指定轴的缩放因子（用于逆运算，如 {@code createFluidPicker} 把世界 Y 还原到玩家 Y）。
     * 调用方应自行检查 {@link #isYClampedGradientOffsetEnabled} 等开关决定是否使用。
     */
    public static Float256 getScale(final Direction.Axis axis) {
        return SCALE[axis.ordinal()];
    }

    /**
     * 🔧 MCRe：读指定轴的偏移量（同上，逆运算场景使用）。
     */
    public static Float256 getShift(final Direction.Axis axis) {
        return SHIFT[axis.ordinal()];
    }

    // ═════════════════ 解析 helper（公开，调用方复用） ═════════════════

    /**
     * 把字符串解析为 Float256，失败回退到 fallback（绝不抛异常）。
     * 解析路径：String → BigDecimal（构造器原生支持 e/E、负号、小数）→ Float256.of(BigDecimal)。
     */
    public static Float256 parseOrFallback(final String s, final Float256 fallback) {
        if (s == null || s.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Float256.of(new BigDecimal(s.trim()));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ═════════════════ 配置快照 record ═════════════════

    /**
     * 🔧 MCRe：偏移/缩放配置快照——开世界时一次解析，运行期不再触碰字符串。
     * 位于 shared 包（levelgen），不引用 client 包，避免循环依赖。
     *
     * <p>使用示例：
     * <pre>{@code
     * Float256 sx = WorldReposition.parseOrFallback(cfg.xWorldScaler, Float256.ONE);
     * Float256 oy = WorldReposition.parseOrFallback(cfg.yWorldOffset, Float256.ZERO);
     * WorldReposition.refresh(new WorldReposition.RepositionConfig(
     *     sx, sy, sz, ox, oy, oz, cfg.enabledYClampedGradientOffset
     * ));
     * }</pre>
     */
    public record RepositionConfig(
            Float256 scaleX, Float256 scaleY, Float256 scaleZ,
            Float256 shiftX, Float256 shiftY, Float256 shiftZ,
            boolean yClampedGradientOffset
    ) {
        /** 默认无变换配置（scale=1, shift=0, yGradient=false） */
        public static final RepositionConfig DISABLED = new RepositionConfig(
                Float256.ONE, Float256.ONE, Float256.ONE,
                Float256.ZERO, Float256.ZERO, Float256.ZERO,
                false
        );
    }
}