package net.minecraft.util;

import net.minecraft.world.phys.Vec3;

public class MathUtil {

    // 类似于原版的 Mth.clamp，但是不会限制无穷值
    public static double clamp(double value, double min, double max) {
        if (Double.isInfinite(value)) return value;
        return value < min ? min : Math.min(value, max);
    }

    public static double lerp3(double delta1, double delta2, double delta3, double start1, double end1, double start2, double end2, double start3, double end3, double start4, double end4) {
        return lerp(delta3, lerp2(delta1, delta2, start1, end1, start2, end2), lerp2(delta1, delta2, start3, end3, start4, end4));
    }

    public static double lerp2(double delta1, double delta2, double start1, double end1, double start2, double end2) {
        return lerp(delta2, lerp(delta1, start1, end1), lerp(delta1, start2, end2));
    }

    /**
     * 线性插值，保留无穷值。
     *
     * <p>与原版 {@link Mth#lerp} 的区别：
     *
     * <ul>
     *   <li>{@code delta == 0}/{@code delta == 1} 直接返回端点，避免 {@code 0 * Inf = NaN}；
     *   <li>同号 {@code Inf} 两端相等，提前返回，避免 {@code Inf - Inf = NaN}；
     *   <li>单端 {@code Inf} 时无穷大主导，返回该 {@code Inf}；
     *   <li>异号 {@code Inf}（数学上无定义）返回 {@code NaN}；
     *   <li>任一端 {@code NaN} 传播 {@code NaN}。
     * </ul>
     *
     * 注意：{@code delta} 假定位于 {@code [0, 1]}；调用方不得传入 {@code NaN}。
     */
    public static double lerp(double delta, double start, double end) {
        // 等值：同时覆盖普通等值、+Inf 与 +Inf、-Inf 与 -Inf、±0.0
        if (start == end) return start;
        // 端点：先于无穷检查，保证 delta=0/1 时按端点取
        if (delta == 0.0) return start;
        if (delta == 1.0) return end;

        // NaN 传播（同号 Inf 已被上面的 start == end 拦掉，此处不会误伤）
        if (Double.isNaN(start) || Double.isNaN(end)) return Double.NaN;

        // 走到这里，两端若都是 Inf 则必为异号（+Inf 与 -Inf），无定义
        if (Double.isInfinite(start) && Double.isInfinite(end)) return Double.NaN;

        // 单端 Inf：0 < delta < 1 时无穷大主导
        if (Double.isInfinite(start)) return start;
        if (Double.isInfinite(end)) return end;

        // 普通有限值：与原版公式一致
        return start + delta * (end - start);
    }

    /**
     * 丢失坐标的精度
     *
     * <p>由于过程中将坐标转换成了浮点数，所以除了“主动精度丢失”外，还会附加上类似基岩版高坐标距离现象的“被动精度丢失”。
     *
     * @param pos 输入坐标
     * @param lose 丢失指数（该指数每增加1，就会多丢失一位；指数为5时，数值的小数部分全部丢失）
     * @return 丢失精度后的坐标。
     */
    public static Vec3 losePrecision(Vec3 pos, int lose) {
        return new Vec3(
        losePrecision((float) pos.x(), lose),
        losePrecision((float) pos.y(), lose),
        losePrecision((float) pos.z(), lose)
        );
    }

    /**
     * 丢失浮点数数值的精度
     *
     * @param original 输入数值
     * @param lose 丢失指数（该指数每增加1，就会多丢失一位；指数为5时，数值的小数部分全部丢失）
     * @return 丢失精度后的坐标
     */
    public static float losePrecision(float original, int lose) {
        lose = 5 - lose;
        return (float) (Math.round(original * Math.pow(2, lose)) / Math.pow(2, lose));
    }
}
