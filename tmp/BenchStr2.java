import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class BenchStr2 {
    static long sink2;
    static BigInteger rand(int bits) {
        java.util.Random r = new java.util.Random(bits);
        BigInteger v = BigInteger.ONE;
        for (int i = 0; i < bits; i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        return v;
    }
    static void bench(String name, int bits, int iters) {
        BigInteger v = rand(bits);
        long sum = 0;
        for (int i = 0; i < 5000; i++) sum += v.toString().length();
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) sum += v.toString().length();
        long t1 = System.nanoTime();
        sink2 += sum;
        System.out.printf("%-14s %5d-bit: %8.1f ns/op\n", name, bits, (t1-t0)*1.0/iters);
    }
    public static void main(String[] args) {
        bench("toString", 32, 200000);
        bench("toString", 64, 200000);
        bench("toString", 128, 100000);
        bench("toString", 256, 100000);
        bench("toString", 512, 30000);
        bench("toString", 1024, 10000);
        bench("toString", 2048, 3000);
        bench("toString", 4096, 1000);
    }
}
