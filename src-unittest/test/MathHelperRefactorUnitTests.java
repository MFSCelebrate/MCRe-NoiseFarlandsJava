package test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math._256bit_Refactoring.DynamicNumber;
import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.MathHelperRefactor;
import java.math.BigInteger;
import java.util.Random;

/**
 * 由 {@code tmp/MathHelperRefactorCheck.java} 迁移而来（2026-11-17）。
 * <p>原脚本是 {@code main()} + PASS/FAIL 计数式自检；此处<b>完整保留原始逻辑与期望值</b>，
 * 只把外壳换成 JUnit：脚本输出被捕获，出现失败标记即断言失败。
 */
class MathHelperRefactorUnitTests {

    static int fail = 0;

    static void chk(final boolean ok, final String tag) {
        if (!ok) {
            fail++;
            if (fail <= 20) System.out.println("FAIL " + tag);
        }
    }

    /** 原版 Mth.wrapDegrees 语义复刻（int） */
    static int origWrapDegrees(int angle) {
        int n = angle % 360;
        if (n >= 180) n -= 360;
        if (n < -180) n += 360;
        return n;
    }

    @Test
    @DisplayName("MathHelperRefactor 正确性自检")
    void scriptRuns() throws Exception {
        final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        final java.io.PrintStream originalOut = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {

                final Random r = new Random(20261113L);
                DynamicNumber.setDefaultType(DynamicNumber.Type.BIG_INTEGER);

                // ═══ 1. 常量 ═══
                chk(MathHelperRefactor.PI.toDouble() == Math.PI, "PI");
                chk(MathHelperRefactor.HALF_PI.toDouble() == Math.PI / 2, "HALF_PI");
                chk(MathHelperRefactor.SQRT_OF_TWO.toDouble() == Math.sqrt(2.0), "SQRT_OF_TWO");
                chk(MathHelperRefactor.DEG_TO_RAD.toDouble() == Math.PI / 180.0, "DEG_TO_RAD");

                // ═══ 2. floor/ceil（FloatRefactor256 小数场景 vs JDK + 大值；DynamicNumber 整数恒等）═══
                for (int i = 0; i < 20_000; i++) {
                    final double d = (r.nextInt(2_000_000) - 1_000_000) / 1024.0; // [-976.6, 976.6] 含小数
                    final FloatRefactor256 x = FloatRefactor256.of(d);
                    chk(MathHelperRefactor.floor(x).toDouble() == Math.floor(d), "f-float floor " + i);
                    chk(MathHelperRefactor.ceil(x).toDouble() == Math.ceil(d), "f-float ceil " + i);
                    chk(MathHelperRefactor.frac(x).toDouble() == d - Math.floor(d), "f-float frac " + i);
                }
                // 负数专项
                chk(MathHelperRefactor.floor(FloatRefactor256.of(-3.25)).toDouble() == -4.0, "floor -3.25");
                chk(MathHelperRefactor.ceil(FloatRefactor256.of(-3.25)).toDouble() == -3.0, "ceil -3.25");
                chk(MathHelperRefactor.frac(FloatRefactor256.of(-3.25)).toDouble() == 0.75, "frac -3.25");
                chk(MathHelperRefactor.floor(FloatRefactor256.of(3.75)).toDouble() == 3.0, "floor 3.75");
                chk(MathHelperRefactor.ceil(FloatRefactor256.of(3.75)).toDouble() == 4.0, "ceil 3.75");
                // 256 专属：原版 int floor 在 2^31 溢出——256 版精确（2^40 + 0.5）
                final FloatRefactor256 big = FloatRefactor256.of(1099511627776.0).add(FloatRefactor256.of(0.5));
                chk(MathHelperRefactor.floor(big).toBigInteger().equals(BigInteger.valueOf(1099511627776L)), "floor 2^40+.5");
                // DynamicNumber（整数容器）floor/ceil 恒等
                for (final long v : new long[] {0L, 7L, -7L, Long.MAX_VALUE, Long.MIN_VALUE}) {
                    chk(MathHelperRefactor.floor(DynamicNumber.of(v)).toBigInteger().equals(BigInteger.valueOf(v)), "dyn floor 恒等 " + v);
                    chk(MathHelperRefactor.ceil(DynamicNumber.of(v)).toBigInteger().equals(BigInteger.valueOf(v)), "dyn ceil 恒等 " + v);
                    chk(MathHelperRefactor.lfloor(DynamicNumber.of(v)) == v, "lfloor " + v);
                    chk(MathHelperRefactor.ceilLong(DynamicNumber.of(v)) == v, "ceilLong " + v);
                }

                // ═══ 3. square/cube/sqrt/invSqrt（小值域 vs JDK double）═══
                for (int i = 0; i < 5_000; i++) {
                    final double d = 1.0 + r.nextDouble() * 1000;
                    final FloatRefactor256 x = FloatRefactor256.of(d);
                    chk(Math.abs(MathHelperRefactor.square(x).toDouble() - d * d) < Math.abs(d * d) * 1e-13, "square " + i);
                    chk(Math.abs(MathHelperRefactor.cube(x).toDouble() - d * d * d) < Math.abs(d * d * d) * 1e-12, "cube " + i);
                    chk(Math.abs(MathHelperRefactor.sqrt(x).toDouble() - Math.sqrt(d)) < 1e-9 * Math.sqrt(d), "sqrt " + i);
                    chk(Math.abs(MathHelperRefactor.invSqrt(x).toDouble() - 1.0 / Math.sqrt(d)) < 1e-9 / Math.sqrt(d), "invSqrt " + i);
                }
                chk(MathHelperRefactor.sqrt(FloatRefactor256.of(4.0)).toDouble() == 2.0, "sqrt 4 = 2");
                chk(MathHelperRefactor.sqrt(FloatRefactor256.of(-1.0)).isNaN(), "sqrt -1 = NaN");
                chk(MathHelperRefactor.frac(FloatRefactor256.of(3.75)).toDouble() == 0.75, "frac 3.75");
                chk(MathHelperRefactor.frac(FloatRefactor256.of(-3.25)).toDouble() == 0.75, "frac -3.25 = 0.75");

                // ═══ 4. 取模族 vs BigInteger 复刻 ═══
                for (int i = 0; i < 20_000; i++) {
                    final BigInteger a = new BigInteger(200, r), b = new BigInteger(60, r).add(BigInteger.TWO);
                    final IntRefactor256 x = IntRefactor256.of(a), y = IntRefactor256.of(b);
                    chk(MathHelperRefactor.floorDiv(x, y).toBigInteger().equals(a.divide(b).subtract(a.remainder(b).signum() != 0 && a.signum() != b.signum() ? BigInteger.ONE : BigInteger.ZERO)), "floorDiv " + i);
                    final BigInteger pm = a.mod(b); // BigInteger.mod = 正余数
                    chk(MathHelperRefactor.positiveModulo(x, y).toBigInteger().equals(pm), "positiveModulo " + i);
                    chk(MathHelperRefactor.isMultipleOf(x, y) == (a.remainder(b).signum() == 0), "isMultipleOf " + i);
                    // positiveCeilDiv / roundToward
                    final BigInteger ceilDiv = a.divide(b).add(a.remainder(b).signum() != 0 ? BigInteger.ONE : BigInteger.ZERO);
                    chk(MathHelperRefactor.positiveCeilDiv(x, y).toBigInteger().equals(ceilDiv), "positiveCeilDiv " + i);
                    chk(MathHelperRefactor.roundToward(x, y).toBigInteger().equals(ceilDiv.multiply(b)), "roundToward " + i);
                }

                // ═══ 5. quantize ═══
                for (int i = 0; i < 5_000; i++) {
                    final BigInteger v = new BigInteger(100, r), res = BigInteger.valueOf(1 + r.nextInt(64));
                    final IntRefactor256 got = MathHelperRefactor.quantize(DynamicNumber.of(v), DynamicNumber.of(res));
                    chk(got.toBigInteger().equals(v.divide(res).multiply(res)), "quantize " + i);
                }

                // ═══ 6. getSeed（原版公式 BigInteger 复刻，小坐标）═══
                for (int i = 0; i < 10_000; i++) {
                    final long x = r.nextInt(2000) - 1000, y = r.nextInt(2000) - 1000, z = r.nextInt(2000) - 1000;
                    // 256 版语义：回绕圈从 64 位扩到 256 位（不回绕是设计意图）——BigInteger 精确复刻
                    BigInteger seed = BigInteger.valueOf(x).multiply(BigInteger.valueOf(3129871L))
                            .xor(BigInteger.valueOf(z).multiply(BigInteger.valueOf(116129781L)))
                            .xor(BigInteger.valueOf(y));
                    seed = seed.multiply(seed).multiply(BigInteger.valueOf(42317861L)).add(seed.multiply(BigInteger.valueOf(11L)));
                    final BigInteger want = seed.shiftRight(16);
                    final IntRefactor256 got = MathHelperRefactor.getSeed(IntRefactor256.of(x), IntRefactor256.of(y), IntRefactor256.of(z));
                    chk(got.toBigInteger().equals(want), "getSeed " + i);
                }

                // ═══ 7. hsvToArgb（原版公式复刻，小值域）═══
                for (int i = 0; i < 5_000; i++) {
                    final float h = r.nextFloat(), s = r.nextFloat(), v = r.nextFloat();
                    // 原版复刻
                    int hi = (int) (h * 6.0F) % 6;
                    float f = h * 6.0F - hi;
                    float p = v * (1.0F - s), q = v * (1.0F - f * s), t = v * (1.0F - (1.0F - f) * s);
                    float red, green, blue;
                    switch (hi) {
                        case 0 -> { red = v; green = t; blue = p; }
                        case 1 -> { red = q; green = v; blue = p; }
                        case 2 -> { red = p; green = v; blue = t; }
                        case 3 -> { red = p; green = q; blue = v; }
                        case 4 -> { red = t; green = p; blue = v; }
                        default -> { red = v; green = p; blue = q; }
                    }
                    final int want = (0 << 24) | (Math.min(255, Math.max(0, (int) (red * 255))) << 16)
                            | (Math.min(255, Math.max(0, (int) (green * 255))) << 8) | Math.min(255, Math.max(0, (int) (blue * 255)));
                    final int got = MathHelperRefactor.hsvToRgb(FloatRefactor256.of(h), FloatRefactor256.of(s), FloatRefactor256.of(v));
                    chk(got == want, "hsv " + i + " got=" + Integer.toHexString(got) + " want=" + Integer.toHexString(want));
                }

                // ═══ 8. 位逻辑族 vs JDK ═══
                for (int i = 0; i < 10_000; i++) {
                    final int v = r.nextInt();
                    chk(MathHelperRefactor.isPowerOfTwo(v) == (v != 0 && (v & v - 1) == 0), "isPowerOfTwo int " + i);
                    chk(MathHelperRefactor.ceillog2(v) == ceilLog2Ref(v), "ceillog2 " + i);
                    final long lv = r.nextLong();
                    chk(MathHelperRefactor.isPowerOfTwo(lv) == (lv != 0 && (lv & lv - 1) == 0), "isPowerOfTwo long " + i);
                }
                for (final long v : new long[] {0L, 1L, 2L, 1024L, 1L << 62, -1L, -8L}) {
                    chk(MathHelperRefactor.log2(IntRefactor256.of(v)) == (v == 0 ? -1 : 63 - Long.numberOfLeadingZeros(Math.abs(v))), "log2 Int256 " + v);
                }
                chk(MathHelperRefactor.murmurHash3Mixer(12345) == murmurRef(12345), "murmur");
                chk(MathHelperRefactor.binarySearch(0, 100, m -> m >= 42) == 42, "binarySearch");

                // ═══ 9. lerpInt/lerpDiscrete（小值域 vs 原版公式）═══
                for (int i = 0; i < 5_000; i++) {
                    final float a = r.nextFloat();
                    final int p0 = r.nextInt(2000) - 1000, p1 = r.nextInt(2000) - 1000;
                    // 256 语义：精确乘积后 floor（不复制原版 float 舍入抖动）
                    final int wantInt = new java.math.BigDecimal(Float.toString(a))
                            .multiply(java.math.BigDecimal.valueOf(p1 - p0))
                            .setScale(0, java.math.RoundingMode.FLOOR).intValue() + p0;
                    chk(MathHelperRefactor.lerpInt(FloatRefactor256.of(a), IntRefactor256.of(p0), IntRefactor256.of(p1)).toInt() == wantInt, "lerpInt " + i);
                    final int delta = p1 - p0;
                    final int wantDisc = p0 + new java.math.BigDecimal(Float.toString(a))
                            .multiply(java.math.BigDecimal.valueOf(delta - 1))
                            .setScale(0, java.math.RoundingMode.FLOOR).intValue() + (a > 0.0F ? 1 : 0);
                    chk(MathHelperRefactor.lerpDiscrete(FloatRefactor256.of(a), IntRefactor256.of(p0), IntRefactor256.of(p1)).toInt() == wantDisc, "lerpDiscrete " + i);
                }

                // ═══ 10. absMax/chessboardDistance/sign/abs/clamp ═══
                chk(MathHelperRefactor.absMax(DynamicNumber.of(-5L), DynamicNumber.of(3L)).toBigInteger().equals(BigInteger.valueOf(5)), "absMax");
                chk(MathHelperRefactor.chessboardDistance(DynamicNumber.of(0L), DynamicNumber.of(0L), DynamicNumber.of(3L), DynamicNumber.of(-7L)).toBigInteger().equals(BigInteger.valueOf(7)), "chessboard");
                chk(MathHelperRefactor.sign(DynamicNumber.of(-9L)) == -1 && MathHelperRefactor.sign(DynamicNumber.of(0L)) == 0, "sign");
                chk(MathHelperRefactor.clamp(IntRefactor256.of(5L), IntRefactor256.of(1L), IntRefactor256.of(3L)).toLong() == 3, "clamp int");
                chk(MathHelperRefactor.clamp(FloatRefactor256.of(5.0), FloatRefactor256.of(1.0), FloatRefactor256.of(3.0)).toDouble() == 3.0, "clamp float");
                chk(MathHelperRefactor.clamp(DynamicNumber.of(5L), DynamicNumber.of(1L), DynamicNumber.of(3L)).toBigInteger().equals(BigInteger.valueOf(3)), "clamp dyn");

                // ═══ 11. 256 专属大值场景（原版做不到的）═══
                final IntRefactor256 huge = IntRefactor256.of(BigInteger.ONE.shiftLeft(200));
                // 256 位容器边界：2^400 必然回绕为 0（设计如此）；不溢出场景走 DynamicNumber BB 配置
                chk(MathHelperRefactor.square(huge).isZero(), "square 2^200 回绕 0（容器边界）");
                DynamicNumber.setDefaultType(DynamicNumber.Type.BIG_INTEGER);
                final DynamicNumber bigSquare = DynamicNumber.of(huge.toBigInteger()).multiply(DynamicNumber.of(huge.toBigInteger()));
                chk(bigSquare.toBigInteger().bitLength() == 401, "BB 平方 400 位不溢出");
                DynamicNumber.setDefaultType(DynamicNumber.Type.LONG);
                final IntRefactor256 seedBig = MathHelperRefactor.getSeed(huge, huge, huge);
                chk(seedBig.bitLength() > 100, "getSeed 大坐标非坍缩");
                chk(MathHelperRefactor.log2(huge) == 200, "log2 2^200");

                System.out.println(fail == 0 ? "ALL PASS（MathHelperRefactor 批1）" : "TOTAL FAIL = " + fail);
    
        } finally {
            System.setOut(originalOut);
        }

        final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
        final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
        assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
    }


    static int ceilLog2Ref(final int input) {
        int v = input != 0 && (input & input - 1) == 0 ? input : smallestEnc(input);
        return DEBRUIJ[(int) (v * 125613361L >> 27) & 31];
    }

    static int smallestEnc(int input) {
        int result = input - 1;
        result |= result >> 1;
        result |= result >> 2;
        result |= result >> 4;
        result |= result >> 8;
        result |= result >> 16;
        return result + 1;
    }

    static final int[] DEBRUIJ = {
        0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9
    };

    static int murmurRef(int hash) {
        hash ^= hash >>> 16;
        hash *= -2048144789;
        hash ^= hash >>> 13;
        hash *= -1028477387;
        return hash ^ hash >>> 16;
    }
}
