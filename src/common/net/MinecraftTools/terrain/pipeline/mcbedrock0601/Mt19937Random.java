package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

/**
 * 🔧 MCBedrock0601GenPipeline 噪声随机层：Minecraft 携带版 0.6.1 的 util/Random 逐位复刻。
 *
 * <p>内核 = <b>MT19937 梅森旋转</b>（N=624/M=397），<b>不是</b> Java 的 48 位 LCG——
 * 同 seed 下 PE 0.6.1 地形与 Java Beta 完全不同的根源。nextXxx 语义全部照抄 PE 源码
 * （handheld/src/util/Random.h），包括：
 * <ul>
 *   <li>{@code nextInt(n) = genrand_int32() % n}——<b>有偏取模</b>（非 Java 的拒绝采样），照抄；</li>
 *   <li>{@code nextBoolean} 用 0x8000000（2^27）掩码——PE 源码原样（疑似笔误），照抄保真；</li>
 *   <li>{@code nextFloat = (float)genrand_real2()}——先 double 乘 1/2^32 再截断 float；</li>
 *   <li>seed 按低 32 位截断（PE 的 C++ long 为 32 位）。</li>
 * </ul>
 */
public final class Mt19937Random {

    private static final int N = 624;
    private static final int M = 397;
    private static final int MATRIX_A = 0x9908b0df;
    private static final int UPPER_MASK = 0x80000000;
    private static final int LOWER_MASK = 0x7fffffff;

    private final int[] mt = new int[N];
    private int mti = N + 1;
    private long seed;
    private boolean haveNextNextGaussian;
    private float nextNextGaussian;

    public Mt19937Random() {
        setSeed(System.currentTimeMillis());
    }

    public Mt19937Random(final long seed) {
        setSeed(seed);
    }

    public long getSeed() {
        return this.seed;
    }

    public void setSeed(final long seed) {
        this.seed = seed;
        this.mti = N + 1;
        this.haveNextNextGaussian = false;
        this.nextNextGaussian = 0.0f;
        this.initGenrand(seed);
    }

    /** C++ long 为 32 位——Java long 只取低 32 位（与 PE 的 init_genrand(unsigned long) 截断一致） */
    private void initGenrand(final long s) {
        this.mt[0] = (int) s;
        for (this.mti = 1; this.mti < N; this.mti++) {
            // Knuth TAOCP Vol2 3rd Ed. P.106 multiplier（mt19937ar 2002/01/09 版）
            this.mt[this.mti] = (int) (1812433253L * (this.mt[this.mti - 1] ^ (this.mt[this.mti - 1] >>> 30)) + this.mti);
        }
    }

    /** generates a random number on [0,0xffffffff]-interval（unsigned） */
    public int genrandInt32() {
        int y;
        if (this.mti >= N) {
            int kk;
            if (this.mti == N + 1) {
                this.initGenrand(5489L); // 默认种子（PE/mt19937ar 同款）
            }
            for (kk = 0; kk < N - M; kk++) {
                y = (this.mt[kk] & UPPER_MASK) | (this.mt[kk + 1] & LOWER_MASK);
                this.mt[kk] = this.mt[kk + M] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            }
            for (; kk < N - 1; kk++) {
                y = (this.mt[kk] & UPPER_MASK) | (this.mt[kk + 1] & LOWER_MASK);
                this.mt[kk] = this.mt[kk + (M - N)] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            }
            y = (this.mt[N - 1] & UPPER_MASK) | (this.mt[0] & LOWER_MASK);
            this.mt[N - 1] = this.mt[M - 1] ^ (y >>> 1) ^ ((y & 1) != 0 ? MATRIX_A : 0);
            this.mti = 0;
        }
        y = this.mt[this.mti++];
        // Tempering
        y ^= (y >>> 11);
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        y ^= (y >>> 18);
        return y;
    }

    /** [0,0x7fffffff]（C++ (int)(genrand_int32()>>1)） */
    public int nextInt() {
        return (int) (genrandInt32() >>> 1);
    }

    /** C++ 无符号 % n（有偏取模，照抄非 Java 拒绝采样） */
    public int nextInt(final int n) {
        return (int) (Integer.toUnsignedLong(genrandInt32()) % n);
    }

    /** C++ (float)genrand_real2()——先 double（无符号 / 2^32）再截 float */
    public float nextFloat() {
        return (float) (Integer.toUnsignedLong(genrandInt32()) * (1.0 / 4294967296.0));
    }

    /** genrand_real2：[0,1)（无符号 / 2^32） */
    public double nextDouble() {
        return Integer.toUnsignedLong(genrandInt32()) * (1.0 / 4294967296.0);
    }

    /** PE 原样：0x8000000（2^27）掩码 */
    public boolean nextBoolean() {
        return (genrandInt32() & 0x8000000) > 0;
    }

    /** PE 原样：注释 long long 实际返回 int（31 位） */
    public int nextLong() {
        return (int) (genrandInt32() >>> 1);
    }

    public int nextLong(final int n) {
        return (int) (Integer.toUnsignedLong(genrandInt32()) % n);
    }

    /** Box-Muller 极坐标法（float 版，照 PE 源码） */
    public float nextGaussian() {
        if (this.haveNextNextGaussian) {
            this.haveNextNextGaussian = false;
            return this.nextNextGaussian;
        }
        float v1;
        float v2;
        float s;
        do {
            v1 = 2.0f * nextFloat() - 1.0f;
            v2 = 2.0f * nextFloat() - 1.0f;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1.0f || s == 0.0f);
        final float multiplier = (float) Math.sqrt(-2.0 * Math.log(s) / s);
        this.nextNextGaussian = v2 * multiplier;
        this.haveNextNextGaussian = true;
        return v1 * multiplier;
    }
}
