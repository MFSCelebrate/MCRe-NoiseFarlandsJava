import net.MinecraftTools.Math._256Bit.*;

public class Test256c {
    static int pass=0, fail=0;
    static void check(String n, Object g, Object e) {
        boolean ok = g.equals(e); if(ok) pass++; else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    static void checkD(String n, double g, double e) {
        double tol=1e-9*Math.max(1,Math.abs(e));
        boolean ok=Math.abs(g-e)<=tol; if(ok) pass++; else fail++;
        System.out.println((ok?"PASS ":"FAIL ")+n+"  got="+g+"  exp="+e);
    }
    public static void main(String[] args) {
        System.out.println("── Float256 非 divide/shl/shr ──");
        // 1+2 依赖 Int256.add（反），预期错
        // 但 of(1).multiply(of(1)) 依赖 Int256.mul（高半边），预期 0
        // of(1).longValue() 依赖 Int256.shr（坏），但只调 of(1) 不涉及移位
        // 直接测构造和 toString
        Float256 f1 = Float256.of(1.0);
        System.out.println("f1=" + f1 + "  double=" + f1.doubleValue() + "  long=" + f1.longValue());
        System.out.println("1+2=" + f1.add(Float256.of(2.0)));
        System.out.println("1*1=" + f1.multiply(f1).doubleValue());
        System.out.println("truncate(1.0)=" + f1.truncate());
        // 0.1+0.2
        System.out.println("0.1+0.2=" + Float256.of(0.1).add(Float256.of(0.2)).doubleValue());
        // sqrt(2)
        System.out.println("sqrt(2)=" + Float256.of(2).sqrt().doubleValue());

        System.out.println("\n── UFloat256 非 divide ──");
        UFloat256 u1 = UFloat256.of(1.0);
        System.out.println("u1=" + u1 + "  double=" + u1.doubleValue() + "  long=" + u1.longValue());
        System.out.println("1+2=" + u1.add(UFloat256.of(2.0)));
        System.out.println("1*1=" + u1.multiply(u1).doubleValue());
        System.out.println("toUInt256(1.0)=" + u1.toUInt256());
        System.out.println("truncate(1.7)=" + UFloat256.of(1.7).truncate());

        System.out.println("\n── DynamicNumber ──");
        System.out.println("a+b=" + DynamicNumber.of(100_000).add(DynamicNumber.of(Int256.ONE.shiftLeft(72))));

        System.out.println("\n==== " + pass + " passed, " + fail + " failed ====");
    }
}