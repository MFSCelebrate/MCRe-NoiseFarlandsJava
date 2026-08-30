import net.MinecraftTools.Serializations.Base58Transfer;
import java.util.Random;

public class TestBase58 {
    public static void main(String[] a) {
        // 参考向量
        String ref = Base58Transfer.encode("2026-08-30");
        System.out.println("encode(2026-08-30) = " + ref + (ref.equals("3pYN3o6QAeKXm1") ? "  ✓" : "  ✗ 期望 3pYN3o6QAeKXm1"));
        // 往返
        Random r = new Random(1);
        int pass = 0, fail = 0;
        for (int t = 0; t < 2000; t++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + r.nextInt(40);
            for (int i = 0; i < len; i++) {
                char c;
                do { c = (char) (r.nextInt(0x80)); } while (c == 0); // 避免极端前导0场景单独验证
                sb.append(c);
            }
            String s = sb.toString();
            String enc = Base58Transfer.encode(s);
            String dec = Base58Transfer.decode(enc);
            if (s.equals(dec)) pass++; else { fail++; if (fail<5) System.out.println("FAIL: " + s + " -> " + enc + " -> " + dec); }
        }
        // 前导零字节
        String leadZero = "\u0000\u0000abc";
        System.out.println("前导零: enc='" + Base58Transfer.encode(leadZero) + "' 往返=" + Base58Transfer.decode(Base58Transfer.encode(leadZero)).equals(leadZero));
        System.out.println("往返测试: " + pass + " pass, " + fail + " fail");
    }
}
