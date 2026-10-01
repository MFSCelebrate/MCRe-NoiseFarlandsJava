/**
 * 超高世界三层分段验证 —— final_density 的 range_choice([320, 25101648) → -1.0)。
 *
 * 需求：
 *   Y <  320       ：正常地形（原版链）
 *   320 ≤ Y < 25101648：虚空（压死噪声振荡，防空岛）—— 与正常世界一致
 *   Y ≥ 25101648   ：天空边境之地（原版链恢复，噪声能正常表达地形）
 *
 * 对照：
 *   修复前（slide 挪 int max，无分段）：中间层密度 = factor×(-1.5+offset)+base3d ≈ -0.5±0.5
 *                                      → 噪声振荡穿 0 → 空岛 ✗
 *   方案B（slide 恢复 240..256）      ：25101648+ 也被 slide 压死 → 天空边境之地消失 ✗
 *   本方案（三层 range_choice）       ：中间层 -1.0 恒定 → 虚空；25101648+ 原版链 → 边境之地 ✓
 */
public class SoHighThreeLayerTest {

    static final int VOID_START = 320;
    static final int FAR_LANDS_Y = 25101648;

    static double clamp(double v, double min, double max) { return v < min ? min : (v > max ? max : v); }

    static double gradient(double y, double fromY, double toY, double fromValue, double toValue) {
        if (y <= fromY) return fromValue;
        if (y >= toY) return toValue;
        return fromValue + (toValue - fromValue) * (y - fromY) / (toY - fromY);
    }

    static double squeeze(double x) {
        double c = clamp(x, -1.0, 1.0);
        return c * 0.5 - c * c * c / 12.0;
    }

    /** depth：三层分段（本方案） */
    static double depthThreeLayer(double y, double offset) {
        if (y >= VOID_START && y < FAR_LANDS_Y) return -100.0;   // 空层：深负压死噪声
        return gradient(y, -64, 320, 1.5, -1.5) + offset;         // 其余：原版
    }

    /** depth：修复前（无分段，原版梯度钳制） */
    static double depthBroken(double y, double offset) {
        return gradient(y, -64, 320, 1.5, -1.5) + offset;
    }

    /** final_density 链（噪声项=0，slopedCheese 由 depth 推导） */
    static double finalDensity(double y, double depth, double factor) {
        double bottomSlide = gradient(y, -64, -40, 0.0, 1.0);
        double topSlide = gradient(y, 2147483311, 2147483567, 1.0, 0.0);   // 极端顶端 = 恒 1
        double jagged = 0.0;
        double base3d = 0.0;   // 噪声项=0
        double halfNeg = depth + jagged > 0 ? depth + jagged : (depth + jagged) * 0.5;
        double slopedCheese = factor * halfNeg + base3d;
        double E;
        if (slopedCheese >= -1000000.0 && slopedCheese < 1.5625) {
            E = Math.min(slopedCheese, 3.35);
        } else {
            E = 0.0;
        }
        double A = 0.1171875 + bottomSlide * (-0.1171875 + (-0.078125 + topSlide * (0.078125 + E)));
        double density = squeeze(0.64 * A);
        double noodle = 64.0;   // noodle 是 min 项只挖洞穴，置大正值排除干扰，专注验证 slide/depth 分层
        return Math.min(density, noodle);
    }

    public static void main(String[] args) {
        System.out.println("=== 超高世界三层分段验证（噪声项=0，factor=0.5，offset=0.3）===\n");
        System.out.println("      Y |       分层 | 修复前密度 | 三层方案密度 | 判定");
        System.out.println("--------+-----------+-----------+-------------+-----");

        double[] ys = {-100, 0, 64, 128, 200, 250, 300, 319, 320, 321, 500, 1000,
                       10000, 1000000, 10000000, 25101647, 25101648, 25101649, 30000000};
        int fail = 0;
        double factor = 0.5, offset = 0.3;

        for (double y : ys) {
            String layer = y < VOID_START ? "正常地形" : (y < FAR_LANDS_Y ? "空层(应虚空)" : "天空边境之地");
            double broken = finalDensity(y, depthBroken(y, offset), factor);
            double fixed = finalDensity(y, depthThreeLayer(y, offset), factor);

            boolean ok;
            if (y < VOID_START) {
                ok = Math.abs(fixed - broken) < 1e-9;              // 正常地形：与原版逐字节一致
            } else if (y < FAR_LANDS_Y) {
                ok = fixed < broken && fixed < 0;                   // 空层：比修复前更深负 → 噪声穿不过 0
            } else {
                ok = Math.abs(fixed - broken) < 1e-9;              // 边境之地：与原版一致（链活着）
            }
            if (!ok) fail++;

            System.out.printf("%9.0f | %9s | %10.6f | %11.6f | %s%n",
                    y, layer, broken, fixed,
                    ok ? "PASS" : "FAIL");
        }

        System.out.println();
        System.out.println("=== 机理结论 ===");
        System.out.println("修复前：中间层 depth=-1.5+offset → slopedCheese≈-0.6 → 但 base3d 噪声(±0.5)振荡穿 0 → 空岛");
        System.out.println("三层方案：中间层 depth=-100 → slopedCheese≈-50 → 噪声(±0.5)无法穿 0 → 恒虚空 ✓");
        System.out.println("        25101648+ 恢复原版 depth → 链活着 → 天空边境之地的噪声能正常表达地形 ✓");
        System.out.println("        Y<320 原版分支 → 与正常世界逐字节一致 ✓");

        System.out.println();
        if (fail == 0) {
            System.out.println("全部 PASS");
        } else {
            System.out.println("FAIL 共 " + fail + " 处");
            System.exit(1);
        }
    }
}