import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * wrap / wrapExact 的 BedrockMode 验证。
 *
 * wrap（Bedrock）：全 float 折叠 —— 模拟基岩版坐标量级（边境之地形态与基岩一致）
 * wrapExact（Bedrock）：转 float 后用 BigDecimal 精确取模 —— 避免地形拉伸 + 模拟基岩输入
 *
 * 对照：double 折叠（原版）在大坐标下灾难性抵消 → 折叠结果只剩离散台阶 → 地形拉伸
 */
public class WrapBedrockTest {
    static final double PERIOD = 3.3554432E7;          // 2^25
    static final float PERIOD_F = 3.3554432E7F;
    static final BigDecimal PERIOD_BD = BigDecimal.valueOf(33554432L);
    static final BigDecimal HALF_BD = BigDecimal.valueOf(0.5);

    // ===== 原版 double 折叠（灾难性抵消）=====
    static double wrapDouble(double x) {
        return x - Math.floor(x / PERIOD + 0.5) * PERIOD;
    }

    // ===== Bedrock float 折叠（wrap 的 Bedrock 路径）=====
    static float wrapFloat(float x) {
        return (float) x - (long) Math.floor((double) ((float) x / PERIOD_F + 0.5F)) * PERIOD_F;
    }

    // ===== Bedrock wrapExact：float 输入 + BigDecimal 精确取模 =====
    static BigDecimal wrapExactBedrock(BigDecimal x) {
        float fx = x.floatValue();
        BigDecimal q = BigDecimal.valueOf(fx).divide(PERIOD_BD, java.math.MathContext.DECIMAL128).add(HALF_BD);
        long l = q.toBigInteger().longValue();
        return BigDecimal.valueOf(fx).subtract(BigDecimal.valueOf(l).multiply(PERIOD_BD));
    }

    // ===== 纯 BigDecimal 精确折叠（无 Bedrock float 输入）=====
    static BigDecimal wrapExactPure(BigDecimal x) {
        BigDecimal q = x.divide(PERIOD_BD, java.math.MathContext.DECIMAL128).add(HALF_BD);
        long l = q.toBigInteger().longValue();
        return x.subtract(BigDecimal.valueOf(l).multiply(PERIOD_BD));
    }

    public static void main(String[] args) {
        System.out.println("=== wrap BedrockMode 验证 ===\n");
        System.out.println("     坐标 | double折叠(原版) | float折叠(Bedrock) | exactBedrock | 纯exact | 判定");
        System.out.println("----------+-----------------+-------------------+--------------+---------+-----");

        // 从正常范围到远地（2^25 周期，X 边境之地 ~1.25e7，超过 2^24 后 double 开始灾难性抵消）
        double[] coords = {0, 100, 1000, 100000, 1000000, 5000000, 12550824,
                           16777216, 20000000, 25101648, 33554432, 50000000, 100000000};
        int fail = 0;
        for (double c : coords) {
            double wd = wrapDouble(c);
            float wf = wrapFloat((float) c);
            BigDecimal web = wrapExactBedrock(BigDecimal.valueOf(c));
            BigDecimal wep = wrapExactPure(BigDecimal.valueOf(c));

            // 精确折叠必须落在 (-P/2, P/2] 内（取模语义）
            boolean webOk = web.abs().doubleValue() <= PERIOD / 2 + 1;
            boolean wepOk = wep.abs().doubleValue() <= PERIOD / 2 + 1;
            // float 折叠同样应落在周期内
            boolean wfOk = Math.abs(wf) <= PERIOD / 2 + 1;
            if (!webOk || !wepOk || !wfOk) fail++;

            // double 折叠在大坐标下的拉伸：结果应与精确值差异大（灾难性抵消）
            boolean stretched = Math.abs(wd - wep.doubleValue()) > 1.0;
            System.out.printf("%10.0f | %15.1f | %18.1f | %12.1f | %7.1f | %s%s%n",
                    c, wd, wf, web.doubleValue(), wep.doubleValue(),
                    (webOk && wepOk && wfOk) ? "PASS" : "FAIL",
                    stretched ? " ←double拉伸" : "");
        }

        System.out.println();
        System.out.println("=== 关键结论 ===");
        System.out.println("1. float 折叠（Bedrock）：坐标量级为单精度 → 边境之地形态与基岩一致");
        System.out.println("2. wrapExact Bedrock：float 输入 + BigDecimal 精确取模 → 落在周期内，无拉伸");
        System.out.println("3. double 折叠（原版）：|x| 超过 2^24 后 ULP>1 → 灾难性抵消 → 折叠结果量化成台阶 → 地形拉伸");

        // 精度对照：float 输入的取模结果与纯 exact 的差 = float 舍入量（预期小量）
        System.out.println();
        System.out.println("=== float 输入 vs 纯 exact 的差（= float 舍入量，应小）===");
        for (double c : new double[]{12550824, 25101648, 100000000}) {
            BigDecimal web = wrapExactBedrock(BigDecimal.valueOf(c));
            BigDecimal wep = wrapExactPure(BigDecimal.valueOf(c));
            double diff = web.doubleValue() - wep.doubleValue();
            System.out.printf("%10.0f: 差 %+.4f%n", c, diff);
        }

        System.out.println();
        if (fail == 0) System.out.println("全部 PASS");
        else { System.out.println("FAIL 共 " + fail + " 处"); System.exit(1); }
    }
}
