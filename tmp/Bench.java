import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;

public class Bench {
    static long now;
    static void start() { now = System.nanoTime(); }
    static double ms() { return (System.nanoTime() - now) / 1e6; }
    static volatile BigInteger sink;

    public static void main(String[] args) {
        // 构造不同位数的测试数
        BigInteger[] nums = new BigInteger[6];
        for (int bits = 0; bits < 6; bits++) {
            java.util.Random r = new java.util.Random(bits);
            BigInteger v = BigInteger.ONE;
            for (int i = 0; i < (1 << (bits + 1)); i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextBoolean()?1:0));
            if (v.signum() == 0) v = v.add(BigInteger.ONE);
            nums[bits] = v;
            System.out.println("bits=" + (1<<(bits+1)) + " len=" + v.bitLength() + " limbs=" + v.bitLength()/32);
        }
        BigInteger a = nums[3], b = nums[2], big = nums[5], small = nums[0];
        // warmup
        for (int i = 0; i < 10000; i++) { sink = a.multiply(b); }
        // add
        start(); for (int i = 0; i < 200000; i++) sink = a.add(b); System.out.printf("add     (256bit) : %8.2f ms\n", ms());
        // subtract
        start(); for (int i = 0; i < 200000; i++) sink = a.subtract(b); System.out.printf("sub     (256bit) : %8.2f ms\n", ms());
        // multiply 256x256
        start(); for (int i = 0; i < 100000; i++) sink = a.multiply(b); System.out.printf("mul     (256x256): %8.2f ms\n", ms());
        // multiply 4096x4096
        start(); for (int i = 0; i < 2000; i++) sink = big.multiply(big); System.out.printf("mul     (4096x4096): %7.2f ms\n", ms());
        // divide 256/128
        start(); for (int i = 0; i < 50000; i++) sink = a.divide(b); System.out.printf("div     (256/128): %8.2f ms\n", ms());
        // divide 4096/2048
        start(); for (int i = 0; i < 500; i++) sink = big.divide(nums[4]); System.out.printf("div     (4096/2048): %6.2f ms\n", ms());
        // toString
        start(); for (int i = 0; i < 20000; i++) sink = null; long t=System.nanoTime(); for (int i = 0; i < 20000; i++) a.toString(); System.out.printf("toString(256bit): %8.2f ms\n", ms());
        // valueOf long
        start(); for (int i = 0; i < 1000000; i++) sink = BigInteger.valueOf(i & 0xFFFF); System.out.printf("valueOf(long)  : %8.2f ms\n", ms());
        // BigDecimal mul/div
        BigDecimal da = new BigDecimal(a, 6), db = new BigDecimal(b, 6);
        MathContext mc = new MathContext(40);
        start(); for (int i = 0; i < 50000; i++) sink = null; t=System.nanoTime(); for (int i = 0; i < 50000; i++) da.multiply(db); System.out.printf("BD.multiply    : %8.2f ms\n", ms());
        start(); for (int i = 0; i < 50000; i++) sink = null; t=System.nanoTime(); for (int i = 0; i < 50000; i++) da.divide(db, mc); System.out.printf("BD.divide(mc)  : %8.2f ms\n", ms());
        System.out.println("done");
    }
}
