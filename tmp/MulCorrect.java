import java.util.Random;
public class MulCorrect {
    static long bad;
    public static void main(String[] a) {
        Random r = new Random(2024);
        for (int t = 0; t < 500; t++) {
            int w = 1 + r.nextInt(20);
            byte[] ba = new byte[w*4+1], bb = new byte[w*4+1];
            r.nextBytes(ba); r.nextBytes(bb);
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger oa = new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(ba);
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger ob = new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(bb);
            java.math.BigInteger ja = new java.math.BigInteger(ba);
            java.math.BigInteger jb = new java.math.BigInteger(bb);
            java.math.BigInteger jprod = ja.multiply(jb);
            byte[] bl = jprod.toByteArray();
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger oursProd = new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(bl);
            if (!oa.multiply(ob).equals(oursProd)) {
                bad++;
                if (bad < 3) System.out.println("FAIL t="+t);
            }
        }
        System.out.println(bad == 0 ? "乘法正确性(完整乘积): 500/500 通过" : ("乘法正确性: " + bad + " 失败"));
    }
}
