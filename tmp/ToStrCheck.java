import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import java.util.Random;
public class ToStrCheck {
    public static void main(String[] args) {
        Random r = new Random(7);
        int pass=0, fail=0;
        for (int bits : new int[]{1,8,16,32,63,64,65,128,192,256,512,1024,2048,4096}) {
            for (int radix : new int[]{2,8,10,16,36}) {
                for (int t = 0; t < 20; t++) {
                    BigInteger v = BigInteger.ONE;
                    for (int i = 0; i < bits; i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
                    if (r.nextBoolean()) v = v.negate();
                    String s = v.toString(radix);
                    BigInteger back = new BigInteger(s, radix);
                    if (v.equals(back)) pass++; else { fail++; if (fail<8) System.out.println("FAIL bits="+bits+" radix="+radix); }
                }
            }
        }
        System.out.println("toString roundtrip: " + pass + " pass, " + fail + " fail");
        // 0 和 1
        System.out.println("ZERO: '" + BigInteger.ZERO + "'  ONE: '" + BigInteger.ONE + "'  TEN: '" + BigInteger.TEN + "'");
        System.out.println("neg: '" + BigInteger.valueOf(-123456789012345678L) + "'");
        System.out.println("2^64-1: '" + BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE) + "'");
        System.out.println("2^64: '" + BigInteger.ONE.shiftLeft(64) + "'");
    }
}
