public class WhichPath {
    // 用 public API 测各规模乘法，看 JDK 25 在 16 ints 是否已上 Karatsuba
    // Karatsuba 曲线：时间-规模对数斜率 ~1.58；naive ~2.0
    public static void main(String[] a) {
        java.util.Random r = new java.util.Random(3);
        for (int words : new int[]{8, 12, 16, 20, 24, 32, 40}) {
            int bits = words * 32;
            java.math.BigInteger x = rndJ(r, bits), y = rndJ(r, bits);
            for (int i=0;i<2000;i++){ sum += x.multiply(y).bitLength(); }
            long best=Long.MAX_VALUE;
            for (int round=0;round<5;round++){
                long t0=System.nanoTime();
                for (int i=0;i<4000;i++) sum += x.multiply(y).bitLength();
                best=Math.min(best,(System.nanoTime()-t0)/4000);
            }
            System.out.printf("JDK words=%2d bits=%4d  mul=%8.1f ns%n", words, bits, (double)best);
        }
        System.out.println("s="+sum);
    }
    static long sum;
    static java.math.BigInteger rndJ(java.util.Random r,int bits){byte[] b=new byte[(bits+7)/8+1];r.nextBytes(b);return new java.math.BigInteger(b).abs();}
}
