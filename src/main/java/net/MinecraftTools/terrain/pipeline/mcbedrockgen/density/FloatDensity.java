package net.MinecraftTools.terrain.pipeline.mcbedrockgen.density;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.WorldReposition;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * 🔧 MCRe Bedrock-Edition 管线：全 Float 密度函数节点家族（大佬 2026-11-13 派活）。
 *
 * <p><b>背景</b>：MCBedrockGenPipeline 此前密度组合层走主线 JSON 双精度树（仅噪声实例换
 * {@code mcbedrockgen.noise} 副本——副本内部本就是 float 全链，外层 double 只是接口）。
 * 本家族把<b>组合层</b>也换成 float：add/mul/clamp/spline 插值/滑梯/糖粒面全部 float 运算，
 * 模拟基岩单精度密度组合语义。
 *
 * <p><b>语义对齐（与主线 DensityFunctions 逐节点镜像）</b>：
 * <ul>
 *   <li>噪声采样：坐标先走 {@link WorldReposition#reposition}（偏移缩放红线），再乘 xzScale/yScale
 *       （double 域乘法后由副本 NormalNoise.getValue 内部量化 float——与现管线逐位同输入）。</li>
 *   <li>Spline 层：26.2 主线 CubicSpline 本就 float 原生（locations/values 均为 float[]），
 *       直接复用 vanilla {@code DensityFunctions.Spline} 节点 + TerrainProvider 数据（零移植）。</li>
 *   <li>interpolated：自管 float 三线性（vanilla NoiseInterpolator 是 double lerp3）——
 *       cell 网格 4×8×4 与 vanilla 对齐，角点 memo 按 chunk 实例有界。</li>
 *   <li>BlendAlpha/BlendOffset/BlendDensity：按 Blender.empty() 语义（1.0 / 0.0 / 透传）——
 *       MCRe 管线全新生成场景恒成立；旧世界升级混合不支持（回落原版路径，见管线注释）。</li>
 *   <li>WindowedDepthGradient：窗口取 vanilla 主世界域 -64..320（与原版 yClampedGradient 等价）；
 *       超高世界窗口感知限制器在 float 链退化为原版梯度（已知限制，见管线注释）。</li>
 * </ul>
 *
 * <p><b>线程/生命周期</b>：缓存型节点（插值/flatCache/cache2d/cacheOnce）持有<b>每实例</b>状态，
 * 树必须按 chunk 组装（{@code OverworldFloatDensity} 每次调用新建），禁跨 chunk 共享。
 * 同一 chunk 的填充为单线程，普通 HashMap 即可。
 */
public final class FloatDensity {

    private FloatDensity() {
    }

    /** 全 Float 密度函数：块坐标 (x, y, z) → float 值。 */
    public interface F {

        float compute(int x, int y, int z);
    }

    // ──────── 叶子 ────────

    public record ConstantF(float value) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return this.value;
        }
    }

    /** Y 恒等（对应主线 Y = yClampedGradient(MIN,MAX,MIN,MAX)，含 Y 轴偏移开关语义）。 */
    public record YF(long minY, long maxY) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final double yy = WorldReposition.isYClampedGradientOffsetEnabled()
                    ? WorldReposition.reposition((long) y, Direction.Axis.Y)
                    : y;
            // clampedMap(yy, minY, maxY, minY, maxY) = clamp 后恒等（超大域 float 会丢整数精度，
            // 但该节点只喂 rangeChoice 的区间比较，主线 double 版同域同语义）
            return (float) yy;
        }
    }

    /**
     * 噪声节点：坐标走 WorldReposition（偏移缩放红线）→ ×scale（double，与主线同序）→
     * 副本 NormalNoise.getValue（内部量化 float、wrap/BedrockMode 魔改随副本）→ (float) 输出。
     */
    public record NoiseF(NormalNoise noise, double xzScale, double yScale) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final double rx = WorldReposition.reposition(x, Direction.Axis.X);
            final double ry = WorldReposition.reposition(y, Direction.Axis.Y);
            final double rz = WorldReposition.reposition(z, Direction.Axis.Z);
            return (float) this.noise.getValue(rx * this.xzScale, ry * this.yScale, rz * this.xzScale);
        }
    }

    /** mappedNoise：noise → mapFromUnitTo([-1,1] → [min,max])，float 域。 */
    public static F mappedNoiseF(final NormalNoise noise, final double xzScale, final double yScale, final float min, final float max) {
        final float d = (max - min) / 2.0f;
        final float offset = min + d;
        final F source = new NoiseF(noise, xzScale, yScale);
        return new AddF(new MulF(new ConstantF(d), source), new ConstantF(offset));
    }

    // ──────── Shift 家族（ShiftNoise：×0.25 采样 ×4 还原；副本噪声内部 float 化） ────────

    /** shift_a：X/Z 施加 WorldReposition、Y=0；内部 getValue(x/4,y/4,z/4)×4。 */
    public record ShiftAF(NormalNoise offsetNoise) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final double rx = WorldReposition.reposition(x, Direction.Axis.X);
            final double rz = WorldReposition.reposition(z, Direction.Axis.Z);
            return (float) (this.offsetNoise.getValue(rx * 0.25, 0.0, rz * 0.25) * 4.0);
        }
    }

    /** shift_b：参数顺序 (z, x, y=0)（主线 ShiftB 同款怪味照抄）。 */
    public record ShiftBF(NormalNoise offsetNoise) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final double rz = WorldReposition.reposition(z, Direction.Axis.Z);
            final double rx = WorldReposition.reposition(x, Direction.Axis.X);
            return (float) (this.offsetNoise.getValue(rz * 0.25, rx * 0.25, 0.0) * 4.0);
        }
    }

    /** shifted_noise（2D）：reposition × xzScale + shift（disableOffsetNoise 开关随主线）。 */
    public record ShiftedNoise2dF(F shiftX, F shiftZ, double xzScale, NormalNoise noise) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final boolean disableOffset = net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen.FarLandsConfigData.activeConfig != null
                    && net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen.FarLandsConfigData.activeConfig.disableOffsetNoise;
            final double sx = disableOffset ? 0.0 : this.shiftX.compute(x, y, z);
            final double sz = disableOffset ? 0.0 : this.shiftZ.compute(x, y, z);
            final double rx = WorldReposition.reposition(x, Direction.Axis.X);
            final double rz = WorldReposition.reposition(z, Direction.Axis.Z);
            return (float) this.noise.getValue(rx * this.xzScale + sx, 0.0, rz * this.xzScale + sz);
        }
    }

    // ──────── 二元/多元组合 ────────

    public record AddF(F a, F b) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return this.a.compute(x, y, z) + this.b.compute(x, y, z);
        }
    }

    public record MulF(F a, F b) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return this.a.compute(x, y, z) * this.b.compute(x, y, z);
        }
    }

    public record MinF(F a, F b) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return Math.min(this.a.compute(x, y, z), this.b.compute(x, y, z));
        }
    }

    public record MaxF(F a, F b) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return Math.max(this.a.compute(x, y, z), this.b.compute(x, y, z));
        }
    }

    /** lerp(p, a, b)：p==0/1 快路径与主线 double lerp 逐位一致（1.0 时 (b-a) 精确还原）。 */
    public record LerpF(F p, F a, F b) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float pf = this.p.compute(x, y, z);
            if (pf == 0.0f) {
                return this.a.compute(x, y, z);
            }
            if (pf == 1.0f) {
                return this.b.compute(x, y, z);
            }
            return this.a.compute(x, y, z) + pf * (this.b.compute(x, y, z) - this.a.compute(x, y, z));
        }
    }

    // ──────── 一元 ────────

    public record AbsF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return Math.abs(this.source.compute(x, y, z));
        }
    }

    public record SquareF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.source.compute(x, y, z);
            return v * v;
        }
    }

    public record CubeF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.source.compute(x, y, z);
            return v * v * v;
        }
    }

    public record HalfNegativeF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.source.compute(x, y, z);
            return v < 0.0f ? v * 0.5f : v;
        }
    }

    public record QuarterNegativeF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.source.compute(x, y, z);
            return v < 0.0f ? v * 0.25f : v;
        }
    }

    /**
     * invert = 1 / v（主线 {@code Mapped.Type.INVERT} 同语义）。
     * <p>教训：初版误写成 {@code 1 - v} —— 主线是<b>倒数</b>；PS 链的 upperBound 依赖 invert，
     * 写错会让初步地表高度整体错位（地表异常条纹的根因之一）。Diag 实测锤抓出。
     */
    public record InvertF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return 1.0f / this.source.compute(x, y, z);
        }
    }

    /** squeeze：d = clamp(v, -1, 1) → d/2 - d³/24（输出域 [-0.4583, 0.4583]）。 */
    public record SqueezeF(F source) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.source.compute(x, y, z);
            final float d = Math.clamp(v, -1.0f, 1.0f);
            return d / 2.0f - d * d * d / 24.0f;
        }
    }

    public record ClampF(F source, float min, float max) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            return Math.clamp(this.source.compute(x, y, z), this.min, this.max);
        }
    }

    public record RangeChoiceF(F input, float minInclusive, float maxExclusive, F whenInRange, F whenOutOfRange) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float v = this.input.compute(x, y, z);
            return v >= this.minInclusive && v < this.maxExclusive
                    ? this.whenInRange.compute(x, y, z)
                    : this.whenOutOfRange.compute(x, y, z);
        }
    }

    // ──────── 梯度族 ────────

    /**
     * y_clamped_gradient：Y 轴坐标受 {@code isYClampedGradientOffsetEnabled} 开关（主线同款），
     * 梯度映射全 float。
     */
    public record YClampedGradientF(int fromY, int toY, float fromValue, float toValue) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float yy = WorldReposition.isYClampedGradientOffsetEnabled()
                    ? (float) WorldReposition.reposition((long) y, Direction.Axis.Y)
                    : y;
            final float fy = (float) this.fromY;
            final float ty = (float) this.toY;
            float t = (yy - fy) / (ty - fy);
            t = Math.clamp(t, 0.0f, 1.0f);
            return this.fromValue + t * (this.toValue - this.fromValue);
        }
    }

    /**
     * MCRe 窗口感知 depth 限制器的 float 版（offsetToDepth 的梯度件）。
     * <p>窗口取 vanilla 主世界域 [-64, 320)（= 原版 yClampedGradient(-64,320,1.5,-1.5)）：
     * 窗口顶上方输出 {@code -1.5 - offset}、窗口底下方输出 {@code +1.5 - offset}
     * （外层 add(offset) 抵消 → depth 恒 ∓1.5 削峰补枯）；窗口内输出纯梯度（offset 由外层 add 参与 normal 语义）。
     */
    public record WindowedDepthGradientF(F offset) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final float yy = WorldReposition.isYClampedGradientOffsetEnabled()
                    ? (float) WorldReposition.reposition((long) y, Direction.Axis.Y)
                    : y;
            if (yy >= 320.0f) {
                return -1.5f - this.offset.compute(x, y, z);
            }
            if (yy < -64.0f) {
                return 1.5f - this.offset.compute(x, y, z);
            }
            float t = (yy + 64.0f) / 384.0f;
            t = Math.clamp(t, 0.0f, 1.0f);
            return 1.5f + t * (-3.0f);
        }
    }

    // ──────── 缓存族（每 chunk 实例状态——树禁跨 chunk 共享的原因） ────────

    /**
     * flat_cache：按 <b>quart</b> 列（4 块）缓存——主线 NoiseChunk.FlatCache 同粒度
     * （offset/factor/气候等 2D 函数按 quart 采样）。
     *
     * <p>🔧 关键语义（Diag/源码实锤）：主线 FlatCache 在 <b>chunk 构造时于 quart 边界点</b>预计算
     * （{@code SinglePointContext(QuartPos.toBlock(firstNoiseX + i), 0, QuartPos.toBlock(firstNoiseZ + j))}，
     * Y 固定 0），查询时按 quart 索引取值。初版本实现"首次访问点求值"——与边界点最多差 3 格
     * → 被缓存函数不够平滑时出现<b>每 4 格错位 3 格</b>的台阶（实测：地表竖直条纹，3 格红 + 1 格黄）。
     * 现改为<b>一律在 quart 边界坐标求值</b>，与主线逐位对齐。
     */
    public static final class FlatCacheF implements F {

        private final F source;
        private final Map<Long, Float> cache = new HashMap<>();

        public FlatCacheF(final F source) {
            this.source = source;
        }

        @Override
        public float compute(final int x, final int y, final int z) {
            final int quartX = x >> 2;
            final int quartZ = z >> 2;
            final long key = ((long) quartX << 32) | (quartZ & 0xFFFFFFFFL);
            final Float cached = this.cache.get(key);
            if (cached != null) {
                return cached;
            }
            // quart 边界块坐标（与主线 QuartPos.toBlock 同语义）+ Y=0（主线 FlatCache 同款）
            final float value = this.source.compute(quartX << 2, 0, quartZ << 2);
            this.cache.put(key, value);
            return value;
        }
    }

    /** cache_2d：按块列 (x, z) 缓存（y 无关函数）。 */
    public static final class Cache2dF implements F {

        private final F source;
        private final Map<Long, Float> cache = new HashMap<>();

        public Cache2dF(final F source) {
            this.source = source;
        }

        @Override
        public float compute(final int x, final int y, final int z) {
            final long key = ((long) x << 32) | (z & 0xFFFFFFFFL);
            final Float cached = this.cache.get(key);
            if (cached != null) {
                return cached;
            }
            final float value = this.source.compute(x, y, z);
            this.cache.put(key, value);
            return value;
        }
    }

    /** cache_once：单槽缓存（主线同款——依赖同位置连续两次求值的访问模式）。 */
    public static final class CacheOnceF implements F {

        private final F source;
        private long lastKey = Long.MIN_VALUE;
        private float lastValue;

        public CacheOnceF(final F source) {
            this.source = source;
        }

        @Override
        public float compute(final int x, final int y, final int z) {
            final long key = mixKey(x, y, z);
            if (key == this.lastKey) {
                return this.lastValue;
            }
            final float value = this.source.compute(x, y, z);
            this.lastKey = key;
            this.lastValue = value;
            return value;
        }
    }

    /**
     * interpolated：自管 float 三线性（cell 网格 4×8×4 与 vanilla NoiseInterpolator 对齐）。
     * 角点 memo 按 chunk 实例有界（4×4×69 cell × float[8] ≈ 40KB/节点）。
     */
    public static final class InterpolatedF implements F {

        private final F source;
        private final Map<Long, float[]> cornerCache = new HashMap<>();

        public InterpolatedF(final F source) {
            this.source = source;
        }

        @Override
        public float compute(final int x, final int y, final int z) {
            final int cellX = Math.floorDiv(x, 4);
            final int cellY = Math.floorDiv(y, 8);
            final int cellZ = Math.floorDiv(z, 4);
            final long key = mixKey(cellX, cellY, cellZ);
            float[] corners = this.cornerCache.get(key);
            if (corners == null) {
                corners = new float[8];
                final int baseX = cellX * 4;
                final int baseY = cellY * 8;
                final int baseZ = cellZ * 4;
                // 角点序（与主线 NoiseInterpolator 的 nXYZ 命名一致）：bit0=x、bit1=y、bit2=z
                // 🔧 修正：Z 角点偏移原写成 (i & 4) << 2 = 16（应为 cellWidth=4）→ Z 方向插值跨越 4 个 cell
                //    的错误角点 → 地表竖直条纹（3 格 cell 内部 vs 角点反向）+ caves 链 Z 向错位 → 洞穴爆炸。
                for (int i = 0; i < 8; i++) {
                    final int dx = (i & 1) * 4;
                    final int dy = ((i >> 1) & 1) * 8;
                    final int dz = ((i >> 2) & 1) * 4;
                    corners[i] = this.source.compute(baseX + dx, baseY + dy, baseZ + dz);
                }
                this.cornerCache.put(key, corners);
            }
            final float fx = (x - cellX * 4) / 4.0f;
            final float fy = (y - cellY * 8) / 8.0f;
            final float fz = (z - cellZ * 4) / 4.0f;
            return lerp3(fx, fy, fz, corners);
        }

        /** float 版 Mth.lerp3（参数序同 vanilla：n000..n111，bit0=x、bit1=y、bit2=z）。 */
        private static float lerp3(final float fx, final float fy, final float fz, final float[] c) {
            final float v00 = c[0] + fx * (c[1] - c[0]);
            final float v10 = c[2] + fx * (c[3] - c[2]);
            final float v01 = c[4] + fx * (c[5] - c[4]);
            final float v11 = c[6] + fx * (c[7] - c[6]);
            final float v0 = v00 + fy * (v10 - v00);
            final float v1 = v01 + fy * (v11 - v01);
            return v0 + fz * (v1 - v0);
        }
    }

    /**
     * find_top_surface（PS 用）：自上而下按 cellHeight 扫描首个 density &gt; 0 的层。
     */
    public record FindTopSurfaceF(F density, F upperBound, int lowerBound, int cellHeight) implements F {

        @Override
        public float compute(final int x, final int y, final int z) {
            final int topY = (int) Math.floor(this.upperBound.compute(x, y, z) / (double) this.cellHeight) * this.cellHeight;
            if (topY <= this.lowerBound) {
                return this.lowerBound;
            }
            for (int blockY = topY; blockY >= this.lowerBound; blockY -= this.cellHeight) {
                if (this.density.compute(x, blockY, z) > 0.0f) {
                    return blockY;
                }
            }
            return this.lowerBound;
        }
    }

    // ──────── 辅助 ────────

    /**
     * 三元组 → 64 位 key（结构无重叠，超大世界不碰撞）。
     * <p>教训：早期版本用 {@code (cellX << 42) ^ (cellY << 21) ^ cellZ} —— 位域在超大世界
     * （cellY/cellZ > 2^21、cellX > 2^22）必然溢出互撞 → 缓存角点串值 → 地表条纹。
     */
    private static long mixKey(final int x, final int y, final int z) {
        long h = (long) x * 0x9E3779B97F4A7C15L;
        h ^= (long) y * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h ^= (h >>> 29);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 32);
        return h;
    }

    /** peaksAndValleys（ridge → ridges_folded，主线同公式 float 化）。 */
    public static F peaksAndValleysF(final F weirdness) {
        return new MulF(
            new AddF(
                new AbsF(new AddF(new AbsF(weirdness), new ConstantF(-0.6666666666666666f))),
                new ConstantF(-0.3333333333333333f)
            ),
            new ConstantF(-3.0f)
        );
    }
}
