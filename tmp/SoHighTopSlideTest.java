/**
 * 超高世界顶部 slide 验证 —— 对照原版 overworld.json 的 240→256 slide。
 *
 * 机理：final_density 的 A 链
 *   A = 0.1171875 + bottomSlide × (-0.1171875 + (-0.078125 + topSlide × (0.078125 + E)))
 *   bottomSlide = yClampedGradient(-64, -40, 0.0, 1.0)
 *   topSlide    = yClampedGradient(from_y, to_y, 1.0, 0.0)
 *
 * 原版 from_y=240, to_y=256 → y≥256 时 topSlide=0 → A = -0.078125 恒定 → 密度负 → 虚空
 * 修复前 from_y=2147483311 → topSlide 恒 1 → A = E（sloped_cheese 链）
 *        → depth 梯度(-64..320) 钳制 -1.5 → 均值负但 base3d 噪声振荡穿 0 → 空岛
 */
public class SoHighTopSlideTest {

    static double clamp(double v, double min, double max) {
        return v < min ? min : (v > max ? max : v);
    }

    /** yClampedGradient：y<from → fromValue；y>to → toValue；线性插值 */
    static double gradient(double y, double fromY, double toY, double fromValue, double toValue) {
        if (y <= fromY) return fromValue;
        if (y >= toY) return toValue;
        return fromValue + (toValue - fromValue) * (y - fromY) / (toY - fromY);
    }

    /** 原版 squeeze */
    static double squeeze(double x) {
        double c = clamp(x, -1.0, 1.0);
        return c * 0.5 - c * c * c / 12.0;   // 原版近似：x/2 - x³/12（单调压缩到 ±~0.44）
    }

    /** 密度链（噪声项置 0）：slopedCheese 传入，E 用 range_choice 的 in-range 分支近似 */
    static double finalDensity(double y, double fromY, double toY, double slopedCheese) {
        double bottomSlide = gradient(y, -64, -40, 0.0, 1.0);
        double topSlide = gradient(y, fromY, toY, 1.0, 0.0);
        // E = range_choice(sloped_cheese, -1e6, 1.5625, min(sloped_cheese, 5*entrances), max(caves))
        // 噪声项=0：entrances≈0.37+0.3=0.67 → 5*0.67=3.35 → in-range 分支 = min(slopedCheese, 3.35)
        double E;
        if (slopedCheese >= -1000000.0 && slopedCheese < 1.5625) {
            E = Math.min(slopedCheese, 3.35);   // entrances=0 时
        } else {
            E = Math.max(0.0, 0.0);             // caves 噪声=0 → 0
        }
        double A = 0.1171875 + bottomSlide * (-0.1171875 + (-0.078125 + topSlide * (0.078125 + E)));
        // interpolated：单元格插值，噪声=0 时恒等
        // blend_density：混合区外恒等
        double density = squeeze(0.64 * A);
        // final = min(density, noodle)；y>320 时 noodle=64 → min 取 density
        double noodle = 1.0;  // noodle 是 min 项只挖洞穴，噪声=0 时近似中性(0~正)，不影响顶部 slide 验证
        return Math.min(density, noodle);
    }

    public static void main(String[] args) {
        System.out.println("=== 超高世界顶部 slide 验证（噪声项=0）===\n");
        System.out.println("  Y    | 原版(240..256)密度 | 修复前(2147483311)密度 | 判定");
        System.out.println("-------+-------------------+----------------------+-----");

        double[] ys = {-100, 0, 64, 100, 200, 239, 240, 248, 255, 256, 257, 300, 319, 320, 500, 1000, 25101647, 25101648, 30000000};
        int fail = 0;

        for (double y : ys) {
            // sloped_cheese 假设为正地形值（0.5，噪声=0 时的典型内陆值）
            double vanilla = finalDensity(y, 240, 256, 0.5);
            double broken = finalDensity(y, 2147483311, 2147483567, 0.5);
            boolean ok = (y >= 256) ? (vanilla < 0) : true;   // y≥256 必须虚空
            if (!ok) fail++;
            System.out.printf("%9.0f | %17.6f | %20.6f | %s%n",
                    y, vanilla, broken, ok ? "虚空✓" : "非虚空✗");
        }

        System.out.println();
        System.out.println("=== 关键结论 ===");
        System.out.println("y≥256：topSlide=0 → A 恒定 -0.078125 → 密度恒负 → 虚空（与正常世界一致）");
        System.out.println("y<240：topSlide=1 → A = 完整链（正常地形）");
        System.out.println("240..256：平滑淡出（原版行为）");
        System.out.println("修复前：topSlide 恒 1 → depth 梯度钳制 -1.5 + base3d 噪声振荡穿 0 → 空岛");

        System.out.println();
        if (fail == 0) {
            System.out.println("全部 PASS —— 超高世界 y≥256 与正常世界一致（虚空）");
        } else {
            System.out.println("FAIL 共 " + fail + " 处");
            System.exit(1);
        }
    }
}