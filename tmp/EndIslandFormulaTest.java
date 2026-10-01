import java.math.BigInteger;

/**
 * 末地岛 base falloff 公式验证 —— 对照 C2ME EndIslandsBenchmark.sampleVanilla（逐字原版实现）。
 *
 * 原版语义（关键）：
 *   compute(context) = (getHeightValue(noise, blockX/8, blockZ/8) - 8.0) / 128.0;
 *   getHeightValue 的入参 sectionX = blockX/8（section 域）
 *   base falloff: f = 100 - sqrt(sectionX² + sectionZ²) * 8   ← 必须在 /8 域！
 *
 * MCRe 现状：compute 传原始 blockX（为在内部应用 WorldReposition 偏移）
 *   → else 路径用原始 blockX 算 base falloff = 差 8 倍 → 主岛 8 倍缩小 → 圆柱
 */
public class EndIslandFormulaTest {

    // ===== 原版参考（C2ME benchmark，入参已是 section 域）=====
    static float vanillaBaseFalloff(int sectionX, int sectionZ) {
        float f = 100.0F - (float) Math.sqrt((float) (sectionX * sectionX + sectionZ * sectionZ)) * 8.0F;
        return Math.max(-100.0F, Math.min(80.0F, f));
    }

    // ===== MCRe 修复前（bug）：用原始 block 域 =====
    static float mcreBrokenBaseFalloff(int blockX, int blockZ) {
        return vanillaBaseFalloff(blockX, blockZ);  // 等价于把 block 坐标当 section 坐标用
    }

    // ===== MCRe 修复后：BigInteger + /8 域归约 =====
    static final BigInteger EIGHT = BigInteger.valueOf(8);

    static float mcreFixedBaseFalloff(int blockX, int blockZ) {
        BigInteger bx = BigInteger.valueOf(blockX).divide(EIGHT);
        BigInteger bz = BigInteger.valueOf(blockZ).divide(EIGHT);
        BigInteger sumSq = bx.multiply(bx).add(bz.multiply(bz));
        float f = 100.0F - (float) Math.sqrt(sumSq.floatValue()) * 8.0F;
        return Math.max(-100.0F, Math.min(80.0F, f));
    }

    public static void main(String[] args) {
        System.out.println("=== 末地岛 base falloff 公式验证 ===\n");

        System.out.println("blockX | vanilla(/8域) | 修复前(raw域) | 修复后(BigInteger/8) | 判定");
        System.out.println("-------+---------------+--------------+---------------------+-----");

        int[] testX = {0, 8, 16, 24, 48, 64, 92, 100, 128, 200, 256, 512, 1000,
                       -8, -16, -64, -200, 30000000, -30000000, 2100000000};
        int failCount = 0;

        for (int bx : testX) {
            float vanilla = vanillaBaseFalloff(bx / 8, 0);
            float broken = mcreBrokenBaseFalloff(bx, 0);
            float fixed = mcreFixedBaseFalloff(bx, 0);

            // 修复后必须与 vanilla 一致（float 精度容差）
            boolean fixedOk = Math.abs(fixed - vanilla) < 0.001F;
            if (!fixedOk) failCount++;

            System.out.printf("%10d | %13.3f | %12.3f | %19.3f | %s%s%n",
                    bx, vanilla, broken, fixed,
                    fixedOk ? "PASS" : "FAIL",
                    (broken != vanilla) ? " (修复前偏差)" : "");
        }

        System.out.println();
        System.out.println("=== 主岛半径对比 ===");
        System.out.println("vanilla (section 域):  f=0 处 sqrt(sectionX²)=11.5 → blockX = 11.5*8 = 92 格");
        System.out.println("修复前  (raw 域):      f=0 处 sqrt(blockX²)=11.5 → blockX = 11.5 格  ← 8 倍缩小！");
        System.out.println("修复后  (BigInteger):  与 vanilla 一致 → 92 格 ✓");

        // 2D 采样点抽查（x,z 都非零）
        System.out.println();
        System.out.println("=== 2D 抽查（x,z 均非零）===");
        int[][] pts = {{100, 50}, {200, 100}, {-64, 32}, {92, 92}, {1000, -500}};
        for (int[] p : pts) {
            float vanilla = vanillaBaseFalloff(p[0] / 8, p[1] / 8);
            float fixed = mcreFixedBaseFalloff(p[0], p[1]);
            boolean ok = Math.abs(fixed - vanilla) < 0.001F;
            if (!ok) failCount++;
            System.out.printf("(%6d,%6d) vanilla=%9.3f fixed=%9.3f %s%n",
                    p[0], p[1], vanilla, fixed, ok ? "PASS" : "FAIL");
        }

        // 大坐标溢出检查（原版 int 溢出 → NaN；BigInteger 无溢出）
        System.out.println();
        System.out.println("=== 溢出/NaN 检查 ===");
        int bigX = 2100000000;
        float vanillaBig = vanillaBaseFalloff(bigX / 8, 0);   // section 域 2.6e8，平方 7e16 溢出 float? 不会
        float fixedBig = mcreFixedBaseFalloff(bigX, 0);
        boolean vanillaNaN = Float.isNaN(vanillaBaseFalloff(bigX, bigX));  // raw 域 x² 溢出
        System.out.printf("raw 域 x=z=21e8: NaN=%b（原版 int 溢出现象）%n", vanillaNaN);
        System.out.printf("BigInteger 路径: %f（无溢出无 NaN）%n", fixedBig);
        if (Float.isNaN(fixedBig)) failCount++;

        System.out.println();
        if (failCount == 0) {
            System.out.println("全部 PASS —— 修复后公式与原版基线一致");
        } else {
            System.out.println("FAIL 共 " + failCount + " 处");
            System.exit(1);
        }
    }
}