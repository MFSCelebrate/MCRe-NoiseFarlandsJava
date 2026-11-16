package net.MinecraftTools.Math._256bit_Refactoring;

/**
 * 🔧 MathHelperRefactor——原版 {@code net.minecraft.util.Mth}（MathHelper）的 256bit 专用适配版。
 *
 * <p>目标（大佬 2026-11-13 指令）：把 Mth 的所有数学计算全部换成 256bit 专用版本——原版 Mth
 * 兜底非 256 场景，本类只服务边境之地/超大世界的高精度需求。所有公开方法的输入输出均为
 * {@link IntRefactor256} / {@link FloatRefactor256} / {@link DynamicNumber}（少数纯索引逻辑的
 * int 方法保留原语种并在注释标注“无语义精度问题”）。
 *
 * <p>分批进度：批1（常量/取整/基础算术/位逻辑/取模/量化/hash）✅ → 批2（lerp/map/wrapDegrees
 * 家族）→ 批3（sin/cos/atan2 LUT/length/rayIntersectsAABB）→ 批4（收尾）。
 */
public final class MathHelperRefactor {

    private MathHelperRefactor() {
    }

    // ═══════════ 常量（FloatRefactor256 化） ═══════════

    /** π（double 最近值转 256 浮点）。 */
    public static final FloatRefactor256 PI = FloatRefactor256.of(Math.PI);
    public static final FloatRefactor256 HALF_PI = FloatRefactor256.of(Math.PI / 2);
    public static final FloatRefactor256 TWO_PI = FloatRefactor256.of(Math.PI * 2);
    public static final FloatRefactor256 DEG_TO_RAD = FloatRefactor256.of(Math.PI / 180.0);
    public static final FloatRefactor256 RAD_TO_DEG = FloatRefactor256.of(180.0 / Math.PI);
    /** 相等判定容差（与原版 1e-5 同值）。 */
    public static final FloatRefactor256 EPSILON = FloatRefactor256.of(1.0E-5);
    public static final FloatRefactor256 SQRT_OF_TWO = FloatRefactor256.of(Math.sqrt(2.0));

    // ═══════════ 取整族（DynamicNumber → IntRefactor256） ═══════════

    /** floor（向负无穷取整；对齐 Math.floor 语义但值域 256 位）。 */
    public static IntRefactor256 floor(final DynamicNumber v) {
        final IntRefactor256 i = v.toIntRefactor256();
        // toIntRefactor256 是截断向零；负数有余数时减一
        return v.signum() < 0 && !v.subtract(DynamicNumber.of(Type_LONG(), i.toBigInteger())).isZero() ? i.subtract(IntRefactor256.ONE) : i;
    }

    private static net.MinecraftTools.Math._256bit_Refactoring.DynamicNumber.Type Type_LONG() {
        return net.MinecraftTools.Math._256bit_Refactoring.DynamicNumber.Type.LONG;
    }

    /** ceil（向正无穷取整）。 */
    public static IntRefactor256 ceil(final DynamicNumber v) {
        final IntRefactor256 i = v.toIntRefactor256();
        return v.signum() > 0 && !v.subtract(DynamicNumber.of(Type_LONG(), i.toBigInteger())).isZero() ? i.add(IntRefactor256.ONE) : i;
    }

    /** floor 的 long 视图（截断回 long；超域抛）。 */
    public static long lfloor(final DynamicNumber v) {
        return floor(v).toLong();
    }

    /** ceil 的 long 视图。 */
    public static long ceilLong(final DynamicNumber v) {
        return ceil(v).toLong();
    }

    // ═══════════ 绝对值/符号 ═══════════

    public static IntRefactor256 abs(final IntRefactor256 v) {
        return v.isNegative() ? v.negate() : v;
    }

    public static FloatRefactor256 abs(final FloatRefactor256 v) {
        return v.signBit() ? v.negate() : v;
    }

    public static DynamicNumber absMax(final DynamicNumber a, final DynamicNumber b) {
        final DynamicNumber aa = a.abs(), ba = b.abs();
        return aa.compareTo(ba) >= 0 ? aa : ba;
    }

    /** sign（-1/0/1；NaN 视作 0）。 */
    public static int sign(final DynamicNumber v) {
        return v.isZero() ? 0 : v.isNegative() ? -1 : 1;
    }

    /** 车棋盘距离（|dx| 与 |dz| 的较大者）。 */
    public static DynamicNumber chessboardDistance(final DynamicNumber x0, final DynamicNumber z0, final DynamicNumber x1, final DynamicNumber z1) {
        return absMax(x1.subtract(x0), z1.subtract(z0));
    }

    // ═══════════ clamp 族 ═══════════

    public static IntRefactor256 clamp(final IntRefactor256 value, final IntRefactor256 min, final IntRefactor256 max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        return value.compareTo(max) > 0 ? max : value;
    }

    public static FloatRefactor256 clamp(final FloatRefactor256 value, final FloatRefactor256 min, final FloatRefactor256 max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        return value.compareTo(max) > 0 ? max : value;
    }

    public static DynamicNumber clamp(final DynamicNumber value, final DynamicNumber min, final DynamicNumber max) {
        if (value.compareTo(min) < 0) {
            return min;
        }
        return value.compareTo(max) > 0 ? max : value;
    }

    // ═══════════ 基础算术族 ═══════════

    public static FloatRefactor256 square(final FloatRefactor256 x) {
        return x.multiply(x);
    }

    public static FloatRefactor256 cube(final FloatRefactor256 x) {
        return x.multiply(x).multiply(x);
    }

    public static IntRefactor256 square(final IntRefactor256 x) {
        return x.multiply(x);
    }

    /** 平方根（FloatRefactor256.sqrt 直通；负数为 NaN）。 */
    public static FloatRefactor256 sqrt(final FloatRefactor256 x) {
        return x.sqrt();
    }

    /** 平方根倒数（ONE / sqrt；0 → +Inf、负 → NaN）。 */
    public static FloatRefactor256 invSqrt(final FloatRefactor256 x) {
        return FloatRefactor256.ONE.divide(x.sqrt());
    }

    /** ceil 的 FloatRefactor256 重载（截断向零 + 正有余数加一）。 */
    public static FloatRefactor256 ceil(final FloatRefactor256 v) {
        if (!v.isFinite() || v.isZero()) {
            return v;
        }
        final FloatRefactor256 truncated = FloatRefactor256.of(v.toIntRefactor256());
        return !v.signBit() && !v.equals(truncated) ? truncated.add(FloatRefactor256.ONE) : truncated;
    }

    /** floor 的 FloatRefactor256 重载（截断向零 + 负有余数减一；批 2 完整化）。 */
    public static FloatRefactor256 floor(final FloatRefactor256 v) {
        if (!v.isFinite() || v.isZero()) {
            return v;
        }
        final FloatRefactor256 truncated = FloatRefactor256.of(v.toIntRefactor256());
        return v.signBit() && !v.equals(truncated) ? truncated.subtract(FloatRefactor256.ONE) : truncated;
    }

    /** 小数部分（v - floor(v)）。 */
    public static FloatRefactor256 frac(final FloatRefactor256 v) {
        return v.subtract(floor(v));
    }

    /** 容差相等（|a-b| < EPSILON；与原版绝对容差同构）。 */
    public static boolean equal(final FloatRefactor256 a, final FloatRefactor256 b) {
        return a.subtract(b).abs().compareTo(EPSILON) < 0;
    }

    // ═══════════ 取模/量化族（IntRefactor256） ═══════════

    /** 向负无穷除法（floorDiv；对齐 JDK Math.floorDiv 语义）。 */
    public static IntRefactor256 floorDiv(final IntRefactor256 a, final IntRefactor256 b) {
        final IntRefactor256 q = a.divide(b);
        final boolean sameSign = a.isNegative() == b.isNegative();
        final boolean hasRemainder = !a.remainder(b).isZero();
        return sameSign || !hasRemainder ? q : q.subtract(IntRefactor256.ONE);
    }

    /** 正取模（结果恒非负；对齐 JDK Math.floorMod）。 */
    public static IntRefactor256 positiveModulo(final IntRefactor256 input, final IntRefactor256 mod) {
        IntRefactor256 r = input.remainder(mod);
        if (r.isNegative()) {
            r = r.add(mod);
        }
        return r;
    }

    /** 向正无穷除法（-floorDiv(-a, b)）。 */
    public static IntRefactor256 positiveCeilDiv(final IntRefactor256 input, final IntRefactor256 divisor) {
        return floorDiv(input.negate(), divisor).negate();
    }

    /** 向上取整到 multiple 的倍数。 */
    public static IntRefactor256 roundToward(final IntRefactor256 input, final IntRefactor256 multiple) {
        return positiveCeilDiv(input, multiple).multiply(multiple);
    }

    public static boolean isMultipleOf(final IntRefactor256 dividend, final IntRefactor256 divisor) {
        return dividend.remainder(divisor).isZero();
    }

    /** 量化到 quantizeResolution 的倍数（向下）。 */
    public static IntRefactor256 quantize(final DynamicNumber value, final DynamicNumber quantizeResolution) {
        return floor(value.divide(quantizeResolution)).multiply(quantizeResolution.toIntRefactor256());
    }

    // ═══════════ 位逻辑族（int 索引语义，无语义精度问题——保留原语种） ═══════════

    /** 2 的幂判定（int）。 */
    public static boolean isPowerOfTwo(final int input) {
        return input != 0 && (input & input - 1) == 0;
    }

    /** 2 的幂判定（long）。 */
    public static boolean isPowerOfTwo(final long input) {
        return input != 0L && (input & input - 1L) == 0L;
    }

    /** 最小覆盖 2 的幂（int；UI 网格尺寸等索引场景）。 */
    public static int smallestEncompassingPowerOfTwo(final int input) {
        int result = input - 1;
        result |= result >> 1;
        result |= result >> 2;
        result |= result >> 4;
        result |= result >> 8;
        result |= result >> 16;
        return result + 1;
    }

    /** ceil(log2(input))（int；de Bruijn 序列照原版）。 */
    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = {
        0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };

    public static int ceillog2(int input) {
        input = isPowerOfTwo(input) ? input : smallestEncompassingPowerOfTwo(input);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) (input * 125613361L >> 27) & 31];
    }

    public static int log2(final int input) {
        return ceillog2(input) - (isPowerOfTwo(input) ? 0 : 1);
    }

    /** IntRefactor256 的 log2（位长语义：floor(log2(|v|))；0 → -1）。 */
    public static int log2(final IntRefactor256 input) {
        return input.isZero() ? -1 : abs(input).bitLength() - 1;
    }

    /** murmurHash3Mixer（int hash 混合；索引域无语义精度问题——保留原语种）。 */
    public static int murmurHash3Mixer(int hash) {
        hash ^= hash >>> 16;
        hash *= -2048144789;
        hash ^= hash >>> 13;
        hash *= -1028477387;
        return hash ^ hash >>> 16;
    }

    /** 二分查找（int 索引域——保留原语种）。 */
    public static int binarySearch(int from, final int to, final java.util.function.IntPredicate condition) {
        int len = to - from;
        while (len > 0) {
            int half = len / 2;
            int middle = from + half;
            if (condition.test(middle)) {
                len = half;
            } else {
                from = middle + 1;
                len -= half + 1;
            }
        }
        return from;
    }

    // ═══════════ 坐标种子（IntRefactor256 版；回绕语义保留在 256 位内） ═══════════

    /**
     * getSeed 的 256 版（原版 Deprecated：x*3129871 ^ z*116129781 ^ y，long 回绕是特性）。
     * 256 版把回绕圈从 64 位扩到 256 位——边境之地的坐标种子不再互相碰撞。
     */
    public static IntRefactor256 getSeed(final IntRefactor256 x, final IntRefactor256 y, final IntRefactor256 z) {
        IntRefactor256 seed = x.multiply(IntRefactor256.of(3129871L))
                .xor(z.multiply(IntRefactor256.of(116129781L)))
                .xor(y);
        seed = seed.multiply(seed).multiply(IntRefactor256.of(42317861L))
                .add(seed.multiply(IntRefactor256.of(11L)));
        return seed.shiftRight(16);
    }

    // ═══════════ HSV → ARGB（输入 256 化，输出 int 打包零 MC 依赖） ═══════════

    /** 复刻 net.minecraft.util.ARGB.color 的打包语义（alpha/r/g/b 各 8 位）。 */
    public static int argb(final int alpha, final int red, final int green, final int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public static int hsvToRgb(final FloatRefactor256 hue, final FloatRefactor256 saturation, final FloatRefactor256 value) {
        return hsvToArgb(hue, saturation, value, 0);
    }

    public static int hsvToArgb(final FloatRefactor256 hue, final FloatRefactor256 saturation, final FloatRefactor256 value, final int alpha) {
        final FloatRefactor256 six = FloatRefactor256.of(6.0);
        final int h = positiveModulo(hue.multiply(six).toIntRefactor256(), IntRefactor256.of(6)).toInt();
        final FloatRefactor256 f = hue.multiply(six).subtract(FloatRefactor256.of(h));
        final FloatRefactor256 p = value.multiply(FloatRefactor256.ONE.subtract(saturation));
        final FloatRefactor256 q = value.multiply(FloatRefactor256.ONE.subtract(f.multiply(saturation)));
        final FloatRefactor256 t = value.multiply(FloatRefactor256.ONE.subtract(FloatRefactor256.ONE.subtract(f).multiply(saturation)));
        final FloatRefactor256 red;
        final FloatRefactor256 green;
        final FloatRefactor256 blue;
        switch (h) {
            case 0 -> { red = value; green = t; blue = p; }
            case 1 -> { red = q; green = value; blue = p; }
            case 2 -> { red = p; green = value; blue = t; }
            case 3 -> { red = p; green = q; blue = value; }
            case 4 -> { red = t; green = p; blue = value; }
            case 5 -> { red = value; green = p; blue = q; }
            default -> throw new IllegalStateException("HSV index out of range: " + h);
        }
        return argb(alpha,
                clamp(red.multiply(FloatRefactor256.of(255.0)).toIntRefactor256(), IntRefactor256.ZERO, IntRefactor256.of(255)).toInt(),
                clamp(green.multiply(FloatRefactor256.of(255.0)).toIntRefactor256(), IntRefactor256.ZERO, IntRefactor256.of(255)).toInt(),
                clamp(blue.multiply(FloatRefactor256.of(255.0)).toIntRefactor256(), IntRefactor256.ZERO, IntRefactor256.of(255)).toInt());
    }

    // ═══════════ 插值整数族（DynamicNumber） ═══════════

    /** lerpInt 的 256 版（p0 + floor(alpha * (p1 - p0))）。 */
    public static IntRefactor256 lerpInt(final FloatRefactor256 alpha, final IntRefactor256 p0, final IntRefactor256 p1) {
        final FloatRefactor256 delta = FloatRefactor256.of(p1.subtract(p0).toBigInteger());
        // floor(FloatRefactor256) 重载——真 floor（先 toBigInteger 截断会把负数弄错 1）
        return p0.add(floor(alpha.multiply(delta)).toIntRefactor256());
    }

    /** lerpDiscrete 的 256 版。 */
    public static IntRefactor256 lerpDiscrete(final FloatRefactor256 alpha, final IntRefactor256 p0, final IntRefactor256 p1) {
        final IntRefactor256 delta = p1.subtract(p0).subtract(IntRefactor256.ONE);
        IntRefactor256 result = p0.add(floor(alpha.multiply(FloatRefactor256.of(delta.toBigInteger()))).toIntRefactor256());
        if (alpha.compareTo(FloatRefactor256.ZERO) > 0) {
            result = result.add(IntRefactor256.ONE);
        }
        return result;
    }

    // ═══════════ 批2：插值家族（FloatRefactor256 全家） ═══════════

    /** lerp（p0 + alpha·(p1-p0)）。 */
    public static FloatRefactor256 lerp(final FloatRefactor256 alpha, final FloatRefactor256 p0, final FloatRefactor256 p1) {
        return p0.add(alpha.multiply(p1.subtract(p0)));
    }

    /** 双线性插值。 */
    public static FloatRefactor256 lerp2(final FloatRefactor256 a1, final FloatRefactor256 a2,
            final FloatRefactor256 x00, final FloatRefactor256 x10, final FloatRefactor256 x01, final FloatRefactor256 x11) {
        return lerp(a2, lerp(a1, x00, x10), lerp(a1, x01, x11));
    }

    /** 三线性插值。 */
    public static FloatRefactor256 lerp3(final FloatRefactor256 a1, final FloatRefactor256 a2, final FloatRefactor256 a3,
            final FloatRefactor256 x000, final FloatRefactor256 x100, final FloatRefactor256 x010, final FloatRefactor256 x110,
            final FloatRefactor256 x001, final FloatRefactor256 x101, final FloatRefactor256 x011, final FloatRefactor256 x111) {
        return lerp(a3, lerp2(a1, a2, x000, x100, x010, x110), lerp2(a1, a2, x001, x101, x011, x111));
    }

    /** clampedLerp（factor 钳制到 [0,1] 后 lerp）。 */
    public static FloatRefactor256 clampedLerp(final FloatRefactor256 factor, final FloatRefactor256 min, final FloatRefactor256 max) {
        if (factor.compareTo(FloatRefactor256.ZERO) < 0) {
            return min;
        }
        return factor.compareTo(FloatRefactor256.ONE) > 0 ? max : lerp(factor, min, max);
    }

    /** inverseLerp（(value-min)/(max-min)）。 */
    public static FloatRefactor256 inverseLerp(final FloatRefactor256 value, final FloatRefactor256 min, final FloatRefactor256 max) {
        return value.subtract(min).divide(max.subtract(min));
    }

    /** map（区间重映射）。 */
    public static FloatRefactor256 map(final FloatRefactor256 value, final FloatRefactor256 fromMin, final FloatRefactor256 fromMax,
            final FloatRefactor256 toMin, final FloatRefactor256 toMax) {
        return lerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    /** clampedMap（inverseLerp 钳制版）。 */
    public static FloatRefactor256 clampedMap(final FloatRefactor256 value, final FloatRefactor256 fromMin, final FloatRefactor256 fromMax,
            final FloatRefactor256 toMin, final FloatRefactor256 toMax) {
        return clampedLerp(inverseLerp(value, fromMin, fromMax), toMin, toMax);
    }

    /** Catmull-Rom 插值（0.5·(2p1 + (p2-p0)a + (2p0-5p1+4p2-p3)a² + (3p1-p0-3p2+p3)a³)）。 */
    public static FloatRefactor256 catmullrom(final FloatRefactor256 alpha, final FloatRefactor256 p0, final FloatRefactor256 p1,
            final FloatRefactor256 p2, final FloatRefactor256 p3) {
        final FloatRefactor256 a2 = square(alpha);
        final FloatRefactor256 a3 = cube(alpha);
        return lerp(FloatRefactor256.of(0.5),
                FloatRefactor256.ZERO,
                FloatRefactor256.of(2.0).multiply(p1)
                        .add(p2.subtract(p0).multiply(alpha))
                        .add(FloatRefactor256.of(2.0).multiply(p0).subtract(FloatRefactor256.of(5.0).multiply(p1))
                                .add(FloatRefactor256.of(4.0).multiply(p2)).subtract(p3).multiply(a2))
                        .add(FloatRefactor256.of(3.0).multiply(p1).subtract(p0)
                                .subtract(FloatRefactor256.of(3.0).multiply(p2)).add(p3).multiply(a3)));
    }

    /** smoothstep（x³(6x²-15x+10)）。 */
    public static FloatRefactor256 smoothstep(final FloatRefactor256 x) {
        return cube(x).multiply(x.multiply(x.multiply(FloatRefactor256.of(6.0)).subtract(FloatRefactor256.of(15.0))).add(FloatRefactor256.of(10.0)));
    }

    /** smoothstep 导数（30x²(x-1)²）。 */
    public static FloatRefactor256 smoothstepDerivative(final FloatRefactor256 x) {
        return FloatRefactor256.of(30.0).multiply(square(x)).multiply(x.subtract(FloatRefactor256.ONE).multiply(x.subtract(FloatRefactor256.ONE)));
    }

    /** rotLerp（角度最短路径插值）。 */
    public static FloatRefactor256 rotLerp(final FloatRefactor256 a, final FloatRefactor256 from, final FloatRefactor256 to) {
        return from.add(a.multiply(wrapDegrees(to.subtract(from))));
    }

    /** rotLerpRad（弧度制，diff 归一化到 [-π, π)）。 */
    public static FloatRefactor256 rotLerpRad(final FloatRefactor256 a, final FloatRefactor256 from, final FloatRefactor256 to) {
        final FloatRefactor256 twoPi = FloatRefactor256.of(Math.PI * 2);
        final FloatRefactor256 pi = FloatRefactor256.of(Math.PI);
        // diff - 2π·floor((diff+π)/2π) ∈ [-π, π)
        FloatRefactor256 diff = to.subtract(from);
        diff = diff.subtract(twoPi.multiply(FloatRefactor256.of(floor(diff.add(pi).divide(twoPi)))));
        return from.add(a.multiply(diff));
    }

    // ═══════════ 批2：角度家族（IntRefactor256 整数版 + FloatRefactor256 浮点版） ═══════════

    /** wrapDegrees 整数版（归一化到 [-180, 180)；remainder 符号同被除数，对齐原版 int %）。 */
    public static IntRefactor256 wrapDegrees(final IntRefactor256 angle) {
        IntRefactor256 n = angle.remainder(IntRefactor256.of(360));
        if (n.compareTo(IntRefactor256.of(180)) >= 0) {
            n = n.subtract(IntRefactor256.of(360));
        }
        if (n.compareTo(IntRefactor256.of(-180)) < 0) {
            n = n.add(IntRefactor256.of(360));
        }
        return n;
    }

    /**
     * wrapDegrees 浮点版（归一化到 [-180, 180)）。floor 版取模保非负余数；
     * 注：alpha/360 为 RN 舍入除，商恰整数边界时可能偏差 360（角度值罕见场景，记录在案）。
     */
    public static FloatRefactor256 wrapDegrees(final FloatRefactor256 angle) {
        final FloatRefactor256 n = angle.subtract(FloatRefactor256.of(360)
                .multiply(FloatRefactor256.of(floor(angle.divide(FloatRefactor256.of(360))))));
        if (n.compareTo(FloatRefactor256.of(180)) >= 0) {
            return n.subtract(FloatRefactor256.of(360));
        }
        return n;
    }

    /** wrapDegrees90（归一化到 [-45, 45)）。 */
    public static FloatRefactor256 wrapDegrees90(final FloatRefactor256 angle) {
        final FloatRefactor256 n = angle.subtract(FloatRefactor256.of(90)
                .multiply(FloatRefactor256.of(floor(angle.divide(FloatRefactor256.of(90))))));
        if (n.compareTo(FloatRefactor256.of(45)) >= 0) {
            return n.subtract(FloatRefactor256.of(90));
        }
        return n;
    }

    /** degreesDifference（to-from 的最短角度差）。 */
    public static FloatRefactor256 degreesDifference(final FloatRefactor256 from, final FloatRefactor256 to) {
        return wrapDegrees(to.subtract(from));
    }

    public static FloatRefactor256 degreesDifferenceAbs(final FloatRefactor256 a, final FloatRefactor256 b) {
        return abs(degreesDifference(a, b));
    }

    /** rotateIfNecessary（限幅趋近）。 */
    public static FloatRefactor256 rotateIfNecessary(final FloatRefactor256 base, final FloatRefactor256 target, final FloatRefactor256 maxDiff) {
        final FloatRefactor256 delta = degreesDifference(base, target);
        return target.subtract(clamp(delta, maxDiff.negate(), maxDiff));
    }

    // ═══════════ 批2：趋近/波形 ═══════════

    /** approach（限幅趋近，increment 取绝对值）。 */
    public static FloatRefactor256 approach(final FloatRefactor256 current, final FloatRefactor256 target, FloatRefactor256 increment) {
        increment = abs(increment);
        return current.compareTo(target) < 0
                ? clamp(current.add(increment), current, target)
                : clamp(current.subtract(increment), target, current);
    }

    public static FloatRefactor256 approachDegrees(final FloatRefactor256 current, final FloatRefactor256 target, final FloatRefactor256 increment) {
        return approach(current, current.add(degreesDifference(current, target)), increment);
    }

    /** triangleWave（三角波）。 */
    public static FloatRefactor256 triangleWave(final FloatRefactor256 index, final FloatRefactor256 period) {
        final FloatRefactor256 q = FloatRefactor256.of(floor(index.divide(period)));
        final FloatRefactor256 m = index.subtract(period.multiply(q));
        final FloatRefactor256 quarter = period.multiply(FloatRefactor256.of(0.25));
        return abs(m.subtract(period.multiply(FloatRefactor256.of(0.5)))).subtract(quarter).divide(quarter);
    }

    /**
     * wobble（随机微抖；256 版保留公式语义）。
     * 注：原版依赖 RandomSource.createThreadLocalInstance(floor(coord*3000))——
     * 256 版的随机源接入待批 4（RandomSource 配套），当前以 1e-7/2 常量振幅语义先行。
     */
    public static FloatRefactor256 wobbleAmplitude() {
        return FloatRefactor256.of(1.0E-7).divide(FloatRefactor256.of(2.0));
    }

    // ═══════════ 批3：三角函数（LUT 索引 256 化——原版 (long)(x*SIN_SCALE) 在 x>8.8e14 溢出） ═══════════

    /** 正弦 LUT 量化（2^16 / 2π）。 */
    private static final double SIN_SCALE = 10430.378350470453;
    private static final int SIN_LUT_SIZE = 65536;
    /** 正弦 LUT（一次性初始化用 JDK Math.sin——常量表生成属离线步骤，非运算路径）。 */
    private static final FloatRefactor256[] SIN_LUT = new FloatRefactor256[SIN_LUT_SIZE];

    static {
        for (int i = 0; i < SIN_LUT_SIZE; i++) {
            SIN_LUT[i] = FloatRefactor256.of(Math.sin(i / SIN_SCALE));
        }
    }

    /**
     * LUT 索引（x·SIN_SCALE 截断向零后取低 16 位——复刻原版 (long) 截断语义而非 floor：
     * 负角时 floor 与 (long) 差 1 索引；IntRefactor256 任意精度，大坐标不再 long 溢出）。
     */
    private static int sinIndex(final FloatRefactor256 x) {
        final IntRefactor256 scaled = x.multiply(FloatRefactor256.of(SIN_SCALE)).toIntRefactor256();
        return scaled.and(IntRefactor256.of(0xFFFF)).toInt();
    }

    /** sin（弧度）。 */
    public static FloatRefactor256 sin(final FloatRefactor256 x) {
        return SIN_LUT[sinIndex(x)];
    }

    /** cos（弧度；LUT 偏移 1/4 周期 = 16384 索引）。 */
    public static FloatRefactor256 cos(final FloatRefactor256 x) {
        // 原版 (long)(i*SCALE + 16384.0)：先加偏移再一起截断（顺序影响负角索引）
        final IntRefactor256 scaled = x.multiply(FloatRefactor256.of(SIN_SCALE)).add(FloatRefactor256.of(16384)).toIntRefactor256();
        final IntRefactor256 idx = scaled.and(IntRefactor256.of(0xFFFF));
        return SIN_LUT[idx.toInt()];
    }

    // ═══════════ 批3：atan2（ASIN LUT + Newton 修正；invSqrt 用 sqrt 精确版） ═══════════

    private static final FloatRefactor256[] ASIN_TAB = new FloatRefactor256[257];
    private static final FloatRefactor256[] COS_TAB = new FloatRefactor256[257];

    static {
        for (int ind = 0; ind < 257; ind++) {
            final double v = ind / 256.0;
            final double asinv = Math.asin(v);
            COS_TAB[ind] = FloatRefactor256.of(Math.cos(asinv));
            ASIN_TAB[ind] = FloatRefactor256.of(asinv);
        }
    }

    /**
     * atan2（y, x）——复刻原版结构：归一化到单位圆 → ASIN LUT → 一阶 Newton 修正 → 象限还原。
     * 索引用 floor(y·256)（原版是 double 位模式魔数技巧，256 版用直接量化，语义等价且更稳）。
     */
    public static FloatRefactor256 atan2(FloatRefactor256 y, FloatRefactor256 x) {
        if (y.isNaN() || x.isNaN()) {
            return FloatRefactor256.NaN;
        }
        // atan2(±0, ±0)：JDK 语义返回 +0（IEEE 754 允许实现定义，选 JDK 对齐；同时防 0/0 → Inf 路径崩）
        if (x.isZero() && y.isZero()) {
            return FloatRefactor256.ZERO;
        }
        final FloatRefactor256 d2 = square(x).add(square(y));
        final boolean negY = y.signBit();
        if (negY) {
            y = y.negate();
        }
        final boolean negX = x.signBit();
        if (negX) {
            x = x.negate();
        }
        boolean steep = y.compareTo(x) > 0;
        if (steep) {
            final FloatRefactor256 t = x;
            x = y;
            y = t;
        }
        final FloatRefactor256 rinv = invSqrt(d2);
        x = x.multiply(rinv);
        y = y.multiply(rinv);
        // 索引：floor(y·256)（y ∈ [0,1] 归一化后）
        final int index = MathHelperRefactor.clamp(floor(y.multiply(FloatRefactor256.of(256))).toIntRefactor256(),
                IntRefactor256.ZERO, IntRefactor256.of(256)).toInt();
        final FloatRefactor256 phi = ASIN_TAB[index];
        final FloatRefactor256 cPhi = COS_TAB[index];
        // sPhi = sin(phi) = index/256（原版由 FRAC_BIAS 位技巧量化得到；写精确 y 会让一阶修正退化为 ≈0）
        final FloatRefactor256 sPhi = FloatRefactor256.of(index / 256.0);
        final FloatRefactor256 sd = y.multiply(cPhi).subtract(x.multiply(sPhi));
        final FloatRefactor256 d = FloatRefactor256.of(6.0).add(square(sd)).multiply(sd).divide(FloatRefactor256.of(6.0));
        FloatRefactor256 theta = phi.add(d);
        if (steep) {
            theta = HALF_PI.subtract(theta);
        }
        if (negX) {
            theta = PI.subtract(theta);
        }
        if (negY) {
            theta = theta.negate();
        }
        return theta;
    }

    // ═══════════ 批3：长度族（FloatRefactor256） ═══════════

    public static FloatRefactor256 lengthSquared(final FloatRefactor256 x, final FloatRefactor256 y) {
        return square(x).add(square(y));
    }

    public static FloatRefactor256 length(final FloatRefactor256 x, final FloatRefactor256 y) {
        return sqrt(lengthSquared(x, y));
    }

    public static FloatRefactor256 lengthSquared(final FloatRefactor256 x, final FloatRefactor256 y, final FloatRefactor256 z) {
        return square(x).add(square(y)).add(square(z));
    }

    public static FloatRefactor256 length(final FloatRefactor256 x, final FloatRefactor256 y, final FloatRefactor256 z) {
        return sqrt(lengthSquared(x, y, z));
    }

    // ═══════════ 批3：rayIntersectsAABB（坐标参数版——Vec3/AABB 256 化属另一工程，此处提供纯坐标入口） ═══════════

    /** 复刻原版 Mth.rayIntersectsAABB 的 slab + Andrew Woo 分离轴判定（全 FloatRefactor256 坐标）。 */
    public static boolean rayIntersectsAABB(
            final FloatRefactor256 rayStartX, final FloatRefactor256 rayStartY, final FloatRefactor256 rayStartZ,
            final FloatRefactor256 rayDirX, final FloatRefactor256 rayDirY, final FloatRefactor256 rayDirZ,
            final FloatRefactor256 minX, final FloatRefactor256 minY, final FloatRefactor256 minZ,
            final FloatRefactor256 maxX, final FloatRefactor256 maxY, final FloatRefactor256 maxZ) {
        final FloatRefactor256 half = FloatRefactor256.of(0.5);
        final FloatRefactor256 centerX = minX.add(maxX).multiply(half);
        final FloatRefactor256 extentX = maxX.subtract(minX).multiply(half);
        final FloatRefactor256 diffX = rayStartX.subtract(centerX);
        if (abs(diffX).compareTo(extentX) > 0 && diffX.multiply(rayDirX).compareTo(FloatRefactor256.ZERO) >= 0) {
            return false;
        }
        final FloatRefactor256 centerY = minY.add(maxY).multiply(half);
        final FloatRefactor256 extentY = maxY.subtract(minY).multiply(half);
        final FloatRefactor256 diffY = rayStartY.subtract(centerY);
        if (abs(diffY).compareTo(extentY) > 0 && diffY.multiply(rayDirY).compareTo(FloatRefactor256.ZERO) >= 0) {
            return false;
        }
        final FloatRefactor256 centerZ = minZ.add(maxZ).multiply(half);
        final FloatRefactor256 extentZ = maxZ.subtract(minZ).multiply(half);
        final FloatRefactor256 diffZ = rayStartZ.subtract(centerZ);
        if (abs(diffZ).compareTo(extentZ) > 0 && diffZ.multiply(rayDirZ).compareTo(FloatRefactor256.ZERO) >= 0) {
            return false;
        }
        final FloatRefactor256 adx = abs(rayDirX), ady = abs(rayDirY), adz = abs(rayDirZ);
        FloatRefactor256 f = rayDirY.multiply(diffZ).subtract(rayDirZ.multiply(diffY));
        if (abs(f).compareTo(extentY.multiply(adz).add(extentZ.multiply(ady))) > 0) {
            return false;
        }
        f = rayDirZ.multiply(diffX).subtract(rayDirX.multiply(diffZ));
        if (abs(f).compareTo(extentX.multiply(adz).add(extentZ.multiply(adx))) > 0) {
            return false;
        }
        f = rayDirX.multiply(diffY).subtract(rayDirY.multiply(diffX));
        return abs(f).compareTo(extentX.multiply(ady).add(extentY.multiply(adx))) < 0;
    }

    // ═══════════ 批4：随机族（java.util.Random 参数版——零 MC 依赖；RandomSource 适配由调用方包一层） ═══════════

    /** randomBetweenInclusive 的 256 版（区间宽度 ≤ 62 位走 nextLong(bound)；超大区间 nextBytes+mod，有偏已注释）。 */
    public static IntRefactor256 randomBetweenInclusive(final java.util.Random random, final IntRefactor256 min, final IntRefactor256 max) {
        final java.math.BigInteger bound = max.subtract(min).add(IntRefactor256.ONE).toBigInteger();
        if (bound.signum() <= 0) {
            return min;
        }
        if (bound.bitLength() <= 62) {
            return min.add(IntRefactor256.of(random.nextLong(bound.longValue())));
        }
        // 超大区间（边境之地坐标撒点）：nextBytes + mod——模偏差存在，均匀性降级（记录在案）
        final byte[] buf = new byte[(bound.bitLength() + 7) / 8];
        random.nextBytes(buf);
        return min.add(IntRefactor256.of(new java.math.BigInteger(buf).mod(bound)));
    }

    /** randomBetween 的 256 版（min + nextFloat·(max-min)）。 */
    public static FloatRefactor256 randomBetween(final java.util.Random random, final FloatRefactor256 min, final FloatRefactor256 max) {
        return lerp(FloatRefactor256.of(random.nextFloat()), min, max);
    }

    /** normal 的 256 版（mean + nextGaussian·deviation）。 */
    public static FloatRefactor256 normal(final java.util.Random random, final FloatRefactor256 mean, final FloatRefactor256 deviation) {
        return mean.add(FloatRefactor256.of(random.nextGaussian()).multiply(deviation));
    }

    /**
     * createInsecureUUID（Random 参数版）。UUID 恒 128 位——无语义扩展空间，照原版公式；
     * 放此处保持 API 族完整（MC 的 RandomSource 由调用方适配）。
     */
    public static java.util.UUID createInsecureUUID(final java.util.Random random) {
        final long most = random.nextLong() & -61441L | 16384L;
        final long least = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new java.util.UUID(most, least);
    }

    /** wobble 完整化（随机源以 floor(coord·3000) 播种；coord 巨大时种子截断到 long——同原版语义）。 */
    public static FloatRefactor256 wobble(final java.util.Random random, final FloatRefactor256 coord) {
        final long seed = floor(coord.multiply(FloatRefactor256.of(3000.0))).toIntRefactor256().toLong();
        random.setSeed(seed);
        final FloatRefactor256 jitter = FloatRefactor256.of(2.0 * random.nextDouble() - 1.0).multiply(wobbleAmplitude());
        return coord.add(jitter);
    }

    // ═══════════ 批4：角度打包/工具（int 索引或 String 解析——无语义精度问题） ═══════════

    /** packDegrees 的 256 版（floor(angle·256/360) 截断为 byte）。 */
    public static byte packDegrees(final FloatRefactor256 angle) {
        return (byte) floor(angle.multiply(FloatRefactor256.of(256.0 / 360.0))).toIntRefactor256().toInt();
    }

    /** unpackDegrees 的 256 版（byte → FloatRefactor256 角度）。 */
    public static FloatRefactor256 unpackDegrees(final byte rot) {
        return FloatRefactor256.of(rot * 360 / 256.0);
    }

    /** smallestSquareSide（int 索引——UI 网格尺寸，无语义精度问题，保留 int）。 */
    public static int smallestSquareSide(final int itemCount) {
        if (itemCount < 0) {
            throw new IllegalArgumentException("itemCount must be greater than or equal to zero");
        }
        return (int) Math.ceil(Math.sqrt(itemCount));
    }

    /** getInt（String 解析；NumberUtils.toInt 的自包含等价——trim + parseInt 兜底 def）。 */
    public static int getInt(final String input, final int def) {
        try {
            return Integer.parseInt(input.trim());
        } catch (final RuntimeException e) {
            return def;
        }
    }

    // ═══════════ 批4：明确跳过项（依赖 joml/Fraction/IntStream 或无 256 语义） ═══════════

    /**
     * 跳过清单（批4 决策记录）：
     * <ul>
     *   <li>rotationAroundAxis（joml Quaternionf 依赖——Vec3/Quaternion 256 化属另一工程，待 Vec3Refactor256）；</li>
     *   <li>mulAndTruncate（commons Fraction 依赖——可用 IntRefactor256 分子/分母模拟，价值低，待定）；</li>
     *   <li>outFromOrigin（IntStream 惰性索引——int 索引域无语义精度问题，直接用原版 Mth）；</li>
     *   <li>fastInvSqrt/fastInvCubeRoot（魔数位技巧依赖原生浮点位宽——256 位无对应魔数；
     *       invSqrt 已由 sqrt 精确版替代，cbrt 待 FloatRefactor256.exp/ln 族后再做）；</li>
     *   <li>wobble 原版的 RandomSource.createThreadLocalInstance——已由 Random 参数版覆盖。</li>
     * </ul>
     */
}

