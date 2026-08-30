import java.util.Random;
public class Bench256vsJdk2 {
    static volatile long sink; // volatile 防消除
    static final int N = 16;
    static long[] ax, bx;

    public static void main(String[] a) {
        Random r = new Random(33);
        ax = new long[N*4]; bx = new long[N*4];
        for (int i=0;i<N*4;i++){ ax[i]=r.nextLong(); bx[i]=r.nextLong(); }
        net.MinecraftTools.Math._256Bit.Int256[] oa = new net.MinecraftTools.Math._256Bit.Int256[N];
        net.MinecraftTools.Math._256Bit.Int256[] ob = new net.MinecraftTools.Math._256Bit.Int256[N];
        net.MinecraftTools.Math._256Bit.UInt256[] ua = new net.MinecraftTools.Math._256Bit.UInt256[N];
        net.MinecraftTools.Math._256Bit.UInt256[] ub = new net.MinecraftTools.Math._256Bit.UInt256[N];
        java.math.BigInteger[] ja = new java.math.BigInteger[N];
        java.math.BigInteger[] jb = new java.math.BigInteger[N];
        for (int i=0;i<N;i++){
            oa[i]=net.MinecraftTools.Math._256Bit.Int256.of(ax[i*4],ax[i*4+1],ax[i*4+2],ax[i*4+3]);
            ob[i]=net.MinecraftTools.Math._256Bit.Int256.of(bx[i*4],bx[i*4+1],bx[i*4+2],bx[i*4+3]);
            ua[i]=net.MinecraftTools.Math._256Bit.UInt256.of(ax[i*4],ax[i*4+1],ax[i*4+2],ax[i*4+3]);
            ub[i]=net.MinecraftTools.Math._256Bit.UInt256.of(bx[i*4],bx[i*4+1],bx[i*4+2],bx[i*4+3]);
            ja[i]=new java.math.BigInteger(1,toBytes(ax,i*4));
            jb[i]=new java.math.BigInteger(1,toBytes(bx,i*4));
        }
        // warmup
        for (int k=0;k<20000;k++){ int i=k&15; sink+=oa[i].add(ob[i]).hashCode(); sink+=ja[i].add(jb[i]).hashCode(); sink+=ua[i].multiply(ub[i]).hashCode(); }

        t("Int256.add ", 2000000, i -> oa[i&15].add(ob[i&15]).hashCode());
        t("Int256.mul ", 800000,  i -> oa[i&15].multiply(ob[i&15]).hashCode());
        t("Int256.div ", 300000,  i -> oa[i&15].divide(ob[i&15]).hashCode());
        t("UInt256.add", 2000000, i -> ua[i&15].add(ub[i&15]).hashCode());
        t("UInt256.mul", 800000,  i -> ua[i&15].multiply(ub[i&15]).hashCode());
        t("UInt256.div", 300000,  i -> ua[i&15].divide(ub[i&15]).hashCode());
        t("BigInt.add ", 2000000, i -> ja[i&15].add(jb[i&15]).hashCode());
        t("BigInt.mul ", 800000,  i -> ja[i&15].multiply(jb[i&15]).hashCode());
        t("BigInt.div ", 300000,  i -> ja[i&15].divide(jb[i&15]).hashCode());
        System.out.println("sink="+sink);
    }
    interface Op { long run(int i); }
    static void t(String name, int iters, Op op) {
        long best = Long.MAX_VALUE;
        for (int round=0; round<4; round++) {
            long t0 = System.nanoTime();
            long acc = 0;
            for (int i=0;i<iters;i++) acc += op.run(i);
            long dt = (System.nanoTime()-t0)/iters;
            sink += acc;
            best = Math.min(best, dt);
        }
        System.out.printf("%-12s %8.1f ns/op%n", name, (double)best);
    }
    static byte[] toBytes(long[] v, int off) {
        byte[] b = new byte[32];
        for (int i=0;i<4;i++) { long x=v[off+i]; int o=i*8;
            b[o]=(byte)(x>>>56); b[o+1]=(byte)(x>>>48); b[o+2]=(byte)(x>>>40); b[o+3]=(byte)(x>>>32);
            b[o+4]=(byte)(x>>>24); b[o+5]=(byte)(x>>>16); b[o+6]=(byte)(x>>>8); b[o+7]=(byte)x; }
        return b;
    }
}
