package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

import com.mojang.serialization.MapCodec;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

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
        // 🔧 enableSkyGrid（大佬裁定）：0.6.1 原版的天空网格——启用 = 原版直接过（不检查 Inf，float 噪声
        // 远坐标溢出的天空网格照原样）；关闭 = 禁用（y≥128 虚空 + Inf 消除——角点无 Inf，26.2 的
        // interpolated 插值（double）自然无 Inf/NaN，覆盖角点/cell/过渡段全程）。
        final boolean skyGrid = isSkyGridEnabled();
        // 🔧 大佬裁定（2026-10-31 实测）：0.6.1 原版有效高度域 0~127，域外全部虚空。
        // y≥128：源码 slide 公式在 yy>16 外推时 (1-slide) 变负，val 项（bb/cc ±64 尺度）反号压过
        // -10 项 → 实心石墙（原版 128 上限下不可见）；y<0：yOffs 负×4 外推为深岩（同样原版不存在）。
        // 域外早退判负（虚空），顺带省计算。
        if (blockY < 0) {
            return -1.0f; // y<0 恒虚空（0.6.1 无此域，与天空网格无关）
        }
        if (!skyGrid && blockY >= 128) {
            return -1.0f; // 关闭：y≥128 虚空（禁天空网格）；启用：原版 slide 外推照算（石墙/Inf 网格照原样）
        }
        // 🔧 偏移缩放（WorldReposition 红线）：采样坐标 = reposition(玩家块坐标)——恒等时无变化
        final float sampleX = (float) net.minecraft.world.level.levelgen.WorldReposition.reposition(blockX, net.minecraft.core.Direction.Axis.X);
        final float sampleY = (float) net.minecraft.world.level.levelgen.WorldReposition.reposition(blockY, net.minecraft.core.Direction.Axis.Y);
        final float sampleZ = (float) net.minecraft.world.level.levelgen.WorldReposition.reposition(blockZ, net.minecraft.core.Direction.Axis.Z);
        final float s = SCALE_S;
        final float hs = SCALE_HS;

        // 温度采样点（网格语义：xc×3+1——玩家域网格点 → reposition → 采样坐标）
        final int txGrid = BiomeSource0601.temperatureCoord(blockX);
        final int tzGrid = BiomeSource0601.temperatureCoord(blockZ);
        final float txSample = (float) net.minecraft.world.level.levelgen.WorldReposition.reposition(txGrid, net.minecraft.core.Direction.Axis.X);
        final float tzSample = (float) net.minecraft.world.level.levelgen.WorldReposition.reposition(tzGrid, net.minecraft.core.Direction.Axis.Z);
        final float temperature = this.system.biomeSource().temperatureAt(txSample, tzSample);
        float downfall = this.system.biomeSource().downfallAt(txSample, tzSample) * temperature;
        float dd = 1 - downfall;
        dd = dd * dd;
        dd = dd * dd;
        dd = 1 - dd;

        // 噪声域：采样坐标/4（x/z）、/8（y）——getRegion 采样点单点等价（reposition 后）
        final float nx = sampleX / 4.0f;
        final float ny = sampleY / 8.0f;
        final float nz = sampleZ / 4.0f;

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

        // 🔧 enableSkyGrid 关闭：Float 正负 Inf 消除（远坐标 fade x³ 溢出的天空网格）——角点无 Inf，
        // 26.2 interpolated 插值（double）覆盖角点/cell/过渡段自然无 Inf/NaN
        if (!skyGrid && Float.isInfinite(val)) {
            return -1.0f;
        }
        return val;
    }

    /** enableSkyGrid 开关（WMS"启用天空网格"；config 缺省时按 0.6.1 原版=启用） */
    private static boolean isSkyGridEnabled() {
        final WorldMainSettingScreen.FarLandsConfigData cfg = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return cfg == null || cfg.enableSkyGrid;
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
