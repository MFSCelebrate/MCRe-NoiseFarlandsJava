import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.MathContext;
public class ProfBD {
    static long sink;
    public static void main(String[] args) {
        java.util.Random r = new java.util.Random(5);
        BigInteger a = BigInteger.ONE;
        for (int i = 0; i < 256; i++) a = a.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        BigInteger b = BigInteger.ONE;
        for (int i = 0; i < 128; i++) b = b.shiftLeft(1).or(BigInteger.valueOf(r.nextInt(2)));
        BigDecimal da = new BigDecimal(a, 20), db = new BigDecimal(b, 20);
        MathContext mc = new MathContext(50);
        // 分解计时：bigMultiplyPowerTen + divideAndRound
        long t0 = System.nanoTime();
        for (int i = 0; i < 20000; i++) { sink = 0; da.divide(db, mc); }
        System.out.println("total BD.div: " + (System.nanoTime()-t0)/20000 + " ns/op");
        // 直接测 BigDecimal 内部等价：divideAndRound(BigInteger, BigInteger) 
        t0 = System.nanoTime();
        BigInteger daInt = a, dbInt = b;
        for (int i = 0; i < 20000; i++) {
            // 模拟 divideAndRound(rb, ys, ...) 
            BigInteger rb = daInt.multiply(BigInteger.TEN.pow(30));
            BigInteger qr[] = rb.divideAndRemainder(dbInt);
            sink += qr[0].bitLength() + qr[1].bitLength();
        }
        System.out.println("mul10^30 + divAndRem: " + (System.nanoTime()-t0)/20000 + " ns/op");
        // 纯 divideAndRemainder
        t0 = System.nanoTime();
        for (int i = 0; i < 20000; i++) { BigInteger[] qr = daInt.divideAndRemainder(dbInt); sink += qr[0].bitLength()+qr[1].bitLength(); }
        System.out.println("divideAndRemainder: " + (System.nanoTime()-t0)/20000 + " ns/op");
        System.out.println("sink=" + sink);
    }
}
