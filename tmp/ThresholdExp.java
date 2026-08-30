// 对照实验：不直接改库，用相同规模分别测 naive 期望曲线 vs 实际
// 我们用公开 API 反向推导：对同一对数字，若 Karatsuba 生效会明显加速
import java.util.Random;
public class ThresholdExp {
    static long sum;
    public static void main(String[] a) {
        Random r = new Random(9);
        // 测 OUR 在 512-bit 和 640-bit 的斜率
        double prev=-1;
        for (int words : new int[]{12, 16, 20, 24, 28, 32, 36}) {
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger x = rndO(r, words*32);
            net.MinecraftTools.Math.DynamicAccuracy.BigInteger y = rndO(r, words*32);
            for(int i=0;i<3000;i++) sum += x.multiply(y).bitLength();
            long best=Long.MAX_VALUE;
            for(int round=0;round<5;round++){
                long t0=System.nanoTime();
                for(int i=0;i<6000;i++) sum += x.multiply(y).bitLength();
                best=Math.min(best,(System.nanoTime()-t0)/6000);
            }
            System.out.printf("OURS words=%2d  mul=%8.1f ns%n", words, (double)best);
        }
        System.out.println("s="+sum);
    }
    static net.MinecraftTools.Math.DynamicAccuracy.BigInteger rndO(Random r,int bits){byte[] b=new byte[(bits+7)/8+1];r.nextBytes(b);return new net.MinecraftTools.Math.DynamicAccuracy.BigInteger(b).abs();}
}
