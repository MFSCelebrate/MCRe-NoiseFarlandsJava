import net.MinecraftTools.Math._256Bit.*;
public class FullTest {
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
    public static void main(String[] args) {
        System.out.println("── Int256/UInt256 核心 ──");
        Int256 P64=Int256.of(0L,0L,1L,0L), P128=Int256.of(0L,1L,0L,0L);
        check("add (2^64-1)+1", Int256.of(0L,0L,0L,-1L).add(Int256.ONE), P64);
        check("sub 2^64-1", P64.subtract(Int256.ONE), Int256.of(0L,0L,0L,-1L));
        check("mul (2^64-1)*2", Int256.of(0L,0L,0L,-1L).multiply(Int256.TWO), Int256.of(0L,0L,1L,-2L));
        check("mul (2^64-1)^2", Int256.of(0L,0L,0L,-1L).multiply(Int256.of(0L,0L,0L,-1L)), Int256.of(0L,0L,-2L,1L));
        check("shl 3<<1", Int256.of(3).shiftLeft(1), Int256.of(6));
        check("shl 1<<64", Int256.ONE.shiftLeft(64), P64);
        check("shl1 b非零", Int256.of(0L,5L,3L,7L).shiftLeft(1), Int256.of(0L,10L,6L,14L));
        check("shl64 b非零", Int256.of(0L,5L,3L,7L).shiftLeft(64), Int256.of(5L,3L,7L,0L));
        check("shr 6>>1", Int256.of(6).shiftRight(1), Int256.of(3));
        check("shr (-2)>>1", Int256.of(-2).shiftRight(1), Int256.MINUS_ONE);
        check("shr64 b非零", Int256.of(0L,5L,3L,7L).shiftRight(64), Int256.of(0L,0L,5L,3L));
        check("shr1 b非零", Int256.of(0L,5L,3L,7L).shiftRight(1), Int256.of(0L,2L,0x8000_0000_0000_0001L,0x8000_0000_0000_0003L));
        check("div 100/3", Int256.of(100).divide(Int256.of(3)), Int256.of(33));
        check("div 2^128/2^64", P128.divide(P64), P64);
        check("div MAX/2", Int256.MAX_VALUE.divide(Int256.TWO), Int256.of(0x3FFF_FFFF_FFFF_FFFFL,-1L,-1L,-1L));
        check("Udiv MAX/2", UInt256.MAX_VALUE.divide(UInt256.TWO), UInt256.of(0x7FFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL));
        check("Udiv MAX/3", UInt256.MAX_VALUE.divide(UInt256.of(3)), UInt256.of(0L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L));

        System.out.println("\n── Float256 ──");
        Float256 f1=Float256.of(1.0), f2=Float256.of(2.0), f3=Float256.of(3.0), f10=Float256.of(10.0);
        check("of(1) exact", f1.toExactString(), "1");
        check("1+2", f1.add(f2), f3);
        checkD("1*1", f1.multiply(f1).doubleValue(), 1.0);
        checkD("1/3", f1.divide(f3).doubleValue(), 1.0/3.0);
        checkD("1/3*3", f1.divide(f3).multiply(f3).doubleValue(), 1.0);
        checkD("0.1+0.2", Float256.of(0.1).add(Float256.of(0.2)).doubleValue(), 0.30000000000000004);
        check("longValue(1.0)", f1.longValue(), 1L);
        check("longValue(2.5)", Float256.of(2.5).longValue(), 2L);
        check("longValue(-1.5)", Float256.of(-1.5).longValue(), -1L);
        check("truncate(1.0)", f1.truncate(), Int256.ONE);
        check("truncate(1.7)", Float256.of(1.7).truncate(), Int256.ONE);
        check("truncate(-1.7)", Float256.of(-1.7).truncate(), Int256.of(-1));
        check("floor(1.7)", Float256.of(1.7).floor(), Int256.ONE);
        check("floor(-1.7)", Float256.of(-1.7).floor(), Int256.of(-2));
        check("ceil(1.2)", Float256.of(1.2).ceil(), Int256.TWO);
        check("ceil(-1.2)", Float256.of(-1.2).ceil(), Int256.of(-1));
        check("round(2.5)", Float256.of(2.5).round(), Int256.of(3));
        checkD("sqrt(2)", Float256.of(2).sqrt().doubleValue(), Math.sqrt(2));
        checkD("2^100", Float256.of(Int256.ONE.shiftLeft(100)).doubleValue(), Math.pow(2,100));
        checkD("toUFloat256(1.5)", Float256.of(1.5).toUFloat256().doubleValue(), 1.5);
        check("truncate(2^100)", Float256.of(Int256.ONE.shiftLeft(100)).truncate(), Int256.ONE.shiftLeft(100));
        check("1.5 exact", Float256.of(1.5).toExactString(), "1.5");

        System.out.println("\n── UFloat256 ──");
        UFloat256 u1=UFloat256.of(1), u3=UFloat256.of(3);
        check("1+2", u1.add(UFloat256.of(2)), UFloat256.of(3));
        checkD("1*1", u1.multiply(u1).doubleValue(), 1.0);
        checkD("1/3", u1.divide(u3).doubleValue(), 1.0/3.0);
        checkD("1/3*3", u1.divide(u3).multiply(UFloat256.of(3)).doubleValue(), 1.0);
        checkD("0.1+0.2", UFloat256.of(0.1).add(UFloat256.of(0.2)).doubleValue(), 0.30000000000000004);
        check("toUInt256(1.0)", UFloat256.of(1.0).toUInt256(), UInt256.ONE);
        check("toUInt256(2^100)", UFloat256.of(UInt256.ONE.shiftLeft(100)).toUInt256(), UInt256.ONE.shiftLeft(100));
        check("truncate(1.7)", UFloat256.of(1.7).truncate(), UInt256.ONE);
        check("ceil(1.2)", UFloat256.of(1.2).ceil(), UInt256.TWO);
        checkD("toFloat256(1.5)", UFloat256.of(1.5).toFloat256().doubleValue(), 1.5);

        System.out.println("\n==== "+pass+" passed, "+fail+" failed ====");
    }
}
