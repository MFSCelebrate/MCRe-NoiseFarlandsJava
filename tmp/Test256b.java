import net.MinecraftTools.Math._256Bit.*;

public class Test256b {
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
        try { r.run(); System.out.println("FAIL " + name + "  no exception"); fail++; }
        catch (ArithmeticException e) { System.out.println("PASS " + name + "  threw"); pass++; }
    }

    public static void main(String[] args) {
        Int256 P128 = Int256.of(0L, 1L, 0L, 0L);      // 2^128
        Int256 P64 = Int256.of(0L, 0L, 1L, 0L);       // 2^64
        System.out.println("── shift b-遮蔽 专项（b 字段非零）──");
        Int256 bx5 = Int256.of(0L, 5L, 3L, 7L);       // 5·2^128 + 3·2^64 + 7
        check("shl64 字段b非零", bx5.shiftLeft(64), Int256.of(5L, 3L, 7L, 0L));
        check("shr64 字段b非零", bx5.shiftRight(64), Int256.of(0L, 0L, 5L, 3L));
        check("shl1 混合", bx5.shiftLeft(1), Int256.of(0L, 10L, 7L, 14L));
        check("shr1 混合", bx5.shiftRight(1), Int256.of(0L, 2L, 5L, 3L));
        check("shl129", P64.shiftLeft(129), Int256.of(0L, 0L, 0L, 0L)); // 2^64<<129=2^193 → 0？不！2^193 超 255? no 193<256 → (2^62,0,0,0)
        System.out.println("   shl129 got=" + P64.shiftLeft(129));
        check("shr193", P128.shiftRight(193), Int256.ZERO);

        System.out.println("\n── UInt256 非 divide ──");
        UInt256 UM64 = UInt256.of(0L, 0L, 0L, -1L);
        UInt256 Utwo64 = UInt256.of(0L, 0L, 1L, 0L);
        check("add (2^64-1)+1", UM64.add(UInt256.ONE), Utwo64);
        check("sub 2^64-1", Utwo64.subtract(UInt256.ONE), UM64);
        check("mul (2^64-1)*2", UM64.multiply(UInt256.TWO), UInt256.of(0L, 0L, 0L, -2L));
        check("mul (2^64-1)^2", UM64.multiply(UM64), UInt256.of(0L, 0L, -2L, 1L));
        check("mul 2^64*2^64", Utwo64.multiply(Utwo64), UInt256.of(0L, 1L, 0L, 0L));
        check("shl 3<<1", UInt256.of(3).shiftLeft(1), UInt256.of(6));
        check("shr 2^128>>64", UInt256.of(0L,1L,0L,0L).shiftRight(64), Utwo64);
        UInt256 ubx5 = UInt256.of(0L, 5L, 3L, 7L);
        check("Ushl64 b非零", ubx5.shiftLeft(64), UInt256.of(5L, 3L, 7L, 0L));
        check("Ushr64 b非零", ubx5.shiftRight(64), UInt256.of(0L, 0L, 5L, 3L));
        check("maskBelow 70", UInt256.MAX_VALUE.maskBelow(70), UInt256.of(0L, 0L, 0L, -1L).or(UInt256.of(0L, 0L, 3L, -1L)));
        check("maskBelow 129", UInt256.MAX_VALUE.maskBelow(129), UInt256.of(0L, 0L, -1L, -1L).or(UInt256.of(0L, 1L, 0L, 0L)).subtract(UInt256.of(0L, 1L, 0L, 0L)).or(UInt256.of(0L, 1L, 0L, 0L)));
        check("bitLength 2^64-1", UM64.bitLength(), 64);
        check("getLowestSetBit 2^64", Utwo64.getLowestSetBit(), 64);

        System.out.println("\n── Float256 非 divide ──");
        Float256 f1 = Float256.of(1.0), f2 = Float256.of(2.0), f3 = Float256.of(3.0);
        check("1+2", f1.add(f2), f3);
        checkD("1*1", f1.multiply(f1).doubleValue(), 1.0);
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
        check("truncate(2^100)", Float256.of(Int256.ONE.shiftLeft(100)).truncate(), Int256.ONE.shiftLeft(100));
        checkD("sqrt(2)", Float256.of(2).sqrt().doubleValue(), Math.sqrt(2));
        checkD("2^100", Float256.of(Int256.ONE.shiftLeft(100)).doubleValue(), Math.pow(2, 100));
        checkD("toUFloat256(1.5)", Float256.of(1.5).toUFloat256().doubleValue(), 1.5);

        System.out.println("\n── UFloat256 非 divide ──");
        UFloat256 u1 = UFloat256.of(1);
        check("1+2", u1.add(UFloat256.of(2)), UFloat256.of(3));
        checkD("1*1", u1.multiply(u1).doubleValue(), 1.0);
        checkD("0.1+0.2", UFloat256.of(0.1).add(UFloat256.of(0.2)).doubleValue(), 0.30000000000000004);
        check("toUInt256(1.0)", UFloat256.of(1.0).toUInt256(), UInt256.ONE);
        check("toUInt256(2^100)", UFloat256.of(UInt256.ONE.shiftLeft(100)).toUInt256(), UInt256.ONE.shiftLeft(100));
        check("truncate(1.7)", UFloat256.of(1.7).truncate(), UInt256.ONE);
        check("ceil(1.2)", UFloat256.of(1.2).ceil(), UInt256.TWO);
        checkD("toFloat256(1.5)", UFloat256.of(1.5).toFloat256().doubleValue(), 1.5);

        System.out.println("\n── DynamicNumber ──");
        checkD("b=a+2^72", DynamicNumber.of(100_000).add(DynamicNumber.of(Int256.ONE.shiftLeft(72))).doubleValue(), 100_000.0 + Math.pow(2,72));

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
    }
}
