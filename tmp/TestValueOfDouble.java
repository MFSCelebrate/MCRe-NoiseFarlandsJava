import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;

public class TestValueOfDouble {
    public static void main(String[] args) {
        // 测试用例：各种整数 double（包含大整数）
        double[] testVals = {
            0.0, -0.0,
            1.0, -1.0,
            100.0, -100.0,
            1e10, -1e10,
            9.007199254740992E15,    // 2^53（long 最大精确）
            9.223372036854776E18,    // 2^63（接近 long 上限，Double.toString 会丢精度）
            9223372036854775808.0,   // 2^63 精确 double
            1.23456789012345E20,     // 超 2^63
            1.7976931348623157E308   // Double.MAX_VALUE
        };

        System.out.println("=== 整数 double 精确转换测试 ===");
        boolean allPass = true;
        for (double v : testVals) {
            BigDecimal bd = BigDecimal.valueOf(v);
            String s = bd.toString();
            // 验证：toString 应该跟原 double 的精确十进制一致（不是 Double.toString 的 17 位截断）
            String doubleStr = String.format("%.0f", v);  // Java double 的精确整数表示
            boolean match = s.equals(doubleStr);
            if (!match) {
                allPass = false;
                System.out.println("FAIL: " + v + " -> BigDecimal=" + s + " (expected " + doubleStr + ")");
            } else {
                System.out.println("PASS: " + v + " -> " + s);
            }
        }

        // 测试：WorldReposition 风格的 (pos * scale + shift)
        System.out.println("\n=== WorldReposition 风格测试 ===");
        BigDecimal playerX = BigDecimal.valueOf(9223372036854775808.0);  // 2^63
        BigDecimal scaleX = new BigDecimal("1");
        BigDecimal shiftX = new BigDecimal("0");
        BigDecimal result = playerX.multiply(scaleX).add(shiftX);
        BigInteger bi = result.toBigInteger();
        System.out.println("2^63 * 1 + 0 = " + bi.toString());
        boolean exact = bi.toString().equals("9223372036854775808");
        System.out.println(exact ? "✅ 精确" : "❌ 丢精度");

        // 测试：缩放 1.5
        BigDecimal scale15 = new BigDecimal("1.5");
        BigDecimal result15 = playerX.multiply(scale15).add(shiftX);
        BigInteger bi15 = result15.toBigInteger();
        System.out.println("2^63 * 1.5 = " + bi15.toString());
        // 期望：13835058055282163712（截断小数部分）
        boolean exact15 = bi15.toString().equals("13835058055282163712");
        System.out.println(exact15 ? "✅ 精确" : "❌ 丢精度");

        System.out.println("\n=== 总评 ===");
        System.out.println(allPass && exact && exact15 ? "✅ 全部通过" : "❌ 有失败");
    }
}
