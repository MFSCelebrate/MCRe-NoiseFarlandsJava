import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
import net.MinecraftTools.Math.DynamicAccuracy.RoundingMode;

/**
 * MCRe 实测锤：地形拉伸 = 浮点 ULP 量化
 *
 * 复刻完整链路（以 BlendedNoise 的 limitX 分支为例）：
 *   pos(整数方块坐标) → WorldReposition(恒等) → × xzMultiplier(171.103) → × 2^k(octave factor)
 *   → PerlinNoise.wrap(折叠 64bit 模式, 周期 2^25) → ImprovedNoise: x = folded + xo
 *   → xf = (int)Math.floor(x)   ← int 饱和（必须保留！）
 *   → xr = x - xf               ← 小数部分（插值权重来源）
 *
 * 判据：相邻整数坐标 pos 与 pos+1 的 xr 是否还能区分。
 *   - 能区分 → 地形逐格变化 ✓
 *   - 不能区分（相同）→ 拉伸（台阶）✗
 */
public class StretchProof {
    static final double XZ_MULT = 684.412 * 0.25;   // = 171.103
    static final double PERIOD = 3.3554432E7;       // = 2^25（精确）
    static final double XO = 123.456789012345;      // 模拟 ImprovedNoise 的随机偏移 xo
    static final int OCTAVE = 15;                   // 最高频 octave（factor = 2^15）

    // ─────────────── 当前 double 链路 ───────────────

    static double dblFolded(double pos) {
        double limitX = pos * XZ_MULT;
        double v = limitX * Math.pow(2.0, OCTAVE);
        return v - Math.floor(v / PERIOD + 0.5) * PERIOD;   // 64bit 模式 wrap
    }

    static double dblXr(double pos) {
        double x = dblFolded(pos) + XO;
        int xf = (int) Math.floor(x);                        // ← int 饱和语义（保留）
        return x - xf;
    }

    // ─────────────── 精确链路（BigDecimal） ───────────────

    static final BigDecimal BD_MULT = new BigDecimal(XZ_MULT);
    static final BigDecimal BD_PERIOD = new BigDecimal(PERIOD);
    static final BigDecimal BD_HALF = new BigDecimal("0.5");
    static final BigDecimal BD_XO = new BigDecimal(XO);
    static final BigDecimal BD_POW2 = new BigDecimal(Math.pow(2.0, OCTAVE));

    static BigDecimal exactFolded(double pos) {
        BigDecimal v = new BigDecimal(pos).multiply(BD_MULT).multiply(BD_POW2);
        BigDecimal q = v.divide(BD_PERIOD, new MathContext(60, RoundingMode.HALF_EVEN)).add(BD_HALF);
        BigDecimal f = q.setScale(0, RoundingMode.FLOOR);
        return v.subtract(f.multiply(BD_PERIOD));
    }

    static BigDecimal exactXr(double pos) {
        BigDecimal x = exactFolded(pos).add(BD_XO);
        BigDecimal xf = x.setScale(0, RoundingMode.FLOOR);    // 精确 floor
        return x.subtract(xf);
    }

    // ─────────────── 统计：相邻整数坐标的区分度 ───────────────

    static void probe(double base) {
        int n = 64;
        int dblDiff = 0, exactDiff = 0;
        double maxDblErr = 0;
        for (int i = 0; i < n; i++) {
            double p0 = base + i, p1 = base + i + 1;
            if (dblXr(p0) != dblXr(p1)) dblDiff++;
            if (exactXr(p0).compareTo(exactXr(p1)) != 0) exactDiff++;
            double err = Math.abs(dblXr(p0) - exactXr(p0).doubleValue());
            if (err > maxDblErr) maxDblErr = err;
        }
        double ulp = Math.ulp(base * XZ_MULT * Math.pow(2.0, OCTAVE));
        System.out.printf("pos=%-12.0f  double区分度=%2d/%d  精确区分度=%2d/%d  |xr|最大误差=%.3e  ULP(折叠前)=%.3e%n",
                base, dblDiff, n, exactDiff, n, maxDblErr, ulp);
    }

    public static void main(String[] args) {
        System.out.println("=== 链路：pos × 171.103 × 2^15 → wrap(2^25) → +xo → floor → xr ===");
        System.out.println("（区分度 = 相邻整数坐标能得到不同 xr 的比例；0 表示完全拉伸）\n");
        double[] bases = {1e3, 1e6, 1e9, 1e12, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19, 1e21, 1e23};
        for (double b : bases) {
            probe(b);
        }

        System.out.println("\n=== 细节：pos = 1e15 起步的 xr 序列（double vs 精确）===");
        for (int i = 0; i < 6; i++) {
            double p = 1e15 + i;
            System.out.printf("pos=%.0f  double xr=%.17f  exact xr=%.17f%n",
                    p, dblXr(p), exactXr(p).doubleValue());
        }
    }
}
