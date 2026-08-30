import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
import java.util.Random;

public class BenchOurBig {
    static long sink;

    static BigInteger rand(Random r, int bits) {
        byte[] b = new byte[(bits + 7) / 8 + 1];
        r.nextBytes(b);
        return new BigInteger(b).abs();
    }

    static long run(String name, int iters, Runnable fn) {
        for (int i = 0; i < Math.min(iters / 10, 2000); i++) fn.run();
        long best = Long.MAX_VALUE;
        for (int round = 0; round < 3; round++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iters; i++) fn.run();
            best = Math.min(best, (System.nanoTime() - t0) / iters);
        }
        System.out.printf("  OURS  %-18s %9.1f ns/op\n", name, (double) best);
        return best;
    }

    public static void main(String[] args) {
        Random r = new Random(42);
        BigInteger a256 = rand(r, 256), b256 = rand(r, 256);
        BigInteger a1024 = rand(r, 1024), b1024 = rand(r, 1024);
        BigInteger a4096 = rand(r, 4096), b4096 = rand(r, 4096);
        BigInteger c256 = rand(r, 128), c1024 = rand(r, 512), c4096 = rand(r, 2048);
        System.out.println("=== DynamicAccuracy (自研) ===");
        run("add 256", 400000, () -> sink += a256.add(b256).bitLength());
        run("mul 256x256", 100000, () -> sink += a256.multiply(b256).bitLength());
        run("mul 1024x1024", 20000, () -> sink += a1024.multiply(b1024).bitLength());
        run("mul 4096x4096", 1500, () -> sink += a4096.multiply(b4096).bitLength());
        run("div 256/128", 100000, () -> sink += a256.divide(c256).bitLength());
        run("div 1024/512", 10000, () -> sink += a1024.divide(c1024).bitLength());
        run("div 4096/2048", 800, () -> sink += a4096.divide(c4096).bitLength());
        run("toString 256", 100000, () -> sink += a256.toString(10).length());
        run("toString 4096", 2000, () -> sink += a4096.toString(10).length());
        run("pow 2^10 [4096]", 3000, () -> sink += a4096.pow(10).bitLength());
        BigDecimal da = new BigDecimal(a256, 20), db = new BigDecimal(b256, 20);
        MathContext mc = new MathContext(50);
        run("BD.div 256(mc50)", 100000, () -> sink += da.divide(db, mc).precision());
        System.out.println("sink=" + sink);
    }
}
