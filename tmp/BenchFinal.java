import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
public class BenchFinal {
    static long sink;
    static BigInteger rand(int bits) {
        java.util.Random r = new java.util.Random(bits);
        BigInteger v = BigInteger.ONE;
        for (int i = 0; i < bits; i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        return v;
    }
    static long bestT = Long.MAX_VALUE;
    static void bench(String name, int iters, Runnable fn) {
        bestT = Long.MAX_VALUE;
        for (int round = 0; round < 5; round++) {
            fn.run(); // warmup
            long t0 = System.nanoTime();
            fn.run();
            long t = (System.nanoTime()-t0)/iters;
            bestT = Math.min(bestT, t);
        }
        System.out.printf("%-18s %9.1f ns/op\n", name, (double) bestT);
    }
    public static void main(String[] args) {
        BigInteger n256 = rand(256), m128 = rand(128), n1024 = rand(1024), n4096 = rand(4096);
        int i1 = 200000, i2 = 50000;
        bench("add 256", i1, () -> { for (int i=0;i<i1;i++) sink += n256.add(m128).bitLength(); });
        bench("mul 256x128", i1, () -> { for (int i=0;i<i1;i++) sink += n256.multiply(m128).bitLength(); });
        bench("mul 256x256", i2, () -> { for (int i=0;i<i2;i++) sink += n256.multiply(n256).bitLength(); });
        bench("div 256/128", i1, () -> { for (int i=0;i<i1;i++) sink += n256.divide(m128).bitLength(); });
        bench("toString 256", i2, () -> { for (int i=0;i<i2;i++) sink += n256.toString().length(); });
        bench("toString 1024", 10000, () -> { for (int i=0;i<10000;i++) sink += n1024.toString().length(); });
        bench("toString 4096", 2000, () -> { for (int i=0;i<2000;i++) sink += n4096.toString().length(); });
        BigDecimal da = new BigDecimal(n256, 20), db = new BigDecimal(m128, 20);
        MathContext mc = new MathContext(50);
        bench("BD.div 256(mc50)", i2, () -> { for (int i=0;i<i2;i++) sink += da.divide(db, mc).precision(); });
        bench("BD.mul 256", i2, () -> { for (int i=0;i<i2;i++) sink += da.multiply(db).precision(); });
        System.out.println("sink=" + sink);
    }
}
