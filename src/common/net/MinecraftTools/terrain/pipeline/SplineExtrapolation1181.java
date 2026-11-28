package net.MinecraftTools.terrain.pipeline;

/**
 * 🔧 MCRe「1.18.1 样条外推」——1.18.1 的 {@code CubicSpline} 端点外推公式（纯数学，无 Minecraft 依赖 → 可单元测试）。
 *
 * <p><b>为什么需要</b>：Minecraft 1.19.2 给 {@code CubicSpline} 加了 {@code linearExtend} 保护 ——
 * 端点<b>斜率为零</b>时直接返回端点值，<b>不</b>做乘法。于是输入为 ±Infinity 时：
 * <pre>{@code
 * 1.19.2+（现版 26.2）：derivative == 0 ? value : value + derivative * (input - location)
 *                     → 0 斜率 + Inf → 返回端点值（有限）→ 气候通道存活
 * 1.18.1：             value + derivative * (input - location)
 *                     → 0 斜率 + Inf → 0 × Infinity = NaN → NaN 沿密度树传播
 * }</pre>
 *
 * <p>这正是 1.18.1「32 位渐消之地只有几百格」的成因链末环：
 * <b>SHIFT 偏移噪声越过 32 位格点上限</b> → 格内小数离开 [0,1) → 平滑权重外推 → SHIFT 输出暴涨 →
 * 气候采样坐标被放大 → 气候噪声输出超过 float 可表示范围（±Infinity）→
 * <b>地形样条把 0 × Infinity 变成 NaN</b> → NaN 沿最终密度与网格插值传播 → 只剩空气和底部岩浆。
 * （1.19.2 的 linearExtend 正是为了掐断这一环，实测能把渐消距离从 504 格拉到 607908 格。）
 *
 * <p><b>已实测的等价性</b>：1.18.1 的 {@code CubicSpline.Multipoint.apply} 与 26.2 的
 * {@code CubicSpline.Multipoint.sample} <b>逐位相同</b>（区间二分、lerp 公式、导数全部一致），
 * <b>唯一差别</b>就是这里这条端点外推公式。
 *
 * @author MCRe NoiseFarlandsJava
 * @since 2026-11-28
 */
public final class SplineExtrapolation1181 {

    /** 1.18.1 管线激活时强制使用 1.18.1 语义（由管线在 patch RandomState 时置位） */
    private static volatile boolean forced1181 = false;

    private SplineExtrapolation1181() {
    }

    /**
     * 由 1.18.1 管线置位：管线激活期间，整条世界生成器链都用 1.18.1 的端点外推语义。
     *
     * @param forced true = 强制 1.18.1 语义
     */
    public static void setForced1181(final boolean forced) {
        forced1181 = forced;
    }

    /**
     * 是否被管线强制为 1.18.1 语义。
     *
     * @return true = 强制中
     */
    public static boolean isForced1181() {
        return forced1181;
    }

    /**
     * 1.18.1 的端点外推：{@code 端点值 + 端点斜率 × (输入 − 端点位置)}。
     *
     * <p>与 1.19.2+ 的差别只有一处：<b>不</b>对「斜率为零」做特殊处理 —— 因此
     * {@code 0 × Infinity = NaN} 会如实出现（这正是要还原的 1.18.1 行为）。
     *
     * @param input    采样输入（可能是 ±Infinity）
     * @param location 端点位置
     * @param value    端点值
     * @param derivative 端点斜率
     * @return 外推结果
     */
    public static float extend(final float input, final float location, final float value, final float derivative) {
        return value + derivative * (input - location);
    }
}
