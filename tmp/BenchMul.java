import java.util.Random;
public class BenchMul {
    static long sink;
    public static void main(String[] a) {
        Random r = new Random(11);
        net.MinecraftTools.Math.DynamicAccuracy.BigInteger[] O = new net.MinecraftTools.Math.DynamicAccuracy.BigInteger[4];
        java.math.BigInteger[] J = new java.math.BigInteger[4];
        for(int i=0;i<4;i++){ O[i]=rndO(r,1024); J[i]=rndJ(r,1024); }
        for(int i=0;i<3000;i++){sink+=O[0].multiply(O[1]).bitLength(); sink+=J[0].multiply(J[1]).bitLength();}
        // 交错多轮
        long oBest=Long.MAX_VALUE, jBest=Long.MAX_VALUE;
        for(int round=0;round<5;round++){
            long t0=System.nanoTime();
            for(int i=0;i<20000;i++) sink+=O[0].multiply(O[1]).bitLength();
            oBest=Math.min(oBest,(System.nanoTime()-t0)/20000);
            t0=System.nanoTime();
            for(int i=0;i<20000;i++) sink+=J[0].multiply(J[1]).bitLength();
            jBest=Math.min(jBest,(System.nanoTime()-t0)/20000);
        }
        System.out.printf("OURS mul 1024x1024: %.1f ns/op (best)%n", (double)oBest);
        System.out.printf("JDK  mul 1024x1024: %.1f ns/op (best)%n", (double)jBest);
        System.out.println("sink="+sink);
    }
    static net.MinecraftTools.Math.DynamicAccuracy.BigInteger rndO(Random r,int b){return new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(rndB(r,b)).abs();}
    static java.math.BigInteger rndJ(Random r,int b){return new java.math.BigInteger(rndB(r,b)).abs();}
    static byte[] rndB(Random r,int b){byte[] x=new byte[(b+7)/8+1];r.nextBytes(x);return x;}
}
