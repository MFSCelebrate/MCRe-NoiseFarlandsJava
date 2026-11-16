package net.minecraft.world.level.levelgen.synth;

import com.google.common.annotations.VisibleForTesting;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

public final class ImprovedNoise {
    private static final float SHIFT_UP_EPSILON = 1.0E-7F;

    /** 🔧 MCRe 精确路径：原版 {@code 1.0E-7F} 是 float 字面量，取它的精确值参与精确运算。 */
    private static final BigDecimal FLOAT_EPS_1E7 = ExactNoiseMath.ofFloat(1.0E-7F);

    private final byte[] p;
    public final double xo;
    public final double yo;
    public final double zo;

    /** 🔧 MCRe 精确路径：xo/yo/zo 的精确值缓存（BigDecimal 不可变，惰性初始化并发安全）。 */
    private BigDecimal xoExact;

    private BigDecimal yoExact;
    private BigDecimal zoExact;

    private static boolean isProgressiveFarlands() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.progressiveFarlands;
    }

    private static boolean simulatedWraparoundOverflowMode() {
        WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.simulatedWraparoundOverflow;
    }

    public ImprovedNoise(final RandomSource random) {
        this.xo = random.nextDouble() * 256.0;
        this.yo = random.nextDouble() * 256.0;
        this.zo = random.nextDouble() * 256.0;
        this.p = new byte[256];

        for (int i = 0; i < 256; i++) {
            this.p[i] = (byte) i;
        }

        for (int i = 0; i < 256; i++) {
            int offset = random.nextInt(256 - i);
            byte tmp = this.p[i];
            this.p[i] = this.p[i + offset];
            this.p[i + offset] = tmp;
        }
    }

    public double noise(final double _x, final double _y, final double _z) {

        return this.noise(_x, _y, _z, 0.0, 0.0);
    }

    public double noise(final double _x, final double _y, final double _z, final double yScale, final double yFudge) {

        double x = _x + this.xo;
        double y = _y + this.yo;
        double z = _z + this.zo;

        int xf, yf, zf;
        if (simulatedWraparoundOverflowMode()) {
            xf = floorToIntWithWrap(x);
            yf = floorToIntWithWrap(y);
            zf = floorToIntWithWrap(z);
        } else {
            xf = Mth.floor(x);
            yf = Mth.floor(y);
            zf = Mth.floor(z);
        }

        double xr = x - xf;
        double yr = y - yf;
        double zr = z - zf;
        double yrFudge;
        if (yScale != 0.0) {
            double fudgeLimit;
            if (yFudge >= 0.0 && yFudge < yr) {
                fudgeLimit = yFudge;
            } else {
                fudgeLimit = yr;
            }
            yrFudge = Mth.floor(fudgeLimit / yScale + 1.0E-7F) * yScale;
        } else {
            yrFudge = 0.0;
        }

        return this.sampleAndLerp(xf, yf, zf, xr, yr - yrFudge, zr, yr);
    }

    /**
     * 🔧 MCRe「使用 BigDecimal / BigInteger 重写地形」——精确版噪声。
     *
     * <p>与原版 {@link #noise(double, double, double, double, double)} 的唯一区别： 坐标 {@code x = _x +
     * xo}、晶格定位 {@code floor} 与小数部分 {@code xr = x - xf} 全部用精确运算，彻底消除「大坐标 → ULP 量化 → 台阶（拉伸）」。
     *
     * <p><b>刻意保留的溢出语义</b>（这些是边境之地的成因，不是 bug）：
     *
     * <ul>
     *   <li>{@code xf = (int) Math.floor(x)} 的<b>饱和</b>——坐标超过 ±2^31 后所有采样点 落到同一晶格角 → 平面边境之地；
     *   <li>{@code xr = x - xf} 中 {@code xf} 用的是**饱和后**的 int，所以超大坐标下 {@code xr} 会变成巨大的数（插值权重失真）——与
     *       double 版行为一致；
     *   <li>{@code Mth.floor(fudgeLimit / yScale + 1.0E-7F)} 的 int 饱和。
     * </ul>
     *
     * 梯度点积与三线性插值仍用 double（输出值域 [-1,1]，精度绰绰有余），保证性能可控。
     */
    public double noiseExact(
            final BigDecimal _x,
            final BigDecimal _y,
            final BigDecimal _z,
            final BigDecimal yScale,
            final BigDecimal yFudge) {
        // 🔧 Bedrock 模式适配：输入与 xo 偏移先量化到 float（模拟基岩单精度），再精确计算
        // float 的值可精确表示为 BigDecimal → 精确计算无灾难性抵消 → 无地形拉伸
        final BigDecimal bx = _x;
        final BigDecimal by = _y;
        final BigDecimal bz = _z;
        final BigDecimal xoB = this.exactXo();
        final BigDecimal yoB = this.exactYo();
        final BigDecimal zoB = this.exactZo();
        final BigDecimal x = bx.add(xoB);
        final BigDecimal y = by.add(yoB);
        final BigDecimal z = bz.add(zoB);

        // ⚠️ 保留 int 饱和（平面边境之地的来源）
        final int xf = ExactNoiseMath.floorToIntSaturated(x);
        final int yf = ExactNoiseMath.floorToIntSaturated(y);
        final int zf = ExactNoiseMath.floorToIntSaturated(z);

        // ⚠️ 与原版逐字一致：xf 是饱和后的 int，xr = x - xf
        final BigDecimal xrExact = x.subtract(BigDecimal.valueOf(xf));
        final BigDecimal yrExact = y.subtract(BigDecimal.valueOf(yf));
        final BigDecimal zrExact = z.subtract(BigDecimal.valueOf(zf));

        final double xr = xrExact.doubleValue();
        final double yr = yrExact.doubleValue();
        final double zr = zrExact.doubleValue();

        double yrFudge;
        if (yScale.signum() != 0) {
            final BigDecimal fudgeLimit;
            if (yFudge.signum() >= 0 && yFudge.compareTo(yrExact) < 0) {
                fudgeLimit = yFudge;
            } else {
                fudgeLimit = yrExact;
            }
            // 原版：Mth.floor(fudgeLimit / yScale + 1.0E-7F) * yScale
            final BigDecimal scaled = fudgeLimit.divide(yScale, ExactNoiseMath.DIVISION).add(FLOAT_EPS_1E7);
            yrFudge = ExactNoiseMath.floorToIntSaturated(scaled) * yScale.doubleValue();
        } else {
            yrFudge = 0.0;
        }

        return this.sampleAndLerp(xf, yf, zf, xr, yr - yrFudge, zr, yr);
    }

    private BigDecimal exactXo() {
        BigDecimal v = this.xoExact;
        if (v == null) {
            v = new BigDecimal(this.xo);
            this.xoExact = v;
        }
        return v;
    }

    private BigDecimal exactYo() {
        BigDecimal v = this.yoExact;
        if (v == null) {
            v = new BigDecimal(this.yo);
            this.yoExact = v;
        }
        return v;
    }

    private BigDecimal exactZo() {
        BigDecimal v = this.zoExact;
        if (v == null) {
            v = new BigDecimal(this.zo);
            this.zoExact = v;
        }
        return v;
    }

    public double noiseWithDerivative(final double _x, final double _y, final double _z, final double[] derivativeOut) {

        // expandSinglePrecision 开启 或 非 BedrockMode：走 double 实现
        double x = _x + this.xo;
        double y = _y + this.yo;
        double z = _z + this.zo;
        int xf = Mth.floor(x);
        int yf = Mth.floor(y);
        int zf = Mth.floor(z);
        double xr = x - xf;
        double yr = y - yf;
        double zr = z - zf;
        return this.sampleWithDerivative(xf, yf, zf, xr, yr, zr, derivativeOut);
    }

    private static double gradDot(final int hash, final double x, final double y, final double z) {

        return SimplexNoise.dot(SimplexNoise.GRADIENT[hash & 15], x, y, z);
    }

    private int p(final int x) {
        return this.p[x & 0xFF] & 0xFF;
    }

    // ========== 插值工具 ==========
    private static double lerp(double delta, double start, double end) {
        if (isProgressiveFarlands()) {
            return start;
        }

        return start + delta * (end - start);
    }

    private static double lerp2(double delta1, double delta2,
            double start1, double end1,
            double start2, double end2) {

        double mid1 = lerp(delta1, start1, end1);
        double mid2 = lerp(delta1, start2, end2);
        return lerp(delta2, mid1, mid2);
    }

    private static double lerp3(double delta1, double delta2, double delta3,
            double v000, double v100, double v010, double v110,
            double v001, double v101, double v011, double v111) {

        double x00 = lerp(delta1, v000, v100);
        double x10 = lerp(delta1, v010, v110);
        double x01 = lerp(delta1, v001, v101);
        double x11 = lerp(delta1, v011, v111);
        double y0 = lerp(delta2, x00, x10);
        double y1 = lerp(delta2, x01, x11);
        return lerp(delta3, y0, y1);
    }

    private double sampleAndLerp(final int x, final int y, final int z, final double xr, final double yr, final double zr, final double yrOriginal) {
        int x0 = this.p(x);
        int x1 = this.p(x + 1);
        int xy00 = this.p(x0 + y);
        int xy01 = this.p(x0 + y + 1);
        int xy10 = this.p(x1 + y);
        int xy11 = this.p(x1 + y + 1);

        double d000 = gradDot(this.p(xy00 + z), xr, yr, zr);
        double d100 = gradDot(this.p(xy10 + z), xr - 1.0, yr, zr);
        double d010 = gradDot(this.p(xy01 + z), xr, yr - 1.0, zr);
        double d110 = gradDot(this.p(xy11 + z), xr - 1.0, yr - 1.0, zr);
        double d001 = gradDot(this.p(xy00 + z + 1), xr, yr, zr - 1.0);
        double d101 = gradDot(this.p(xy10 + z + 1), xr - 1.0, yr, zr - 1.0);
        double d011 = gradDot(this.p(xy01 + z + 1), xr, yr - 1.0, zr - 1.0);
        double d111 = gradDot(this.p(xy11 + z + 1), xr - 1.0, yr - 1.0, zr - 1.0);

        double xAlpha = Mth.smoothstep(xr);
        double yAlpha = Mth.smoothstep(yrOriginal);
        double zAlpha = Mth.smoothstep(zr);

        return ImprovedNoise.lerp3(xAlpha, yAlpha, zAlpha, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    private double sampleWithDerivative(final int x, final int y, final int z, final double xr, final double yr, final double zr, final double[] derivativeOut) {
        int x0 = this.p(x);
        int x1 = this.p(x + 1);
        int xy00 = this.p(x0 + y);
        int xy01 = this.p(x0 + y + 1);
        int xy10 = this.p(x1 + y);
        int xy11 = this.p(x1 + y + 1);
        int p000 = this.p(xy00 + z);
        int p100 = this.p(xy10 + z);
        int p010 = this.p(xy01 + z);
        int p110 = this.p(xy11 + z);
        int p001 = this.p(xy00 + z + 1);
        int p101 = this.p(xy10 + z + 1);
        int p011 = this.p(xy01 + z + 1);
        int p111 = this.p(xy11 + z + 1);

        // === 原 double 实现 ===
        int[] g000 = SimplexNoise.GRADIENT[p000 & 15];
        int[] g100 = SimplexNoise.GRADIENT[p100 & 15];
        int[] g010 = SimplexNoise.GRADIENT[p010 & 15];
        int[] g110 = SimplexNoise.GRADIENT[p110 & 15];
        int[] g001 = SimplexNoise.GRADIENT[p001 & 15];
        int[] g101 = SimplexNoise.GRADIENT[p101 & 15];
        int[] g011 = SimplexNoise.GRADIENT[p011 & 15];
        int[] g111 = SimplexNoise.GRADIENT[p111 & 15];

        double d000 = SimplexNoise.dot(g000, xr, yr, zr);
        double d100 = SimplexNoise.dot(g100, xr - 1.0, yr, zr);
        double d010 = SimplexNoise.dot(g010, xr, yr - 1.0, zr);
        double d110 = SimplexNoise.dot(g110, xr - 1.0, yr - 1.0, zr);
        double d001 = SimplexNoise.dot(g001, xr, yr, zr - 1.0);
        double d101 = SimplexNoise.dot(g101, xr - 1.0, yr, zr - 1.0);
        double d011 = SimplexNoise.dot(g011, xr, yr - 1.0, zr - 1.0);
        double d111 = SimplexNoise.dot(g111, xr - 1.0, yr - 1.0, zr - 1.0);

        double xAlpha = Mth.smoothstep(xr);
        double yAlpha = Mth.smoothstep(yr);
        double zAlpha = Mth.smoothstep(zr);

        double d1x = Mth.lerp3(xAlpha, yAlpha, zAlpha, g000[0], g100[0], g010[0], g110[0], g001[
        0], g101[0], g011[0], g111[0]);
        double d1y = Mth.lerp3(xAlpha, yAlpha, zAlpha, g000[1], g100[1], g010[1], g110[1], g001[
        1], g101[1], g011[1], g111[1]);
        double d1z = Mth.lerp3(xAlpha, yAlpha, zAlpha, g000[2], g100[2], g010[2], g110[2], g001[
        2], g101[2], g011[2], g111[2]);

        double d2x = Mth.lerp2(yAlpha, zAlpha, d100 - d000, d110 - d010, d101 - d001, d111 - d011);
        double d2y = Mth.lerp2(zAlpha, xAlpha, d010 - d000, d011 - d001, d110 - d100, d111 - d101);
        double d2z = Mth.lerp2(xAlpha, yAlpha, d001 - d000, d101 - d100, d011 - d010, d111 - d110);

        double xSD = Mth.smoothstepDerivative(xr);
        double ySD = Mth.smoothstepDerivative(yr);
        double zSD = Mth.smoothstepDerivative(zr);

        double dX = d1x + xSD * d2x;
        double dY = d1y + ySD * d2y;
        double dZ = d1z + zSD * d2z;

        derivativeOut[0] += dX;
        derivativeOut[1] += dY;
        derivativeOut[2] += dZ;

        return ImprovedNoise.lerp3(xAlpha, yAlpha, zAlpha, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    @VisibleForTesting
    public void parityConfigString(final StringBuilder sb) {
        NoiseUtils.parityNoiseOctaveConfigString(sb, this.xo, this.yo, this.zo, this.p);
    }

    private static int floorToIntWithWrap(double val) {
        if (val >= Integer.MAX_VALUE || val <= Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.floor(val);
    }
}