import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;

public class Bench2 {
    static volatile BigInteger sink;
    static BigInteger rand(int bits) {
        java.util.Random r = new java.util.Random(bits);
        BigInteger v = BigInteger.ONE;
        for (int i = 0; i < bits; i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        return v;
    }
    static void run(String name, int iters, java.util.function.Supplier<BigInteger> fn) {
        // warmup
        for (int i = 0; i < Math.min(iters/10, 2000); i++) sink = fn.get();
        long t = System.nanoTime();
        for (int i = 0; i < iters; i++) sink = fn.get();
        double ms = (System.nanoTime() - t) / 1e6;
        System.out.printf("%-22s %8d 次  %9.2f ms  %9.1f ns/op\n", name, iters, ms, ms*1e6/iters);
    }
    public static void main(String[] args) {
        BigInteger n256a = rand(256), n256b = rand(256);
        BigInteger n1024a = rand(1024), n1024b = rand(1024);
        BigInteger n4096a = rand(4096), n4096b = rand(4096);
        System.out.println("== BigInteger 大数运算 ==");
        run("add 256+256", 200000, () -> n256a.add(n256b));
        run("add 4096+4096", 100000, () -> n4096a.add(n4096b));
        run("mul 256x256", 100000, () -> n256a.multiply(n256b));
        run("mul 1024x1024", 20000, () -> n1024a.multiply(n1024b));
        run("mul 4096x4096", 1000, () -> n4096a.multiply(n4096b));
        run("div 256/128", 100000, () -> n256a.divide(n256b.shiftRight(128)));
        run("div 1024/512", 10000, () -> n1024a.divide(n1024b.shiftRight(512)));
        run("div 4096/2048", 1000, () -> n4096a.divide(n4096b.shiftRight(2048)));
        run("toString 256", 50000, () -> BigInteger.valueOf(n256a.toString().hashCode()));
        run("toString 4096", 2000, () -> BigInteger.valueOf(n4096a.toString().hashCode()));
        run("valueOf(12345)", 1000000, () -> BigInteger.valueOf(12345));
        run("shiftLeft 4096<<100", 100000, () -> n4096a.shiftLeft(100));
        System.out.println("== BigDecimal ==");
        BigDecimal d256a = new BigDecimal(n256a, 20), d256b = new BigDecimal(n256b, 20);
        BigDecimal d4096a = new BigDecimal(n4096a, 20);
        MathContext mc = new MathContext(50);
        run("BD.mul 256", 100000, () -> { sink=null; return null; });
        long t=System.nanoTime(); for(int i=0;i<50000;i++){ sink=null; d256a.multiply(d256b);} System.out.printf("%-22s %8d 次  %9.2f ms  %9.1f ns/op\n","BD.mul 256",50000,(System.nanoTime()-t)/1e6,((System.nanoTime()-t)/1e6)*1e6/50000);
        t=System.nanoTime(); for(int i=0;i<20000;i++){ sink=null; d256a.divide(d256b, mc);} System.out.printf("%-22s %8d 次  %9.2f ms  %9.1f ns/op\n","BD.div 256(mc50)",20000,(System.nanoTime()-t)/1e6,((System.nanoTime()-t)/1e6)*1e6/20000);
        System.out.println("done");
    }
}
