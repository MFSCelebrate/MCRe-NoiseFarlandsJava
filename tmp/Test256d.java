import net.MinecraftTools.Math._256Bit.*;
public class Test256d {
    public static void main(String[] args) {
        Float256 f1 = Float256.of(1.0);
        System.out.println("f1=" + f1 + " double=" + f1.doubleValue());
        try { System.out.println("longValue(1.0)=" + f1.longValue()); } catch (Exception e) { System.out.println("longValue(1.0) threw: " + e); }
        System.out.println("1+2=" + f1.add(Float256.of(2.0)));
        System.out.println("1*1 double=" + f1.multiply(f1).doubleValue());
        try { System.out.println("truncate(1.0)=" + f1.truncate()); } catch (Exception e) { System.out.println("truncate(1.0) threw: " + e); }
        System.out.println("0.1+0.2=" + Float256.of(0.1).add(Float256.of(0.2)).doubleValue());
        System.out.println("sqrt(2)=" + Float256.of(2).sqrt().doubleValue());
        System.out.println("1.5 exact=" + Float256.of(1.5).toExactString());
        System.out.println("---");
        UFloat256 u1 = UFloat256.of(1.0);
        System.out.println("u1=" + u1 + " double=" + u1.doubleValue());
        System.out.println("1+2=" + u1.add(UFloat256.of(2.0)));
        System.out.println("1*1 double=" + u1.multiply(u1).doubleValue());
        try { System.out.println("toUInt256(1.0)=" + u1.toUInt256()); } catch (Exception e) { System.out.println("toUInt256(1.0) threw: " + e); }
        try { System.out.println("truncate(1.7)=" + UFloat256.of(1.7).truncate()); } catch (Exception e) { System.out.println("truncate threw: " + e); }
        System.out.println("0.1+0.2=" + UFloat256.of(0.1).add(UFloat256.of(0.2)).doubleValue());
        System.out.println("---");
        System.out.println("DN a+b=" + DynamicNumber.of(100_000).add(DynamicNumber.of(Int256.ONE.shiftLeft(72))));
    }
}
