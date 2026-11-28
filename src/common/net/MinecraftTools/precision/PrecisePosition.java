package net.MinecraftTools.precision;

import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import net.MinecraftTools.Math._256bit_Refactoring.IntRefactor256;
import java.math.BigInteger;

/**
 * 🔧 MCRe「高精度位置」：把实体位置的<b>真值</b>存成 237 位尾数的二进制浮点（{@link FloatRefactor256}），
 * 而<b>对外仍以 double 投影</b>（出口不变 → vanilla 全部读路径零改动、逐位兼容）。
 *
 * <p><b>为什么需要</b>：double 只有 53 位尾数（约 15.95 位十进制有效数字），坐标到 1e46 时 ULP 已达 1.27e30 ——
 * 此时 {@code pos + 0.1} 直接<b>归零</b>，缩放后的坐标末尾出现一长串 000000。MCRe 的偏移缩放（WorldReposition）
 * 会把这类量化原样放大，于是「坐标越远、精度越差」。
 *
 * <p><b>本类解决什么</b>：让位置的<b>累加</b>发生在高精度域（{@code 237 位}），double 只作为「出口投影」。
 * 实测：1.2345678901234567e46 处加 0.1，double 结果与原值完全相同（归零），
 * 而本类结果保留到小数位 {@code .1000000000000000055511151231257827021181583404541015625}。
 *
 * <p><b>设计要点</b>：
 * <ul>
 *   <li><b>投影缓存</b>：精确值只在变化时重算投影，{@code x()/y()/z()} 只是读 double 字段 ——
 *       实体 getter 每 tick 被调用上千次，不能每次都做 256 位 → double 的转换（约 600 ns）。</li>
 *   <li><b>投影回填保护</b>：{@code set(x, y, z)} 收到的 double 若与当前精确值的投影<b>逐位相同</b>，
 *       则<b>保留</b>原精确值（不按 double 重建）—— 否则 vanilla 的 reapplyPosition / snapTo /
 *       teleportSetPosition 这类「把投影原样回填」的路径会把 1280/237 位真值降级回 double，
 *       一次回填在 5e18 量级就可能跳 512 格。</li>
 *   <li><b>旧位置也存精确</b>：{@code setOldFromCurrent()} 复制当前精确值 → 相机插值起点不再被 double 量化。</li>
 * </ul>
 *
 * <p>本类只负责「一个实体的位置与旧位置」，不含帧（锚点 + 局部）语义；帧架构用于解决
 * 「两个世界量级 double 相减 / 插值」被 ulp 吞掉的问题，属后续扩展。
 *
 * @see net.MinecraftTools.precision.PrecisePosition
 */
public final class PrecisePosition {

    /** 当前精确位置（三轴）。 */
    private FloatRefactor256 exactX;
    private FloatRefactor256 exactY;
    private FloatRefactor256 exactZ;

    /** 上一 tick 的精确位置（三轴）。 */
    private FloatRefactor256 oldExactX;
    private FloatRefactor256 oldExactY;
    private FloatRefactor256 oldExactZ;

    /** 当前精确值的 double 投影（缓存，避免 getter 每次转换）。 */
    private double projX;
    private double projY;
    private double projZ;

    /** 旧精确值的 double 投影。 */
    private double oldProjX;
    private double oldProjY;
    private double oldProjZ;

    // ══════════ 帧（frame）= 锚点（256 位整数）+ 局部（double ∈ [0,1)）══════════
    // 动机：两个「世界量级」的 double 直接相减，会在该量级的 ulp 处被吞掉
    //   （5e18 处 ulp=1024 → 位移被吞成 0 或 1024）；插值同理。
    // 帧把「量级」交给锚点（整数、精确），把「小数」交给局部（double），
    // 于是差值与插值都在小数域完成 → 不再被 ulp 吞并。
    /** 帧锚点：精确值的整数下取整（|坐标| < FRAME_LIMIT 时为 0 → 局部等于世界值 → 逐位不变）。 */
    private IntRefactor256 anchorX;
    private IntRefactor256 anchorY;
    private IntRefactor256 anchorZ;

    /** 帧内局部：锚点为 0 时是位置投影本身，否则落在 [0,1)。 */
    private double localX;
    private double localY;
    private double localZ;

    /** 旧位置的帧锚点与局部。 */
    private IntRefactor256 oldAnchorX;
    private IntRefactor256 oldAnchorY;
    private IntRefactor256 oldAnchorZ;
    private double oldLocalX;
    private double oldLocalY;
    private double oldLocalZ;

    /**
     * 帧阈值：|坐标| 小于它时锚点取 0。
     * <p>该段内「局部 = 世界值」，vanilla 的形状与本帧同源 → 行为与改动前**逐位相同**、且不分配。
     */
    private static final BigInteger FRAME_LIMIT = new BigInteger("1000000000000");

    private PrecisePosition(final double x, final double y, final double z) {
        this.exactX = FloatRefactor256.of(x);
        this.exactY = FloatRefactor256.of(y);
        this.exactZ = FloatRefactor256.of(z);
        this.oldExactX = this.exactX;
        this.oldExactY = this.exactY;
        this.oldExactZ = this.exactZ;
        this.projX = x;
        this.projY = y;
        this.projZ = z;
        this.oldProjX = x;
        this.oldProjY = y;
        this.oldProjZ = z;
        this.refreshFrame();
        this.oldAnchorX = this.anchorX;
        this.oldAnchorY = this.anchorY;
        this.oldAnchorZ = this.anchorZ;
        this.oldLocalX = this.localX;
        this.oldLocalY = this.localY;
        this.oldLocalZ = this.localZ;
    }

    /** 由 double 三轴建立高精度位置（首次建立用）。 */
    public static PrecisePosition of(final double x, final double y, final double z) {
        return new PrecisePosition(x, y, z);
    }

    /**
     * 写入位置（来自 vanilla 的 double）。
     *
     * <p><b>投影回填保护</b>：入参与当前精确值的投影逐位相同 → <b>保留</b>原精确值；
     * 否则视为一次新的权威赋值（传送 / 外部写入），用 double 重建精确值。
     */
    public void set(final double x, final double y, final double z) {
        this.exactX = keepIfProjection(this.exactX, x);
        this.exactY = keepIfProjection(this.exactY, y);
        this.exactZ = keepIfProjection(this.exactZ, z);
        this.projX = x;
        this.projY = y;
        this.projZ = z;
        this.refreshFrame();
    }

    /**
     * 把当前精确值复制为旧位置（保留精度）。
     *
     * <p>vanilla 的 {@code setOldPos(this.position)} 传的是 double 投影，会把旧位置降级；
     * 这里改为复制精确值 → 相机插值起点不再被量化。
     */
    public void setOldFromCurrent() {
        this.oldExactX = this.exactX;
        this.oldExactY = this.exactY;
        this.oldExactZ = this.exactZ;
        this.oldProjX = this.projX;
        this.oldProjY = this.projY;
        this.oldProjZ = this.projZ;
        this.oldAnchorX = this.anchorX;
        this.oldAnchorY = this.anchorY;
        this.oldAnchorZ = this.anchorZ;
        this.oldLocalX = this.localX;
        this.oldLocalY = this.localY;
        this.oldLocalZ = this.localZ;
    }

    /**
     * 直接写入精确值（不经 double 中转）—— 传送等「落点已知为高精度」的场景。
     *
     * <p>写完后投影与帧同步刷新；随后 vanilla 的 double 回填（{@code setPosRaw}）因投影相同
     * 会被 {@code keepIfProjection} 保留，**不会把精确值降级** ✓
     */
    public void setExact(final FloatRefactor256 x, final FloatRefactor256 y, final FloatRefactor256 z) {
        this.exactX = x;
        this.exactY = y;
        this.exactZ = z;
        this.projX = x.toDouble();
        this.projY = y.toDouble();
        this.projZ = z.toDouble();
        this.refreshFrame();
    }

    /**
     * 在高精度域累加位移量。
     *
     * <p>位移量本身仍是 double（小数量级，double 完全够），但<b>累加发生在 237 位尾数上</b> →
     * 1e46 处加 0.1 不再归零。
     */
    public void add(final double dx, final double dy, final double dz) {
        if (dx != 0.0) {
            this.exactX = this.exactX.add(FloatRefactor256.of(dx));
        }
        if (dy != 0.0) {
            this.exactY = this.exactY.add(FloatRefactor256.of(dy));
        }
        if (dz != 0.0) {
            this.exactZ = this.exactZ.add(FloatRefactor256.of(dz));
        }
        this.refreshProjection();
        // 🔧 MCRe 修复（2026-11-27）：精确值变了，帧（锚点 + 局部）也必须重算，
        //   否则局部量停留在旧值 → 帧内位移恒为 0（被单元测试 frameDeltaSurvivesHugeCoordinates 逮到）
        this.refreshFrame();
    }

    /** 重算投影缓存（精确值变化后调用）。 */
    private void refreshProjection() {
        this.projX = this.exactX.toDouble();
        this.projY = this.exactY.toDouble();
        this.projZ = this.exactZ.toDouble();
    }

    /** 重算帧（锚点 + 局部）。 */
    private void refreshFrame() {
        this.anchorX = anchorOf(this.exactX);
        this.anchorY = anchorOf(this.exactY);
        this.anchorZ = anchorOf(this.exactZ);
        this.localX = localOf(this.exactX, this.anchorX);
        this.localY = localOf(this.exactY, this.anchorY);
        this.localZ = localOf(this.exactZ, this.anchorZ);
    }

    /**
     * 帧锚点：精确值的整数下取整。
     * <p>|值| 小于 {@link #FRAME_LIMIT} 时取 0（共享常量，不分配）→ 该段内局部等于世界值，逐位不变。
     */
    private static IntRefactor256 anchorOf(final FloatRefactor256 value) {
        if (!value.isFinite() || value.isZero()) {
            return IntRefactor256.ZERO;
        }

        final BigInteger magnitude = value.abs().toBigInteger();

        if (magnitude.compareTo(FRAME_LIMIT) < 0) {
            return IntRefactor256.ZERO;
        }

        // 超阈值：锚点 = 精确下取整。
        //   ⚠️ 截断向零对**正数**已经是 floor；对**负数**才是 ceil → 负数非整数时要再退一格。
        //   （初版把这条写反 → 锚点比真值大 1 → 局部为负 → 帧内位移整整差 1，被单元测试逮到）
        final boolean integral = value.abs().subtract(FloatRefactor256.of(magnitude)).isZero();
        BigInteger floorMagnitude = magnitude;
        if (value.signum() < 0 && !integral) {
            floorMagnitude = magnitude.add(BigInteger.ONE);
        }
        if (value.signum() < 0) {
            floorMagnitude = floorMagnitude.negate();
        }

        return IntRefactor256.of(floorMagnitude);
    }

    /**
     * 帧内局部：锚点为 0 时是投影本身，否则是「精确值 − 锚点」（落在 [0,1)）。
     *
     * <p>⚠️ <b>不能用浮点减法</b>：237 位尾数盖不住「1e46 量级的整数 + 2^-84 量级的小数」这个跨度
     * → 减法会丢尾（实测局部残差 1.36e-20 而非 0.25）。
     * <p>这里走<b>精确十进制</b>路径：精确值的十进制展开 − 锚点的十进制展开 → 再转 double。
     * 代价是每帧一次十进制转换（玩家级可接受）。
     */
    private static double localOf(final FloatRefactor256 value, final IntRefactor256 anchor) {
        if (anchor.isZero()) {
            return value.toDouble();
        }

        return new java.math.BigDecimal(value.toExactString())
                .subtract(new java.math.BigDecimal(anchor.toString()))
                .doubleValue();
    }

    /** 投影相同则保留原精确值，否则按 double 重建。 */
    private static FloatRefactor256 keepIfProjection(final FloatRefactor256 current, final double value) {
        if (current != null && current.toDouble() == value) {
            return current;
        }
        return FloatRefactor256.of(value);
    }

    /** 当前 X 的 double 投影。 */
    public double x() {
        return this.projX;
    }

    /** 当前 Y 的 double 投影。 */
    public double y() {
        return this.projY;
    }

    /** 当前 Z 的 double 投影。 */
    public double z() {
        return this.projZ;
    }

    /** 旧位置 X 的 double 投影。 */
    public double oldX() {
        return this.oldProjX;
    }

    /** 旧位置 Y 的 double 投影。 */
    public double oldY() {
        return this.oldProjY;
    }

    /** 旧位置 Z 的 double 投影。 */
    public double oldZ() {
        return this.oldProjZ;
    }

    /** 当前 X 的精确值（237 位尾数）。 */
    public FloatRefactor256 exactX() {
        return this.exactX;
    }

    /** 当前 Y 的精确值。 */
    public FloatRefactor256 exactY() {
        return this.exactY;
    }

    /** 当前 Z 的精确值。 */
    public FloatRefactor256 exactZ() {
        return this.exactZ;
    }

    /** 旧位置 X 的精确值。 */
    public FloatRefactor256 oldExactX() {
        return this.oldExactX;
    }

    /** 旧位置 Y 的精确值。 */
    public FloatRefactor256 oldExactY() {
        return this.oldExactY;
    }

    /** 旧位置 Z 的精确值。 */
    public FloatRefactor256 oldExactZ() {
        return this.oldExactZ;
    }

    // ══════════ 帧 API ══════════

    /** 该轴的帧锚点（256 位整数）。 */
    public IntRefactor256 anchor(final int axis) {
        return switch (axis) {
            case 0 -> this.anchorX;
            case 1 -> this.anchorY;
            default -> this.anchorZ;
        };
    }

    /** 该轴的帧内局部（锚点为 0 时就是世界投影）。 */
    public double local(final int axis) {
        return switch (axis) {
            case 0 -> this.localX;
            case 1 -> this.localY;
            default -> this.localZ;
        };
    }

    /** 旧位置的帧锚点。 */
    public IntRefactor256 oldAnchor(final int axis) {
        return switch (axis) {
            case 0 -> this.oldAnchorX;
            case 1 -> this.oldAnchorY;
            default -> this.oldAnchorZ;
        };
    }

    /** 旧位置的帧内局部。 */
    public double oldLocal(final int axis) {
        return switch (axis) {
            case 0 -> this.oldLocalX;
            case 1 -> this.oldLocalY;
            default -> this.oldLocalZ;
        };
    }

    /**
     * 旧位置 → 当前位置的位移（<b>帧内相减</b>，单位格）。
     *
     * <p>锚点差用整数精确算，局部差用小数算 → 两个世界量级的 double 直接相减会被 ulp 吞掉的问题不存在。
     */
    public double frameDelta(final int axis) {
        final double anchorGap = this.anchor(axis).subtract(this.oldAnchor(axis)).toDouble();
        return anchorGap + (this.local(axis) - this.oldLocal(axis));
    }

    /**
     * 上一 tick → 当前 tick 的插值（<b>帧内</b>），返回值相对当前帧锚点。
     *
     * <p>两端先在帧内相减（差量只有一 tick 的位移）再插值 → 插值起点与终点都不被 double 量化。
     */
    public double lerpLocal(final int axis, final float partial) {
        final double oldLocal = this.oldLocal(axis);
        return oldLocal + (this.local(axis) - oldLocal) * partial;
    }

    /** 该轴的精确方块格号（向下取整；超出 long 时饱和）。 */
    public long cellCoord(final int axis) {
        return this.anchor(axis).toLong();
    }
}
