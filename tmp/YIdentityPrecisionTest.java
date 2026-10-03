/** minecraft:y 恒等函数 = yClampedGradient(MIN_Y, MAX_Y, MIN_Y, MAX_Y) 的 double 精度实测 */
public class YIdentityPrecisionTest {
    static final double MIN_Y = -2147483632.0;
    static final double MAX_Y = 2147483632.0;

    static double clampedMap(double x, double minX, double maxX, double min, double max) {
        if (x < minX) return min;
        if (x > maxX) return max;
        return min + (max - min) * ((x - minX) / (maxX - minX));
    }

    /** minecraft:y 恒等 */
    static double yFunction(double blockY) {
        return clampedMap(blockY, MIN_Y, MAX_Y, MIN_Y, MAX_Y);
    }

    public static void main(String[] args) {
        double[] ys = {0, 100, 319, 320, 1000, 100000, 1000000, 10000000,
                       25101646, 25101647, 25101648, 25101649, 25101650,
                       536870912, 1073741824, 2000000000};
        System.out.println("blockY        | minecraft:y 返回值      | 误差        | <25101648? | range_choice 分支");
        System.out.println("--------------+------------------------+-------------+-----------+------------------");
        for (double y : ys) {
            double v = yFunction(y);
            double err = v - y;
            boolean inVoid = v < 25101648.0;
            String branch = (v >= 320.0 && v < 25101648.0) ? "虚空层(-1.0)" : "原版链";
            System.out.printf("%13.0f | %22.6f | %+11.6f | %-9b | %s%n", y, v, err, inVoid, branch);
        }
    }
}
