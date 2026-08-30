import net.MinecraftTools.Math._256Bit.*;
public class TestCore {
    static int pass=0, fail=0;
    static void check(String n, Object g, Object e) {
        boolean ok=g.equals(e); if(ok)pass++; else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static void checkL(String n, long g, long e) {
        boolean ok=g==e; if(ok)pass++; else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static void checkExcept(String n, Runnable r) {
        try { r.run(); System.out.println("FAIL "+n+"  no exception"); fail++; }
        catch (ArithmeticException ex) { System.out.println("PASS "+n+"  threw"); pass++; }
    }
    public static void main(String[] args) {
        Int256 M64=Int256.of(0L,0L,0L,-1L);        // 2^64-1
        Int256 P64=Int256.of(0L,0L,1L,0L);         // 2^64
        Int256 P128=Int256.of(0L,1L,0L,0L);        // 2^128
        System.out.println("── Int256 add/sub ──");
        check("(2^64-1)+1", M64.add(Int256.ONE), P64);
        check("2^64-1", P64.subtract(Int256.ONE), M64);
        check("2^128-1", P128.subtract(Int256.ONE), Int256.of(0L,0L,-1L,-1L));
        check("100+200", Int256.of(100).add(Int256.of(200)), Int256.of(300));
        check("100-250", Int256.of(100).subtract(Int256.of(250)), Int256.of(-150));
        check("MAX+1", Int256.MAX_VALUE.add(Int256.ONE), Int256.MIN_VALUE);
        System.out.println("── Int256 mul ──");
        check("(2^64-1)*2", M64.multiply(Int256.TWO), Int256.of(0L,0L,0L,-2L));
        check("(2^64-1)^2", M64.multiply(M64), Int256.of(0L,0L,-2L,1L));
        check("2^64*2^64", P64.multiply(P64), P128);
        check("2^128*2^128", P128.multiply(P128), Int256.ZERO);
        check("1000*999", Int256.of(1000).multiply(Int256.of(999)), Int256.of(999000));
        check("(-5)*6", Int256.of(-5).multiply(Int256.of(6)), Int256.of(-30));
        System.out.println("── Int256 shift ──");
        check("3<<1", Int256.of(3).shiftLeft(1), Int256.of(6));
        check("1<<64", Int256.ONE.shiftLeft(64), P64);
        check("1<<128", Int256.ONE.shiftLeft(128), P128);
        check("shl64 b非零", Int256.of(0L,5L,3L,7L).shiftLeft(64), Int256.of(5L,3L,7L,0L));
        check("6>>1", Int256.of(6).shiftRight(1), Int256.of(3));
        check("2^128>>64", P128.shiftRight(64), P64);
        check("shr64 b非零", Int256.of(0L,5L,3L,7L).shiftRight(64), Int256.of(0L,0L,5L,3L));
        check("(-2)>>1", Int256.of(-2).shiftRight(1), Int256.MINUS_ONE);
        check("2^64>>193", P64.shiftRight(193), Int256.ZERO);
        check("shr 混合 左", Int256.of(0L,5L,3L,7L).shiftLeft(1), Int256.of(0L,10L,7L,14L));
        check("shr 混合 右", Int256.of(0L,5L,3L,7L).shiftRight(1), Int256.of(0L,2L,5L,3L));
        System.out.println("── Int256 misc ──");
        checkExcept("longValue high-ones d>=0", () -> Int256.of(-1L,-1L,-1L,5L).longValue());
        checkL("longValue -1", Int256.MINUS_ONE.longValue(), -1L);
        checkL("longValue Long.MIN", Int256.of(Long.MIN_VALUE).longValue(), Long.MIN_VALUE);
        check("div 100/3", Int256.of(100).divide(Int256.of(3)), Int256.of(33));
        check("div -100/3", Int256.of(-100).divide(Int256.of(3)), Int256.of(-33));
        check("div 2^128/2^64", P128.divide(P64), P64);
        check("div 2^255-2 /2", Int256.of(0x7FFF_FFFF_FFFF_FFFFL,-1L,-1L,-2L).divide(Int256.TWO), Int256.of(0x3FFF_FFFF_FFFF_FFFFL,-1L,-1L,-1L));
        check("div 2^64-1 /3", M64.divide(Int256.of(3)), Int256.of(6148914691236517205L));
        check("rem 100%3", Int256.of(100).remainder(Int256.of(3)), Int256.ONE);
        check("neg MIN", Int256.MIN_VALUE.negate(), Int256.MIN_VALUE);
        check("neg -5", Int256.of(-5).negate(), Int256.of(5));

        UInt256 UM64=UInt256.of(0L,0L,0L,-1L);
        UInt256 UP64=UInt256.of(0L,0L,1L,0L);
        UInt256 UP128=UInt256.of(0L,1L,0L,0L);
        System.out.println("\n── UInt256 ──");
        check("add (2^64-1)+1", UM64.add(UInt256.ONE), UP64);
        check("sub 2^64-1", UP64.subtract(UInt256.ONE), UM64);
        check("mul (2^64-1)^2", UM64.multiply(UM64), UInt256.of(0L,0L,-2L,1L));
        check("mul 2^64*2^64", UP64.multiply(UP64), UP128);
        check("mul MAX*2", UInt256.MAX_VALUE.multiply(UInt256.TWO), UInt256.of(0L,-1L,-1L,-2L));
        check("shl 3<<1", UInt256.of(3).shiftLeft(1), UInt256.of(6));
        check("shl64 b非零", UInt256.of(0L,5L,3L,7L).shiftLeft(64), UInt256.of(5L,3L,7L,0L));
        check("shr 2^128>>64", UP128.shiftRight(64), UP64);
        check("shr64 b非零", UInt256.of(0L,5L,3L,7L).shiftRight(64), UInt256.of(0L,0L,5L,3L));
        check("div 100/3", UInt256.of(100).divide(UInt256.of(3)), UInt256.of(33));
        check("div MAX/3", UInt256.MAX_VALUE.divide(UInt256.of(3)), UInt256.of(0L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L));
        check("div MAX/2", UInt256.MAX_VALUE.divide(UInt256.TWO), UInt256.of(0x7FFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL));
        check("div 2^128/2^64", UP128.divide(UP64), UP64);
        check("bitLength 2^64-1", UM64.bitLength(), 64);
        System.out.println("\n==== "+pass+" passed, "+fail+" failed ====");
    }
}
