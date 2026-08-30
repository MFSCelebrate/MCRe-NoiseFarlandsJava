import net.MinecraftTools.Math._256Bit.*;

public class Test256 {
    static int pass = 0, fail = 0;

    static void check(String name, Object got, Object exp) {
        boolean ok = got.equals(exp);
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + "  exp=" + exp);
    }
    static void checkD(String name, double got, double exp) {
        double tol = 1e-9 * Math.max(1.0, Math.abs(exp));
        boolean ok = Math.abs(got - exp) <= tol;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + "  got=" + got + "  exp=" + exp);
    }
    static void checkExcept(String name, Runnable r) {
        try { r.run(); System.out.println("FAIL " + name + "  no exception thrown"); fail++; }
        catch (ArithmeticException e) { System.out.println("PASS " + name + "  threw ArithmeticException"); pass++; }
    }

    public static void main(String[] args) {
        // ══════════ Int256 核心 ══════════
        Int256 M64 = Int256.of(0L, 0L, 0L, -1L);          // 2^64-1
        Int256 two64 = Int256.of(0L, 0L, 1L, 0L);         // 2^64
        System.out.println("── Int256 ──");
        check("add (2^64-1)+1", M64.add(Int256.ONE), two64);
        check("sub 2^64-1", two64.subtract(Int256.ONE), M64);
        check("mul (2^64-1)*2", M64.multiply(Int256.TWO), Int256.of(0L, 0L, 0L, -2L));
        check("mul (2^64-1)^2", M64.multiply(M64), Int256.of(0L, 0L, -2L, 1L));
        check("mul 2^64*2^64", two64.multiply(two64), Int256.of(0L, 1L, 0L, 0L));
        check("shl 3<<1", Int256.of(3).shiftLeft(1), Int256.of(6));
        check("shl 1<<64", Int256.ONE.shiftLeft(64), two64);
        check("shl 1<<128", Int256.ONE.shiftLeft(128), Int256.of(0L, 0L, 1L, 0L));
        check("shl 1<<255", Int256.ONE.shiftLeft(255), Int256.of(0x8000_0000_0000_0000L, 0L, 0L, 0L));
        check("shr 6>>1", Int256.of(6).shiftRight(1), Int256.of(3));
        check("shr 2^64>>64", two64.shiftRight(64), Int256.ONE);
        check("shr (-2)>>1", Int256.of(-2).shiftRight(1), Int256.MINUS_ONE);
        check("shr 2^128>>64", Int256.of(0L, 0L, 1L, 0L).shiftRight(64), two64);
        check("neg MIN_VALUE", Int256.MIN_VALUE.negate(), Int256.MIN_VALUE);
        check("bitLength MIN", Int256.MIN_VALUE.bitLength(), 255);
        check("bitLength -1", Int256.MINUS_ONE.bitLength(), 0);
        check("bitLength -2", Int256.of(-2).bitLength(), 1);
        checkExcept("longValue high-ones d>=0", () -> Int256.of(-1L, -1L, -1L, 5L).longValue());
        check("longValue -1", Int256.MINUS_ONE.longValue(), -1L);
        check("longValue Long.MIN", Int256.of(Long.MIN_VALUE).longValue(), Long.MIN_VALUE);
        check("div 100/3", Int256.of(100).divide(Int256.of(3)), Int256.of(33));
        check("div 1000/7", Int256.of(1000).divide(Int256.of(7)), Int256.of(142));
        check("div -100/3", Int256.of(-100).divide(Int256.of(3)), Int256.of(-33));
        check("div 2^128/2^64", Int256.of(0L,0L,1L,0L).divide(two64), two64);
        check("div 2^64-1 / 3", M64.divide(Int256.of(3)), Int256.of(6148914691236517205L));
        check("rem 100%3", Int256.of(100).remainder(Int256.of(3)), Int256.ONE);

        // ══════════ UInt256 核心 ══════════
        UInt256 UM64 = UInt256.of(0L, 0L, 0L, -1L);
        UInt256 Utwo64 = UInt256.of(0L, 0L, 1L, 0L);
        System.out.println("\n── UInt256 ──");
        check("add (2^64-1)+1", UM64.add(UInt256.ONE), Utwo64);
        check("sub 2^64-1", Utwo64.subtract(UInt256.ONE), UM64);
        check("mul (2^64-1)*2", UM64.multiply(UInt256.TWO), UInt256.of(0L, 0L, 0L, -2L));
        check("mul (2^64-1)^2", UM64.multiply(UM64), UInt256.of(0L, 0L, -2L, 1L));
        check("mul 2^64*2^64", Utwo64.multiply(Utwo64), UInt256.of(0L, 1L, 0L, 0L));
        check("shl 3<<1", UInt256.of(3).shiftLeft(1), UInt256.of(6));
        check("shl 1<<64", UInt256.ONE.shiftLeft(64), Utwo64);
        check("shr 2^64>>64", Utwo64.shiftRight(64), UInt256.ONE);
        check("shr 2^128>>64", UInt256.of(0L,0L,1L,0L).shiftRight(64), Utwo64);
        check("bitLength 2^64-1", UM64.bitLength(), 64);
        check("getLowestSetBit 2^64", Utwo64.getLowestSetBit(), 64);
        check("maskBelow 70", UInt256.MAX_VALUE.maskBelow(70), UInt256.of(0L,0L,0L,-1L).or(UInt256.of(0L,0L,1L,0L)).and(UInt256.of(0L, 0L, 3L, -1L)));
        check("div 100/3", UInt256.of(100).divide(UInt256.of(3)), UInt256.of(33));
        check("div 1000/7", UInt256.of(1000).divide(UInt256.of(7)), UInt256.of(142));
        check("div 2^128/2^64", UInt256.of(0L,0L,1L,0L).divide(Utwo64), Utwo64);
        check("div MAX/3", UInt256.MAX_VALUE.divide(UInt256.of(3)), UInt256.of(0L, 0x5555_5555_5555_5555L, 0x5555_5555_5555_5555L, 0x5555_5555_5555_5555L));

        // ══════════ Float256 ══════════
        System.out.println("\n── Float256 ──");
        Float256 f1 = Float256.of(1.0), f2 = Float256.of(2.0), f3 = Float256.of(3.0);
        check("1+2", f1.add(f2), f3);
        checkD("1*1", f1.multiply(f1).doubleValue(), 1.0);
        checkD("1/3", f1.divide(f3).doubleValue(), 1.0/3.0);
        checkD("1/3*3", f1.divide(f3).multiply(f3).doubleValue(), 1.0);
        checkD("0.1+0.2", Float256.of(0.1).add(Float256.of(0.2)).doubleValue(), 0.30000000000000004);
        check("1.5 exact", Float256.of(1.5).toExactString(), "1.5");
        check("0.1 exact", Float256.of(0.1).toExactString(), "0.1000000000000000055511151231257827021181583404541015625");
        check("longValue(1.0)", f1.longValue(), 1L);
        check("longValue(2.5)", Float256.of(2.5).longValue(), 2L);
        check("truncate(1.0)", f1.truncate(), Int256.ONE);
        check("truncate(1.7)", Float256.of(1.7).truncate(), Int256.ONE);
        check("floor(1.7)", Float256.of(1.7).floor(), Int256.ONE);
        check("floor(-1.7)", Float256.of(-1.7).floor(), Int256.of(-2));
        check("ceil(1.2)", Float256.of(1.2).ceil(), Int256.TWO);
        check("ceil(-1.2)", Float256.of(-1.2).ceil(), Int256.of(-1));
        check("round(2.5)", Float256.of(2.5).round(), Int256.of(3));
        checkD("sqrt(2)", Float256.of(2).sqrt().doubleValue(), Math.sqrt(2));
        checkD("2^100", Float256.of(Int256.ONE.shiftLeft(100)).doubleValue(), Math.pow(2, 100));
        checkD("toUFloat256(1.5)", Float256.of(1.5).toUFloat256().doubleValue(), 1.5);

        // ══════════ UFloat256 ══════════
        System.out.println("\n── UFloat256 ──");
        UFloat256 u1 = UFloat256.of(1), u3 = UFloat256.of(3);
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

        // ══════════ DynamicNumber ══════════
        System.out.println("\n── DynamicNumber ──");
        check("of(0.1)+of(0.2)", DynamicNumber.of(0.1).add(DynamicNumber.of(0.2)), DynamicNumber.of(0.30000000000000004));
        checkD("b=a+2^72", DynamicNumber.of(100_000).add(DynamicNumber.of(Int256.ONE.shiftLeft(72))).doubleValue(), 100_000.0 + Math.pow(2,72));

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
    }
}
