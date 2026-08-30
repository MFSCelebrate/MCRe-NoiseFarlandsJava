import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
public class BenchStr {
    static volatile int sink;
    static BigInteger rand(int bits) {
        java.util.Random r = new java.util.Random(bits);
        BigInteger v = BigInteger.ONE;
        for (int i = 0; i < bits; i++) v = v.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        return v;
    }
    public static void main(String[] args) {
        for (int bits : new int[]{32, 64, 128, 256, 512, 1024, 2048, 4096}) {
            BigInteger v = rand(bits);
            int iters = bits <= 256 ? 100000 : (bits <= 1024 ? 20000 : 3000);
            for (int i = 0; i < 3000; i++) sink = v.toString().length();
            long t = System.nanoTime();
            for (int i = 0; i < iters; i++) sink = v.toString().length();
            System.out.printf("toString %5d-bit: %9.1f ns/op\n", bits, (System.nanoTime()-t)*1e6/iters/1e3);
        }
    }
}
