import net.MinecraftTools.Math._256Bit.*;
public class FinalTest {
    static int pass=0, fail=0;
    static void check(String n, Object g, Object e) {
        boolean ok=g.equals(e); if(ok)pass++;else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static void checkD(String n, double g, double e) {
        double tol=1e-9*Math.max(1,Math.abs(e));
        boolean ok=Math.abs(g-e)<=tol; if(ok)pass++;else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static Float256 two70 = Float256.of(Int256.ONE.shiftLeft(70));
    public static void main(String[] args) {
        UInt256 q = UInt256.MAX_VALUE.divide(UInt256.of(3));
        UInt256 back = q.multiply(UInt256.of(3));
        UInt256 rem = UInt256.MAX_VALUE.subtract(back);
        check("MAX=3q+r r<3", rem.compareTo(UInt256.of(3)) < 0, true);
        checkD("-2*3", Float256.of(-2).multiply(Float256.of(3)).doubleValue(), -6.0);
        checkD("2^100*2^100", Float256.of(Int256.ONE.shiftLeft(100)).multiply(Float256.of(Int256.ONE.shiftLeft(100))).doubleValue(), Math.pow(2,200));
        checkD("(2^70+1)+2^70", two70.add(two70.add(Float256.ONE)).doubleValue(), Math.pow(2,70)*2+1);
        checkD("3-1", Float256.of(3).subtract(Float256.ONE).doubleValue(), 2.0);
        checkD("1-3", Float256.ONE.subtract(Float256.of(3)).doubleValue(), -2.0);
        checkD("1+2^-100", Float256.of(1.0).add(Float256.ONE.divide(Float256.of(Int256.ONE.shiftLeft(100)))).doubleValue(), 1.0 + Math.pow(2,-100));
        check("UF 1-1", UFloat256.ONE.subtract(UFloat256.ONE), UFloat256.ZERO);
        check("UF 1-2→0", UFloat256.ONE.subtract(UFloat256.TWO), UFloat256.ZERO);
        check("neg toString", Int256.of(-12345).toString(), "-12345");
        Int256 round = Int256.of(0x1234_5678_9ABC_DEF0L, 0x0FED_CBA9_8765_4321L, -1L, -2L);
        check("byte[] roundtrip", Int256.of(round.toByteArray()), round);
        Int256 x1 = Int256.of(0L, 0xDEAD_BEEF_CAFE_F00DL, 0x1234_5678_9ABC_DEF0L, -5L);
        Int256 x2 = Int256.of(0L, 0x0BAD_F00D_DEAD_BEEFL, 0x0FED_CBA9_8765_4321L, 7L);
        check("big mul vs BI", x1.multiply(x2).toBigInteger(), x1.toBigInteger().multiply(x2.toBigInteger()));
        System.out.println("\n==== "+pass+" passed, "+fail+" failed ====");
    }
}
