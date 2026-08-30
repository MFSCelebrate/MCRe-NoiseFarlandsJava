import java.util.Random;
public class Bench256vsJdk {
    static long sink;
    static java.math.BigInteger mod = java.math.BigInteger.ONE.shiftLeft(256);

    public static void main(String[] a) {
        Random r = new Random(33);
        long[] al = {r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong()};
        long[] bl = {r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong()};
        net.MinecraftTools.Math._256Bit.Int256 oa = net.MinecraftTools.Math._256Bit.Int256.of(al[0],al[1],al[2],al[3]);
        net.MinecraftTools.Math._256Bit.Int256 ob = net.MinecraftTools.Math._256Bit.Int256.of(bl[0],bl[1],bl[2],bl[3]);
        net.MinecraftTools.Math._256Bit.UInt256 ua = net.MinecraftTools.Math._256Bit.UInt256.of(al[0],al[1],al[2],al[3]);
        net.MinecraftTools.Math._256Bit.UInt256 ub = net.MinecraftTools.Math._256Bit.UInt256.of(bl[0],bl[1],bl[2],bl[3]);
        // JDK: 用同一位模式的两个 256-bit 正数（把高位符号位清 0 保证为正）
        java.math.BigInteger ja = new java.math.BigInteger(1, toBytes(al));
        java.math.BigInteger jb = new java.math.BigInteger(1, toBytes(bl));
        // warmup
        for (int i=0;i<10000;i++){ sink+=oa.add(ob).hashCode(); sink+=ja.add(jb).hashCode(); sink+=ua.multiply(ub).hashCode(); }

        t("Int256.add", 2000000, () -> { sink += oa.add(ob).hashCode(); });
        t("Int256.mul", 500000,  () -> { sink += oa.multiply(ob).hashCode(); });
        t("Int256.div", 300000,  () -> { sink += oa.divide(ob).hashCode(); });
        t("UInt256.add", 2000000, () -> { sink += ua.add(ub).hashCode(); });
        t("UInt256.mul", 500000,  () -> { sink += ua.multiply(ub).hashCode(); });
        t("UInt256.div", 300000,  () -> { sink += ua.divide(ub).hashCode(); });
        t("BigInt.add", 2000000,  () -> { sink += ja.add(jb).hashCode(); });
        t("BigInt.mul", 500000,   () -> { sink += ja.multiply(jb).hashCode(); });
        t("BigInt.div", 300000,   () -> { sink += ja.divide(jb).hashCode(); });
        System.out.println("sink="+sink);
    }
    static void t(String name, int iters, Runnable fn) {
        long best = Long.MAX_VALUE;
        for (int round=0; round<4; round++) {
            long t0 = System.nanoTime();
            for (int i=0;i<iters;i++) fn.run();
            best = Math.min(best, (System.nanoTime()-t0)/iters);
        }
        System.out.printf("%-12s %8.1f ns/op%n", name, (double)best);
    }
    static byte[] toBytes(long[] v) {
        byte[] b = new byte[32];
        for (int i=0;i<4;i++) {
            long x = v[i];
            b[i*8]   = (byte)(x>>>56); b[i*8+1] = (byte)(x>>>48);
            b[i*8+2] = (byte)(x>>>40); b[i*8+3] = (byte)(x>>>32);
            b[i*8+4] = (byte)(x>>>24); b[i*8+5] = (byte)(x>>>16);
            b[i*8+6] = (byte)(x>>>8);  b[i*8+7] = (byte)x;
        }
        return b;
    }
}
