package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

import com.mojang.serialization.MapCodec;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 地形密度（getHeights 公式逐字单点化）。
 *
 * <p>坐标语义（从 PE 源码推导实锤）：getHeights 的噪声域 n = 块坐标/4（x/z）、块坐标/8（y）——
 * 采样点 (xc,yc,zc) 的噪声输入 = (chunkX×4+xc)×s = (块坐标/4)×s。单点采样 =
 * PerlinNoise0601.getValue(块坐标/4 × xs, 块坐标/8 × ys, 块坐标/4 × zs)，与 getRegion 的
 * 采样点值逐位等价（add 的 (_x+xx)×xs 公式对齐）。
 *
 * <p>外推：26.2 的插值角点 y = -64+8k，0.6.1 采样层 yy = by/8.0（连续）——y<0 外推为深岩
 * （yOffs 负值×4 放大），y>104 顶部滑向 -10（原版 slide 外推），y>128 恒 -10（空气）。
 * 0.6.1 的 chunk 独立温度采样已连续化（见 BiomeSource0601——去 chunk 缝）。
 *
 * <p>由管线的 interpolated 包装驱动：26.2 的 NoiseInterpolator 按 cell（4×8×4）角点采样
 * 本密度 + 方块三线性插值——与 0.6.1 的 prepareHeights 插值数学等价，且白嫖 26.2 的
 * 插值/多线程/分带基建。
 */
public final class TerrainDensity0601 implements DensityFunction.SimpleFunction {

    /** PE getHeights 的坐标缩放（Beta 1.7.3 同源常量） */
    private static final float SCALE_S = 1 * 684.412f;
    private static final float SCALE_HS = 1 * 684.412f;
    /** 0.6.1 采样层数（128 高 / 8 步长 + 1） */
    private static final float Y_SIZE = 17.0f;

    private final Bedrock0601NoiseSystem system;

    public TerrainDensity0601(final Bedrock0601NoiseSystem system) {
        this.system = system;
    }

    @Override
    public double compute(final DensityFunction.FunctionContext ctx) {
        return this.heightValue(ctx.blockX(), ctx.blockY(), ctx.blockZ());
    }

    /** getHeights 公式单点化（源码逐字；bb/cc/pnr 用 8 堆单点采样，温度/湿度连续单点） */
    public float heightValue(final int blockX, final int blockY, final int blockZ) {
        final float s = SCALE_S;
        final float hs = SCALE_HS;

        // 温度采样点（连续化：采样格 k = 块坐标/4 → 温度块坐标 = 3k+1）
        final int tx = BiomeSource0601.temperatureCoord(blockX);
        final int tz = BiomeSource0601.temperatureCoord(blockZ);
        final float temperature = this.system.biomeSource().temperatureAt(tx, tz);
        float downfall = this.system.biomeSource().downfallAt(tx, tz) * temperature;
        float dd = 1 - downfall;
        dd = dd * dd;
        dd = dd * dd;
        dd = 1 - dd;

        // 噪声域：x/z = 块坐标/4、y = 块坐标/8（getRegion 采样点单点等价——2D/3D 噪声统一用此域）
        final float nx = blockX / 4.0f;
        final float ny = blockY / 8.0f;
        final float nz = blockZ / 4.0f;

        // scaleNoise/depthNoise：2D point 版（走 add ySize==1 分支——与 getRegion 采样点逐位等价）
        float scale = (this.system.scaleNoise().getRegionPoint2D(nx, nz, 1.121f, 1.121f) + 256.0f) / 512;
        scale *= dd;
        if (scale > 1) scale = 1;

        float depth = this.system.depthNoise().getRegionPoint2D(nx, nz, 200.0f, 200.0f) / 8000.0f;
        if (depth < 0) depth = -depth * 0.3f;
        depth = depth * 3.0f - 2.0f;

        if (depth < 0) {
            depth = depth / 2;
            if (depth < -1) depth = -1;
            depth = depth / 1.4f;
            depth /= 2;
            scale = 0;
        } else {
            if (depth > 1) depth = 1;
            depth = depth / 8;
        }

        if (scale < 0) scale = 0;
        scale = scale + 0.5f;
        depth = depth * Y_SIZE / 16;

        final float yCenter = Y_SIZE / 2.0f + depth * 4;

        final float pnr = this.system.perlinNoise1().getRegionPoint3D(nx, ny, nz, s / 80.0f, hs / 160.0f, s / 80.0f);
        final float ar = this.system.lperlinNoise1().getRegionPoint3D(nx, ny, nz, s, hs, s);
        final float br = this.system.lperlinNoise2().getRegionPoint3D(nx, ny, nz, s, hs, s);

        final float yy = ny;
        float val = 0;

        float yOffs = (yy - yCenter) * 12 / scale;
        if (yOffs < 0) yOffs *= 4;

        final float bb = ar / 512;
        final float cc = br / 512;

        final float v = (pnr / 10 + 1) / 2;
        if (v < 0) {
            val = bb;
        } else if (v > 1) {
            val = cc;
        } else {
            val = bb + (cc - bb) * v;
        }
        val -= yOffs;

        if (yy > Y_SIZE - 4) {
            final float slide = (yy - (Y_SIZE - 4)) / (4 - 1.0f);
            val = val * (1 - slide) + -10 * slide;
        }

        return val;
    }

    // ===== 值域：±∞（yOffs 项随 y 外推无界；避免上层值域短路优化误判）=====

    @Override
    public double minValue() {
        return Double.NEGATIVE_INFINITY;
    }

    @Override
    public double maxValue() {
        return Double.POSITIVE_INFINITY;
    }

    /** 代码组装树不被 JSON 引用——unit 兜底（TerrainPipeline 设计原则：MapCodec.unit 思路） */
    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return KeyDispatchDataCodec.of(MapCodec.unit(() -> this));
    }
}
