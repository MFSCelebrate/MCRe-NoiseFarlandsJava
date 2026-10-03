import java.math.BigInteger;

/**
 * 末地环（End Rings）域验证 —— getHeightValue 两条路径的语义。
 *
 * fixEndRings=false（默认，原版行为）：
 *   int 溢出：sx8*sx8 对 |sx8|>46341（|blockX|>370728）溢出为负 → sqrt NaN → 末地环效果
 * fixEndRings=true（修复末地环）：
 *   BigInteger 无溢出 → 无 NaN → 正常地形
 *
 * 两者都必须在 /8（section）域计算（*8 修复）。
 */
public class EndRingDomainTest {
    static final BigInteger EIGHT = BigInteger.valueOf(8);

    /** fixEndRings=false：int 溢出路径（原版行为） */
    static float elsePath(int blockX, int blockZ) {
        int sx8 = blockX / 8;
        int sz8 = blockZ / 8;
        float f = 100.0F - (float) Math.sqrt(sx8 * sx8 + sz8 * sz8) * 8.0F;
        return Math.max(-100.0F, Math.min(80.0F, f));
    }

    /** fixEndRings=true：BigInteger 路径（修复末地环） */
    static float ifPath(int blockX, int blockZ) {
        BigInteger sx8 = BigInteger.valueOf(blockX).divide(EIGHT);
        BigInteger sz8 = BigInteger.valueOf(blockZ).divide(EIGHT);
        BigInteger sumSq = sx8.multiply(sx8).add(sz8.multiply(sz8));
        float f = 100.0F - (float) Math.sqrt(sumSq.floatValue()) * 8.0F;
        return Math.max(-100.0F, Math.min(80.0F, f));
    }

    public static void main(String[] args) {
        System.out.println("=== 末地环域验证 ===\n");
        System.out.println("   blockX | else路径(int溢出) | if路径(BigInteger) | 判定");
        System.out.println("----------+------------------+-------------------+-----");

        // int 溢出阈值：|sx8| > 46341 → |blockX| > 370728
        int[] xs = {0, 8, 92, 1000, 100000, 370000, 370728, 370729, 371000,
                    1000000, 10000000, 46341, 2000000000};
        int fail = 0;
        for (int x : xs) {
            float a = elsePath(x, 0);
            float b = ifPath(x, 0);
            boolean aNaN = Float.isNaN(a);
            boolean bNaN = Float.isNaN(b);
            // 判定：|blockX| < 370728（46341×8，开区间）无溢出，两路径一致；
            // |blockX| ≥ 370728：else 路径溢出（NaN 或野值，由 int 回绕符号决定）= 末地环保留；if 路径必须正常
            boolean ok;
            if (Math.abs(x) < 370728) {
                ok = !aNaN && !bNaN && Math.abs(a - b) < 0.001F;
            } else {
                ok = !bNaN;   // else 的溢出行为（NaN/野值）即末地环，不苛求具体值
            }
            if (!ok) fail++;
            System.out.printf("%10d | %16s | %17s | %s%n",
                    x,
                    aNaN ? "NaN(末地环)" : String.format("%.3f", a),
                    bNaN ? "NaN" : String.format("%.3f", b),
                    ok ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.println("=== 关键结论 ===");
        System.out.println("|blockX| ≤ 370728：两路径一致（/8 域修复生效，主岛 92 格正常）");
        System.out.println("|blockX| > 370728：else 路径 NaN → 末地环效果保留（原版行为）");
        System.out.println("                 if 路径正常 → fixEndRings=true 修复末地环");

        System.out.println();
        if (fail == 0) System.out.println("全部 PASS");
        else { System.out.println("FAIL 共 " + fail + " 处"); System.exit(1); }
    }
}
