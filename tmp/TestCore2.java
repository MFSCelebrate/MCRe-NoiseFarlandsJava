import net.MinecraftTools.Math._256Bit.*;
public class TestCore2 {
    static int pass=0, fail=0;
    static void check(String n, Object g, Object e) {
        boolean ok=g.equals(e); if(ok)pass++; else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static void checkExcept(String n, Runnable r) {
        try { r.run(); System.out.println("FAIL "+n+"  no exception"); fail++; }
        catch (ArithmeticException ex) { System.out.println("PASS "+n+"  threw"); pass++; }
    }
    public static void main(String[] args) {
        Int256 P64=Int256.of(0L,0L,1L,0L);
        Int256 P128=Int256.of(0L,1L,0L,0L);
        System.out.println("── Int256 shift（含负数/跨 limb）──");
        check("6>>1", Int256.of(6).shiftRight(1), Int256.of(3));
        check("(-2)>>1", Int256.of(-2).shiftRight(1), Int256.MINUS_ONE);
        check("(-2)>>2", Int256.of(-2).shiftRight(2), Int256.MINUS_ONE);
        check("(-9)>>2", Int256.of(-9).shiftRight(2), Int256.of(-3)); // floor(-2.25)=-3
        check("(-9)>>3", Int256.of(-9).shiftRight(3), Int256.of(-2)); // floor(-1.125)=-2
        check("shr64 b非零", Int256.of(0L,5L,3L,7L).shiftRight(64), Int256.of(0L,0L,5L,3L));
        check("shr65 b非零", Int256.of(0L,5L,3L,7L).shiftRight(65), Int256.of(0L,0L,2L,0x8000_0000_0000_0001L));
        check("shr1 b非零", Int256.of(0L,5L,3L,7L).shiftRight(1), Int256.of(0L,2L,0x8000_0000_0000_0001L,0x8000_0000_0000_0003L));
        check("shr2^128>>65", P128.shiftRight(65), P64.shiftRight(1));
        check("shr2^64>>193", P64.shiftRight(193), Int256.ZERO);
        check("shr129", P128.shiftRight(129), Int256.ZERO);
        check("shl1 b非零", Int256.of(0L,5L,3L,7L).shiftLeft(1), Int256.of(0L,10L,6L,14L));
        check("shl65", P64.shiftLeft(65), P128.shiftLeft(1));
        check("shl129", P64.shiftLeft(129), Int256.of(0L,0L,0L,0L).or(Int256.ONE.shiftLeft(193)));
        System.out.println("── Int256 div ──");
        check("div 100/3", Int256.of(100).divide(Int256.of(3)), Int256.of(33));
        check("div -100/3", Int256.of(-100).divide(Int256.of(3)), Int256.of(-33));
        check("div 100/-3", Int256.of(100).divide(Int256.of(-3)), Int256.of(-33));
        check("div 2^128/2^64", P128.divide(P64), P64);
        check("div 2^255-2 /2", Int256.of(0x7FFF_FFFF_FFFF_FFFFL,-1L,-1L,-2L).divide(Int256.TWO), Int256.of(0x3FFF_FFFF_FFFF_FFFFL,-1L,-1L,-1L));
        check("div 2^64-1 /3", Int256.of(0L,0L,0L,-1L).divide(Int256.of(3)), Int256.of(6148914691236517205L));
        check("div MAX/MAX", Int256.MAX_VALUE.divide(Int256.MAX_VALUE), Int256.ONE);
        check("div MAX/2", Int256.MAX_VALUE.divide(Int256.TWO), Int256.of(0x3FFF_FFFF_FFFF_FFFFL,-1L,-1L,-1L));
        check("rem 100%3", Int256.of(100).remainder(Int256.of(3)), Int256.ONE);
        System.out.println("── UInt256 shift/div ──");
        UInt256 UP64=UInt256.of(0L,0L,1L,0L), UP128=UInt256.of(0L,1L,0L,0L);
        check("Ushr64 b非零", UInt256.of(0L,5L,3L,7L).shiftRight(64), UInt256.of(0L,0L,5L,3L));
        check("Ushr65 b非零", UInt256.of(0L,5L,3L,7L).shiftRight(65), UInt256.of(0L,0L,2L,0x8000_0000_0000_0001L));
        check("Ushr 2^128>>65", UP128.shiftRight(65), UP64.shiftRight(1));
        check("Ushr (-1L as u)>>63", UInt256.of(-1L).shiftRight(63), UInt256.ONE);
        check("Ushl1 b非零", UInt256.of(0L,5L,3L,7L).shiftLeft(1), UInt256.of(0L,10L,6L,14L));
        check("Udiv 100/3", UInt256.of(100).divide(UInt256.of(3)), UInt256.of(33));
        check("Udiv MAX/2", UInt256.MAX_VALUE.divide(UInt256.TWO), UInt256.of(0x7FFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL,0xFFFF_FFFF_FFFF_FFFFL));
        check("Udiv MAX/3", UInt256.MAX_VALUE.divide(UInt256.of(3)), UInt256.of(0L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L,0x5555_5555_5555_5555L));
        check("Udiv 2^128/2^64", UP128.divide(UP64), UP64);
        System.out.println("\n==== "+pass+" passed, "+fail+" failed ====");
    }
}
