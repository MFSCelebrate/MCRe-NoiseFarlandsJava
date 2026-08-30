import java.util.Random;
public class BenchSquare {
    static long sink;
    public static void main(String[] a) {
        Random r = new Random(7);
        net.MinecraftTools.Math.DynamicAccuracy.BigInteger oa = rndO(r,1024);
        java.math.BigInteger ja = rndJ(r,1024);
        for(int i=0;i<3000;i++){sink+=oa.multiply(oa).bitLength(); sink+=ja.multiply(ja).bitLength();}
        long t0=System.nanoTime();
        for(int i=0;i<30000;i++) sink+=oa.multiply(oa).bitLength();
        long t1=System.nanoTime();
        System.out.printf("OURS square 1024: %.1f ns/op%n", (t1-t0)/30000.0);
        t0=System.nanoTime();
        for(int i=0;i<30000;i++) sink+=ja.multiply(ja).bitLength();
        t1=System.nanoTime();
        System.out.printf("JDK  square 1024: %.1f ns/op%n", (t1-t0)/30000.0);
        t0=System.nanoTime();
        for(int i=0;i<30000;i++) sink+=oa.multiply(oa).bitLength();
        t1=System.nanoTime();
        System.out.printf("OURS square 1024 (2nd): %.1f ns/op%n", (t1-t0)/30000.0);
        System.out.println("sink="+sink);
    }
    static net.MinecraftTools.Math.DynamicAccuracy.BigInteger rndO(Random r,int b){return new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(rndB(r,b)).abs();}
    static java.math.BigInteger rndJ(Random r,int b){return new java.math.BigInteger(rndB(r,b)).abs();}
    static byte[] rndB(Random r,int b){byte[] x=new byte[(b+7)/8+1];r.nextBytes(x);return x;}
}
