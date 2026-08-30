import java.util.Random;
public class BenchMul2 {
    static long sink;
    public static void main(String[] a) {
        Random r = new Random(11);
        // 1024-bit = 32 ints；256-bit = 8 ints
        for (int bits : new int[]{256, 512, 1024, 2048}) {
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger oa = rndO(r,bits), ob = rndO(r,bits);
            java.math.BigInteger ja = rndJ(r,bits), jb = rndJ(r,bits);
            for(int i=0;i<2000;i++){sink+=oa.multiply(ob).bitLength(); sink+=ja.multiply(jb).bitLength();}
            long oBest=Long.MAX_VALUE, jBest=Long.MAX_VALUE;
            for(int round=0;round<5;round++){
                long t0=System.nanoTime();
                for(int i=0;i<8000;i++) sink+=oa.multiply(ob).bitLength();
                oBest=Math.min(oBest,(System.nanoTime()-t0)/8000);
                t0=System.nanoTime();
                for(int i=0;i<8000;i++) sink+=ja.multiply(jb).bitLength();
                jBest=Math.min(jBest,(System.nanoTime()-t0)/8000);
            }
            System.out.printf("bits=%5d  OURS=%7.1f  JDK=%7.1f  ratio=%.2f%n", bits, (double)oBest, (double)jBest, oBest/(double)jBest);
        }
        System.out.println("sink="+sink);
    }
    static net.MinecraftTools.Math.DynamicAccuracy.BigInteger rndO(Random r,int b){return new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(rndB(r,b)).abs();}
    static java.math.BigInteger rndJ(Random r,int b){return new java.math.BigInteger(rndB(r,b)).abs();}
    static byte[] rndB(Random r,int b){byte[] x=new byte[(b+7)/8+1];r.nextBytes(x);return x;}
}
