package net.MinecraftTools.vectorization;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;

/**
 * 🔧 MCRe 向量化专用库：JDK Vector API（{@code jdk.incubator.vector}）后端。
 *
 * <p>⚠️ <b>本类是整个工程里唯一 import Vector API 的地方</b> —— 必须通过
 * {@link VectorMath#installVectorBackend()} <b>反射</b>加载，否则在未加
 * {@code --add-modules jdk.incubator.vector} 的 JVM 上类加载即失败（游戏崩溃）。
 *
 * <p><b>实现要点</b>：
 * <ul>
 *   <li>SoA 批量：按 species 宽度分块处理，尾部走标量 → 任意 count 都能用</li>
 *   <li>全部分支型映射改写为无分支代数式（halfNegative = v − min(v,0)×0.5）→ 不需要 mask</li>
 *   <li>与标量后端<b>逐位同式</b>（同运算顺序）→ 两条后端结果可逐点对照</li>
 * </ul>
 */
final class VectorMathJdk implements VectorMath.Ops {

    /**
     * ⚠️ JDK 25 没有公开的 FloatSpecies 类型：{@code VectorSpecies<Float>.fromArray} 返回
     * {@code Vector<Float>}（无算子），而算子在 {@code FloatVector} 上 → 这里统一经
     * {@link #load}/{@link #bcast} 取回 FloatVector（float species 的实例必为 FloatVector，运行时安全）。
     */
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    private static final float ZERO = 0.0f;

    private VectorMathJdk() {
    }

    /** 反射安装入口（必须 public + setAccessible：类本身是包私有的）。 */
    public static VectorMath.Ops create() {
        return new VectorMathJdk();
    }

    @Override
    public String name() {
        return "jdk-vector(" + SPECIES.length() + " lanes, " + SPECIES.toString().length() + ")";
    }



    /** 取回带算子的 FloatVector（fromArray 的静态返回类型是 Vector<Float>）。 */
    private static FloatVector load(final float[] a, final int i) {
        return (FloatVector) SPECIES.fromArray(a, i);
    }

    /**
     * 广播标量到向量。
     * <p>⚠️ JDK 25 的 {@code VectorSpecies.broadcast} 只收整型（long 重载会把数值截断，<b>不是</b> float 位模式）
     * → 用预填充缓冲 + load 实现；缓冲按线程复用，避免每次分配。
     */
    private static final ThreadLocal<float[]> BROADCAST_BUFFER = ThreadLocal.withInitial(() -> new float[SPECIES.length()]);

    private static FloatVector bcast(final float value) {
        final float[] buffer = BROADCAST_BUFFER.get();
        java.util.Arrays.fill(buffer, value);
        return load(buffer, 0);
    }

    /** 写回数组（intoArray 是 Vector 的实例方法，不在 species 上）。 */
    private static void store(final FloatVector v, final float[] out, final int i) {
        v.intoArray(out, i);
    }

    @Override
    public void lerp(final float[] deltas, final float[] starts, final float[] ends, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector d = load(deltas, i);
            final FloatVector s = load(starts, i);
            final FloatVector e = load(ends, i);
            store(s.add(d.mul(e.sub(s))), out, i);
        }

        for (; i < count; i++) {
            out[i] = starts[i] + deltas[i] * (ends[i] - starts[i]);
        }
    }

    @Override
    public void lerp3(
        final float[] fx, final float[] fy, final float[] fz,
        final float[] n000, final float[] n100, final float[] n010, final float[] n110,
        final float[] n001, final float[] n101, final float[] n011, final float[] n111,
        final float[] out, final int count
    ) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector fxv = load(fx, i);
            final FloatVector fyv = load(fy, i);
            final FloatVector fzv = load(fz, i);
            final FloatVector a000 = load(n000, i);
            final FloatVector a100 = load(n100, i);
            final FloatVector a010 = load(n010, i);
            final FloatVector a110 = load(n110, i);
            final FloatVector a001 = load(n001, i);
            final FloatVector a101 = load(n101, i);
            final FloatVector a011 = load(n011, i);
            final FloatVector a111 = load(n111, i);
            final FloatVector v00 = a000.add(fxv.mul(a100.sub(a000)));
            final FloatVector v10 = a010.add(fxv.mul(a110.sub(a010)));
            final FloatVector v01 = a001.add(fxv.mul(a101.sub(a001)));
            final FloatVector v11 = a011.add(fxv.mul(a111.sub(a011)));
            final FloatVector v0 = v00.add(fyv.mul(v10.sub(v00)));
            final FloatVector v1 = v01.add(fyv.mul(v11.sub(v01)));
            store(v0.add(fzv.mul(v1.sub(v0))), out, i);
        }

        for (; i < count; i++) {
            final float v00 = n000[i] + fx[i] * (n100[i] - n000[i]);
            final float v10 = n010[i] + fx[i] * (n110[i] - n010[i]);
            final float v01 = n001[i] + fx[i] * (n101[i] - n001[i]);
            final float v11 = n011[i] + fx[i] * (n111[i] - n011[i]);
            final float v0 = v00 + fy[i] * (v10 - v00);
            final float v1 = v01 + fy[i] * (v11 - v01);
            out[i] = v0 + fz[i] * (v1 - v0);
        }
    }

    @Override
    public void clamp(final float[] values, final float min, final float max, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            final FloatVector lo = bcast(min);
            final FloatVector hi = bcast(max);
            store(v.max(lo).min(hi), out, i);
        }

        for (; i < count; i++) {
            final float v = values[i];
            out[i] = v < min ? min : Math.min(v, max);
        }
    }

    @Override
    public void abs(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            store(load(values, i).abs(), out, i);
        }

        for (; i < count; i++) {
            out[i] = Math.abs(values[i]);
        }
    }

    @Override
    public void square(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            store(v.mul(v), out, i);
        }

        for (; i < count; i++) {
            final float v = values[i];
            out[i] = v * v;
        }
    }

    @Override
    public void cube(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            store(v.mul(v).mul(v), out, i);
        }

        for (; i < count; i++) {
            final float v = values[i];
            out[i] = v * v * v;
        }
    }

    @Override
    public void halfNegative(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            store(v.sub(v.min(bcast(ZERO)).mul(0.5f)), out, i);
        }

        for (; i < count; i++) {
            final float v = values[i];
            out[i] = v - Math.min(v, 0.0f) * 0.5f;
        }
    }

    @Override
    public void quarterNegative(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            store(v.sub(v.min(bcast(ZERO)).mul(0.75f)), out, i);
        }

        for (; i < count; i++) {
            final float v = values[i];
            out[i] = v - Math.min(v, 0.0f) * 0.75f;
        }
    }

    @Override
    public void squeeze(final float[] values, final float[] out, final int count) {
        final int lanes = SPECIES.length();
        int i = 0;

        for (; i + lanes <= count; i += lanes) {
            final FloatVector v = load(values, i);
            final FloatVector c = v.max(bcast(-1.0f)).min(bcast(1.0f));
            final FloatVector c3 = c.mul(c).mul(c);
            store(c.mul(0.5f).sub(c3.mul(1.0f / 24.0f)), out, i);
        }

        for (; i < count; i++) {
            final float c = Math.clamp(values[i], -1.0f, 1.0f);
            out[i] = c / 2.0f - c * c * c / 24.0f;
        }
    }

    /**
     * floorDiv / floorMod：JDK 25 的 Vector API <b>没有 floor 原语</b>（无 Vector.floor()），
     * 而这两项在我们的密度链里不是热点 → 直接走标量实现（语义与标量后端逐位一致）。
     */
    @Override
    public void floorDiv(final float[] values, final float divisor, final float[] out, final int count) {
        for (int i = 0; i < count; i++) {
            out[i] = (float) Math.floor(values[i] / divisor);
        }
    }

    @Override
    public void floorMod(final float[] values, final float divisor, final float[] out, final int count) {
        for (int i = 0; i < count; i++) {
            out[i] = values[i] - divisor * (float) Math.floor(values[i] / divisor);
        }
    }
}
