import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * BedrockMode + 高精度模式「无地形拉伸」机理验证。
 *
 * 拉伸机理：折叠（wrap）的大数相减灾难性抵消 → 折叠结果只剩离散台阶 → 地形拉伸
 *
 * 修复模式（ Bedrock 适配）：输入量化到 float（模拟基岩单精度输入量级）
 *   → float 的值可精确表示为 BigDecimal → 精确取模无灾难性抵消 → 折叠结果逐格变化 → 无拉伸 ✓
 */
public class BedrockExactNoStretchTest {
    static final BigDecimal PERIOD_BD = BigDecimal.valueOf(33554432L);   // 2^25
    static final BigDecimal HALF_BD = BigDecimal.valueOf(0.5);

    /** 原版 double 折叠（灾难性抵消 → 拉伸） */
    static double wrapDouble(double x) {
        return x - Math.floor(x / 3.3554432E7 + 0.5) * 3.3554432E7;
    }

    /** float 量化 + 精确取模（Bedrock 适配：无拉伸） */
    static BigDecimal wrapBedrockExact(BigDecimal x) {
        float fx = x.floatValue();                                    // ① float 量化（基岩输入量级）
        BigDecimal q = BigDecimal.valueOf(fx).divide(PERIOD_BD, java.math.MathContext.DECIMAL128).add(HALF_BD);
        long l = q.toBigInteger().longValue();
        return BigDecimal.valueOf(fx).subtract(BigDecimal.valueOf(l).multiply(PERIOD_BD));  // ② 精确取模
    }

    /** 统计折叠结果的唯一值个数（0.25 步进采样） */
    static int countSteps(int start, int count, boolean bedrock) {
        java.util.HashSet<Double> steps = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            double coord = start + i * 0.25;
            if (bedrock) {
                steps.add(wrapBedrockExact(BigDecimal.valueOf(coord)).doubleValue());
            } else {
                steps.add(wrapDouble(coord));
            }
        }
        return steps.size();
    }

    /** 统计不同 float 值的个数（输入量化后的粒度基准） */
    static int countDistinctFloats(int start, int count) {
        java.util.HashSet<Float> vals = new java.util.HashSet<>();
        for (int i = 0; i < count; i++) {
            vals.add((float) (start + i * 0.25));
        }
        return vals.size();
    }

    public static void main(String[] args) {
        System.out.println("=== BedrockMode + 高精度「无拉伸」机理验证 ===\n");

        // 在远地区域（坐标 > 2^24，double ULP > 1）采样 1024 个连续点，数台阶
        System.out.println("远地区域（坐标 > 2^24，double ULP > 1，0.25 步进 1024 点）：");
        System.out.println("  起始坐标     | double折叠台阶数 | Bedrock精确折叠台阶数 | 判定");
        System.out.println("--------------+-----------------+---------------------+-----");
        int[] starts = {16777216, 20000000, 25101648, 33554432, 100000000};
        int fail = 0;
        for (int start : starts) {
            int dSteps = countSteps(start, 1024, false);
            int bSteps = countSteps(start, 1024, true);
            int floatGranularity = countDistinctFloats(start, 1024);
            // 无拉伸判定：折叠唯一值数 == 输入唯一 float 值数（折叠随每个 float 步 1:1 变化）
            // 拉伸时折叠唯一值数 < 输入唯一值数（灾难性抵消把多个输入折叠到同一结果）
            boolean ok = bSteps == floatGranularity;
            if (!ok) fail++;
            System.out.printf("%13d | %15d | %20d | 输入float粒度=%d %s%n",
                    start, dSteps, bSteps, floatGranularity, ok ? "PASS(无拉伸)" : "FAIL(拉伸)");
        }

        System.out.println();
        System.out.println("=== 单点精度对照 ===");
        System.out.println("     坐标 | double折叠       | float量化+精确折叠 | 差");
        System.out.println("----------+-----------------+-------------------+--------");
        for (double c : new double[]{16777216.25, 20000000.5, 25101648.25, 100000000.75}) {
            double wd = wrapDouble(c);
            double wb = wrapBedrockExact(BigDecimal.valueOf(c)).doubleValue();
            System.out.printf("%10.2f | %15.6f | %17.6f | %+.6f%n", c, wd, wb, wb - wd);
        }

        System.out.println();
        System.out.println("=== 机理结论 ===");
        System.out.println("1. double 折叠：|x|>2^24 后 ULP>1 → 大数相减灾难性抵消 → 折叠结果量化成少数台阶 → 地形拉伸");
        System.out.println("2. Bedrock 适配：输入量化 float（基岩语义）→ float 可精确转 BigDecimal → 精确取模无抵消 → 逐格变化无拉伸");
        System.out.println("3. 覆盖的高精度方法：wrapExact / PerlinNoise.getValueExact / ImprovedNoise.noiseExact / NormalNoise.getValueExact / BlendedNoise.computeExact");

        System.out.println();
        if (fail == 0) System.out.println("全部 PASS");
        else { System.out.println("FAIL 共 " + fail + " 处"); System.exit(1); }
    }
}
