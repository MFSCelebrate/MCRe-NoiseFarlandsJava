package net.MinecraftTools.vectorization;

/**
 * 🔧 MCRe 向量化专用库（大佬 2026-11-15 拍板：放弃 GPU 加速，走 SIMD 向量化路线）。
 *
 * <p><b>为什么是"批量 SoA"而不是"逐点 SIMD"</b>：SIMD 的收益来自同构批量——
 * 我们把密度链/噪声采样改成「一次算 N 个坐标」（SoA：xs[]/ys[]/zs[] → out[]），
 * 同一批原语即可被 JIT 自动向量化（HotSpot SuperWord），也可被 JDK Vector API 显式 SIMD 化。
 *
 * <p><b>模块可用性</b>：{@code jdk.incubator.vector} 是孵化模块，未加
 * {@code --add-modules jdk.incubator.vector} 时 JVM 无法加载其类 → 本门面
 * <b>刻意不 import 任何 Vector API 类型</b>，JDK 实现类
 * ({@link VectorMathJdk}) 由 {@link #installVectorBackend()} <b>反射</b>加载：
 * 加载失败（模块缺失）时自动退回<b>标量 fallback</b>，游戏照常运行。
 *
 * <p><b>原语清单</b>（全部无分支 → SIMD 友好；分支型映射用代数式改写）：
 * <ul>
 *   <li>{@code halfNegative(v) = v − min(v,0)×0.5}、{@code quarterNegative(v) = v − min(v,0)×0.75}
 *       （避免 mask 分支，与标量版语义逐位一致）</li>
 *   <li>{@code lerp3} 的三线性展开与 {@code FloatDensity.InterpolatedF} 同式</li>
 * </ul>
 */
public final class VectorMath {

    /** 批量算子后端（标量 fallback / JDK Vector API 实现）。 */
    public interface Ops {

        String name();

        void lerp(float[] deltas, float[] starts, float[] ends, float[] out, int count);

        void lerp3(
            float[] fx, float[] fy, float[] fz,
            float[] n000, float[] n100, float[] n010, float[] n110,
            float[] n001, float[] n101, float[] n011, float[] n111,
            float[] out, int count
        );

        void clamp(float[] values, float min, float max, float[] out, int count);

        void abs(float[] values, float[] out, int count);

        void square(float[] values, float[] out, int count);

        void cube(float[] values, float[] out, int count);

        void halfNegative(float[] values, float[] out, int count);

        void quarterNegative(float[] values, float[] out, int count);

        void squeeze(float[] values, float[] out, int count);

        void floorDiv(float[] values, float divisor, float[] out, int count);

        void floorMod(float[] values, float divisor, float[] out, int count);
    }

    private static volatile Ops ops = ScalarOps.INSTANCE;

    private VectorMath() {
    }

    /**
     * 安装向量化后端（反射加载 {@link VectorMathJdk}）。
     * <p>失败（未加 --add-modules / 老 JVM）→ 保持标量 fallback，**游戏不受影响**。
     *
     * @return true = 已装上向量化后端
     */
    public static boolean installVectorBackend() {
        try {
            final Class<?> impl = Class.forName("net.MinecraftTools.vectorization.VectorMathJdk");
            // ⚠️ 实现类是包私有的 → getDeclaredMethod + setAccessible（unnamed module 内允许）
            final java.lang.reflect.Method factory = impl.getDeclaredMethod("create");
            factory.setAccessible(true);
            final Ops backend = (Ops) factory.invoke(null);
            ops = backend;
            return true;
        } catch (Throwable t) {
            ops = ScalarOps.INSTANCE;
            return false;
        }
    }

    /** 当前后端（供诊断显示）。 */
    public static Ops ops() {
        return ops;
    }

    public static String backendName() {
        return ops.name();
    }

    // ──────── 门面（委派当前后端）────────

    public static void lerp(final float[] deltas, final float[] starts, final float[] ends, final float[] out, final int count) {
        ops.lerp(deltas, starts, ends, out, count);
    }

    public static void lerp3(
        final float[] fx, final float[] fy, final float[] fz,
        final float[] n000, final float[] n100, final float[] n010, final float[] n110,
        final float[] n001, final float[] n101, final float[] n011, final float[] n111,
        final float[] out, final int count
    ) {
        ops.lerp3(fx, fy, fz, n000, n100, n010, n110, n001, n101, n011, n111, out, count);
    }

    public static void clamp(final float[] values, final float min, final float max, final float[] out, final int count) {
        ops.clamp(values, min, max, out, count);
    }

    public static void abs(final float[] values, final float[] out, final int count) {
        ops.abs(values, out, count);
    }

    public static void square(final float[] values, final float[] out, final int count) {
        ops.square(values, out, count);
    }

    public static void cube(final float[] values, final float[] out, final int count) {
        ops.cube(values, out, count);
    }

    public static void halfNegative(final float[] values, final float[] out, final int count) {
        ops.halfNegative(values, out, count);
    }

    public static void quarterNegative(final float[] values, final float[] out, final int count) {
        ops.quarterNegative(values, out, count);
    }

    public static void squeeze(final float[] values, final float[] out, final int count) {
        ops.squeeze(values, out, count);
    }

    public static void floorDiv(final float[] values, final float divisor, final float[] out, final int count) {
        ops.floorDiv(values, divisor, out, count);
    }

    public static void floorMod(final float[] values, final float divisor, final float[] out, final int count) {
        ops.floorMod(values, divisor, out, count);
    }

    // ──────── 标量 fallback（无模块依赖；也是 SIMD 版的逐位基准）────────

    /** 标量后端（无 Vector API 依赖）。 */
    static final class ScalarOps implements Ops {

        static final ScalarOps INSTANCE = new ScalarOps();

        private ScalarOps() {
        }

        @Override
        public String name() {
            return "scalar";
        }

        @Override
        public void lerp(final float[] deltas, final float[] starts, final float[] ends, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
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
            for (int i = 0; i < count; i++) {
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
            for (int i = 0; i < count; i++) {
                final float v = values[i];
                out[i] = v < min ? min : Math.min(v, max);
            }
        }

        @Override
        public void abs(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                out[i] = Math.abs(values[i]);
            }
        }

        @Override
        public void square(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                final float v = values[i];
                out[i] = v * v;
            }
        }

        @Override
        public void cube(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                final float v = values[i];
                out[i] = v * v * v;
            }
        }

        @Override
        public void halfNegative(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                final float v = values[i];
                out[i] = v - Math.min(v, 0.0f) * 0.5f;
            }
        }

        @Override
        public void quarterNegative(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                final float v = values[i];
                out[i] = v - Math.min(v, 0.0f) * 0.75f;
            }
        }

        @Override
        public void squeeze(final float[] values, final float[] out, final int count) {
            for (int i = 0; i < count; i++) {
                final float c = Math.clamp(values[i], -1.0f, 1.0f);
                out[i] = c / 2.0f - c * c * c / 24.0f;
            }
        }

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
}
