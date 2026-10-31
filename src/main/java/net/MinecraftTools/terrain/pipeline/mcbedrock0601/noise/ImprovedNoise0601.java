package net.MinecraftTools.terrain.pipeline.mcbedrock0601.noise;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.minecraft.util.Mth;

import net.MinecraftTools.terrain.pipeline.mcbedrock0601.Mt19937Random;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 ImprovedNoise 逐位复刻（synth/ImprovedNoise.cpp，float 全程）。
 *
 * <p>经典 Ken Perlin 内核（fade/grad/lerp 与 Java ImprovedNoise 同构），差异点照抄 PE 原样：
 * <ul>
 *   <li>偏移 xo/yo/zo = nextFloat()*256（Java 用 nextDouble）；</li>
 *   <li>{@code add()} 的 ySize==1（2D）路径 <b>grad2 与 grad 混用</b>（AA 用 grad2、BA/AA+1/BA+1 用
 *       grad(y=0)）——PE 真实算法原样（疑似开发期笔误，保真）；</li>
 *   <li>3D 路径带 yOld 列缓存优化（Y 不变时复用 8 角点哈希/插值中间值）；</li>
 *   <li>buffer 索引顺序 = x 外、z 中、y 内（{@code ((xx*zSize)+zz)*ySize+yy}），与
 *       RandomLevelSource.getHeights 的读取顺序一致。</li>
 * </ul>
 */
public final class ImprovedNoise0601 {

    private final int[] p = new int[512];
    public float xo;
    public float yo;
    public float zo;

    public ImprovedNoise0601(final Mt19937Random random) {
        this.init(random);
    }

    private void init(final Mt19937Random random) {
        this.xo = random.nextFloat() * 256.0f;
        this.yo = random.nextFloat() * 256.0f;
        this.zo = random.nextFloat() * 256.0f;
        for (int i = 0; i < 256; i++) {
            this.p[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            final int j = random.nextInt(256 - i) + i;
            final int tmp = this.p[i];
            this.p[i] = this.p[j];
            this.p[j] = tmp;
            this.p[i + 256] = this.p[i];
        }
    }

    public float noise(float _x, float _y, float _z) {
        final float x = wrap0601(_x) + this.xo;
        final float y = _y + this.yo;
        final float z = wrap0601(_z) + this.zo;

        int xf = (int) x;
        int yf = (int) y;
        int zf = (int) z;
        if (x < xf) xf--;
        if (y < yf) yf--;
        if (z < zf) zf--;

        final int X = xf & 255;
        final int Y = yf & 255;
        final int Z = zf & 255;

        final float rx = x - xf;
        final float ry = y - yf;
        final float rz = z - zf;

        final float u = rx * rx * rx * (rx * (rx * 6 - 15) + 10);
        final float v = ry * ry * ry * (ry * (ry * 6 - 15) + 10);
        final float w = rz * rz * rz * (rz * (rz * 6 - 15) + 10);

        final int A = this.p[X] + Y;
        final int AA = this.p[A] + Z;
        final int AB = this.p[A + 1] + Z;
        final int B = this.p[X + 1] + Y;
        final int BA = this.p[B] + Z;
        final int BB = this.p[B + 1] + Z;

        return lerp(w,
            lerp(v,
                lerp(u, grad(this.p[AA], rx, ry, rz), grad(this.p[BA], rx - 1, ry, rz)),
                lerp(u, grad(this.p[AB], rx, ry - 1, rz), grad(this.p[BB], rx - 1, ry - 1, rz))),
            lerp(v,
                lerp(u, grad(this.p[AA + 1], rx, ry, rz - 1), grad(this.p[BA + 1], rx - 1, ry, rz - 1)),
                lerp(u, grad(this.p[AB + 1], rx, ry - 1, rz - 1), grad(this.p[BB + 1], rx - 1, ry - 1, rz - 1))));
    }

    private static float lerp(final float t, final float a, final float b) {
        // 🔧 渐进式边境之地（大佬裁定）：progressiveFarlands 开启时 lerp 直接返回 a（插值退化——
        // 噪声的 fade 插值层全部取起点梯度，晶格值阶跃）。关闭时正常三线性。
        if (isProgressiveFarlands()) {
            return a;
        }
        return a + t * (b - a);
    }

    /** progressiveFarlands 开关（WMS"渐进式边境之地"） */
    private static boolean isProgressiveFarlands() {
        final WorldMainSettingScreen.FarLandsConfigData cfg = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return cfg != null && cfg.progressiveFarlands;
    }

    // ===== 坐标折叠（批6：照主线 wrap 结构 float 化——0.6.1 管线的边境之地位置控制）=====

    /** PE/Release 式折叠（照主线 computeReleaseValue：整数部分 mod 2^24 折回 + 小数保留） */
    public static double computeReleaseValue0601(final double x) {
        final long l = Mth.lfloor(x);
        double shifted = x - l;
        final long wrapped = l % 16777216L;
        return shifted + wrapped;
    }

    private static boolean limitReturnValueMode0601(final WorldMainSettingScreen.FarLandsConfigData config) {
        return config.limitReturnValue;
    }

    /**
     * 🔧 批6：坐标折叠（照主线 wrap 结构 float 化）——precisionMode 分派：
     * 64bit / 1.18-exp-64bit → C=2^25 以 0 为中心对称折回；Release → mod 2^24 式；
     * 32bit / 1.18-exp-32bit / default → 恒等（0.6.1 原版：(int) 溢出自然发生）。
     * 末尾 limitReturnValue（对数域量级折叠，log10(0) 天然跳过）。
     * 作用点：ImprovedNoise0601 的噪声输入（scale 后、xo 前——照主线 wrap 位置）。
     */
    public static float wrap0601(final float x) {
        final WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        if (config == null) {
            return x;
        }
        final double limitNoiseValue = config.limitReturnValueValue;
        final String mode = config.precisionMode;
        float folded;
        switch (mode) {
            case "64bit":
            case "1.18-exp-64bit":
                folded = x - Mth.lfloor(x / 3.3554432E7F + 0.5F) * 3.3554432E7F;
                break;
            case "Release":
                folded = (float) computeReleaseValue0601(x);
                break;
            default:
                folded = x;
                break;
        }
        if (limitReturnValueMode0601(config)) {
            final double abs = Math.abs((double) folded);
            if (Math.log10(abs) > limitNoiseValue) {
                final double logAbs = Math.log10(abs);
                folded = (float) (Math.pow(10, logAbs - Math.floor(logAbs - limitNoiseValue)) * Math.signum(folded));
            }
        }
        return folded;
    }

    /** PE 专用 2D 梯度（u 恒 ∈ {0, x}——(1-((h&8)>>3)) 整型算术照抄） */
    private static float grad2(final int hash, final float x, final float z) {
        final int h = hash & 15;
        final float u = (1 - ((h & 8) >> 3)) * x;
        final float v = h < 4 ? 0.0f : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    private static float grad(final int hash, final float x, final float y, final float z) {
        final int h = hash & 15;
        final float u = h < 8 ? x : y;
        final float v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }

    public float getValue(final float x, final float y) {
        return this.noise(x, y, 0.0f);
    }

    public float getValue(final float x, final float y, final float z) {
        return this.noise(x, y, z);
    }

    /**
     * add() ySize==1 分支的单点等价（xx=0）——getRegion 2D 采样的单点版。
     * ⚠️ 与 {@link #noise} 数值不同（grad2/grad 混用 vs 纯 3D grad——PE 源码事实），必须对应用途。
     */
    public float point2D(final float _x, final float _z, final float xs, final float zs) {
        float x = wrap0601(_x * xs) + this.xo;
        int xf = (int) x;
        if (x < xf) xf--;
        final int X = xf & 255;
        x -= xf;
        final float u = x * x * x * (x * (x * 6 - 15) + 10);

        float z = wrap0601(_z * zs) + this.zo;
        int zf = (int) z;
        if (z < zf) zf--;
        final int Z = zf & 255;
        z -= zf;
        final float w = z * z * z * (z * (z * 6 - 15) + 10);

        final int A = this.p[X] + 0;
        final int AA = this.p[A] + Z;
        final int B = this.p[X + 1] + 0;
        final int BA = this.p[B] + Z;
        final float vv0 = lerp(u, grad2(this.p[AA], x, z), grad(this.p[BA], x - 1, 0.0f, z));
        final float vv2 = lerp(u, grad(this.p[AA + 1], x, 0.0f, z - 1), grad(this.p[BA + 1], x - 1, 0.0f, z - 1));
        return lerp(w, vv0, vv2);
    }

    /**
     * add() 3D 分支的单点等价（xx=yy=zz=0）——getRegion 3D 采样的单点版。
     * lerp 结构照 add（v0/v1 中间层），与 {@link #noise} 的浮点路径不同（PE 源码事实）。
     */
    public float point3D(final float _x, final float _y, final float _z, final float xs, final float ys, final float zs) {
        float x = wrap0601(_x * xs) + this.xo;
        int xf = (int) x;
        if (x < xf) xf--;
        final int X = xf & 255;
        x -= xf;
        final float u = x * x * x * (x * (x * 6 - 15) + 10);

        float z = wrap0601(_z * zs) + this.zo;
        int zf = (int) z;
        if (z < zf) zf--;
        final int Z = zf & 255;
        z -= zf;
        final float w = z * z * z * (z * (z * 6 - 15) + 10);

        float y = _y * ys + this.yo;
        int yf = (int) y;
        if (y < yf) yf--;
        final int Y = yf & 255;
        y -= yf;
        final float v = y * y * y * (y * (y * 6 - 15) + 10);

        final int A = this.p[X] + Y;
        final int AA = this.p[A] + Z;
        final int AB = this.p[A + 1] + Z;
        final int B = this.p[X + 1] + Y;
        final int BA = this.p[B] + Z;
        final int BB = this.p[B + 1] + Z;
        final float vv0 = lerp(u, grad(this.p[AA], x, y, z), grad(this.p[BA], x - 1, y, z));
        final float vv1 = lerp(u, grad(this.p[AB], x, y - 1, z), grad(this.p[BB], x - 1, y - 1, z));
        final float vv2 = lerp(u, grad(this.p[AA + 1], x, y, z - 1), grad(this.p[BA + 1], x - 1, y, z - 1));
        final float vv3 = lerp(u, grad(this.p[AB + 1], x, y - 1, z - 1), grad(this.p[BB + 1], x - 1, y - 1, z - 1));

        final float v0 = lerp(v, vv0, vv1);
        final float v1 = lerp(v, vv2, vv3);
        return lerp(w, v0, v1);
    }

    /**
     * 区域采样累加（octave 版本由 PerlinNoise0601 驱动：第 i 层频率 = scale×pow、振幅 = 1/pow）。
     * buffer 索引 = x 外、z 中、y 内。
     */
    public void add(final float[] buffer, float _x, float _y, float _z, final int xSize, final int ySize, final int zSize,
                    final float xs, final float ys, final float zs, final float pow, final float amp) {
        if (ySize == 1) {
            // ===== PE 2D 路径（grad2/grad 混用怪味照抄）=====
            int pp = 0;
            final float scale = 1.0f / pow * amp;
            for (int xx = 0; xx < xSize; xx++) {
                float x = wrap0601((_x + xx) * xs) + this.xo;
                int xf = (int) x;
                if (x < xf) xf--;
                final int X = xf & 255;
                x -= xf;
                final float u = x * x * x * (x * (x * 6 - 15) + 10);

                for (int zz = 0; zz < zSize; zz++) {
                    float z = wrap0601((_z + zz) * zs) + this.zo;
                    int zf = (int) z;
                    if (z < zf) zf--;
                    final int Z = zf & 255;
                    z -= zf;
                    final float w = z * z * z * (z * (z * 6 - 15) + 10);

                    final int A = this.p[X] + 0;
                    final int AA = this.p[A] + Z;
                    final int B = this.p[X + 1] + 0;
                    final int BA = this.p[B] + Z;
                    final float vv0 = lerp(u, grad2(this.p[AA], x, z), grad(this.p[BA], x - 1, 0.0f, z));
                    final float vv2 = lerp(u, grad(this.p[AA + 1], x, 0.0f, z - 1), grad(this.p[BA + 1], x - 1, 0.0f, z - 1));

                    final float val = lerp(w, vv0, vv2);
                    buffer[pp++] += val * scale;
                }
            }
            return;
        }

        // ===== 3D 路径（yOld 列缓存照抄）=====
        int pp = 0;
        final float scale = 1 / pow * amp;
        int yOld = -1;
        int A = 0;
        int AA = 0;
        int AB = 0;
        int B = 0;
        int BA = 0;
        int BB = 0;
        float vv0 = 0.0f;
        float vv1 = 0.0f;
        float vv2 = 0.0f;
        float vv3 = 0.0f;

        for (int xx = 0; xx < xSize; xx++) {
            float x = wrap0601((_x + xx) * xs) + this.xo;
            int xf = (int) x;
            if (x < xf) xf--;
            final int X = xf & 255;
            x -= xf;
            final float u = x * x * x * (x * (x * 6 - 15) + 10);

            for (int zz = 0; zz < zSize; zz++) {
                float z = wrap0601((_z + zz) * zs) + this.zo;
                int zf = (int) z;
                if (z < zf) zf--;
                final int Z = zf & 255;
                z -= zf;
                final float w = z * z * z * (z * (z * 6 - 15) + 10);

                for (int yy = 0; yy < ySize; yy++) {
                    float y = (_y + yy) * ys + this.yo;
                    int yf = (int) y;
                    if (y < yf) yf--;
                    final int Y = yf & 255;
                    y -= yf;
                    final float v = y * y * y * (y * (y * 6 - 15) + 10);

                    if (yy == 0 || Y != yOld) {
                        yOld = Y;
                        A = this.p[X] + Y;
                        AA = this.p[A] + Z;
                        AB = this.p[A + 1] + Z;
                        B = this.p[X + 1] + Y;
                        BA = this.p[B] + Z;
                        BB = this.p[B + 1] + Z;
                        vv0 = lerp(u, grad(this.p[AA], x, y, z), grad(this.p[BA], x - 1, y, z));
                        vv1 = lerp(u, grad(this.p[AB], x, y - 1, z), grad(this.p[BB], x - 1, y - 1, z));
                        vv2 = lerp(u, grad(this.p[AA + 1], x, y, z - 1), grad(this.p[BA + 1], x - 1, y, z - 1));
                        vv3 = lerp(u, grad(this.p[AB + 1], x, y - 1, z - 1), grad(this.p[BB + 1], x - 1, y - 1, z - 1));
                    }

                    final float v0 = lerp(v, vv0, vv1);
                    final float v1 = lerp(v, vv2, vv3);
                    final float val = lerp(w, v0, v1);
                    buffer[pp++] += val * scale;
                }
            }
        }
    }
}
