import net.MinecraftTools.Math._256Bit.*;
public class FloatRand {
    static int pass=0, fail=0;
    public static void main(String[] args) {
        java.util.Random rnd = new java.util.Random(99);
        // Float256 随机对照（Float256 精度 176bit 远超 double，结果应非常接近）
        int checked=0;
        for (int t = 0; t < 2000; t++) {
            double a = (rnd.nextDouble()-0.5)*1e6;
            double b = (rnd.nextDouble()-0.5)*1e6;
            if (a == 0 || b == 0 || Double.isNaN(a)||Double.isNaN(b)||Double.isInfinite(a)||Double.isInfinite(b)) continue;
            Float256 fa = Float256.of(a), fb = Float256.of(b);
            double sum = fa.add(fb).doubleValue(), expSum = a+b;
            double mul = fa.multiply(fb).doubleValue(), expMul = a*b;
            double div = fa.divide(fb).doubleValue(), expDiv = a/b;
            double sub = fa.subtract(fb).doubleValue(), expSub = a-b;
            if (Math.abs(sum-expSum) < 1e-9*Math.max(1,Math.abs(expSum))) pass++; else { fail++; if(fail<5) System.out.println("add FAIL "+a+"+"+b+" got="+sum+" exp="+expSum); }
            if (Math.abs(mul-expMul) < 1e-9*Math.max(1,Math.abs(expMul))) pass++; else { fail++; if(fail<5) System.out.println("mul FAIL "+a+"*"+b+" got="+mul+" exp="+expMul); }
            if (Math.abs(div-expDiv) < 1e-9*Math.max(1,Math.abs(expDiv))) pass++; else { fail++; if(fail<5) System.out.println("div FAIL "+a+"/"+b+" got="+div+" exp="+expDiv); }
            if (Math.abs(sub-expSub) < 1e-9*Math.max(1,Math.abs(expSub))) pass++; else { fail++; if(fail<5) System.out.println("sub FAIL "+a+"-"+b+" got="+sub+" exp="+expSub); }
            checked++;
        }
        System.out.println("Float256 随机对照 " + checked + " 组: " + pass + " pass, " + fail + " fail");

        // UFloat256 随机对照（正数）
        pass=0; fail=0; checked=0;
        for (int t = 0; t < 2000; t++) {
            double a = rnd.nextDouble()*1e6;
            double b = rnd.nextDouble()*1e6;
            if (a == 0 || b == 0) continue;
            UFloat256 fa = UFloat256.of(a), fb = UFloat256.of(b);
            double sum = fa.add(fb).doubleValue(), expSum = a+b;
            double mul = fa.multiply(fb).doubleValue(), expMul = a*b;
            double div = fa.divide(fb).doubleValue(), expDiv = a/b;
            if (Math.abs(sum-expSum) < 1e-9*Math.max(1,Math.abs(expSum))) pass++; else { fail++; if(fail<5) System.out.println("Uadd FAIL"); }
            if (Math.abs(mul-expMul) < 1e-9*Math.max(1,Math.abs(expMul))) pass++; else { fail++; if(fail<5) System.out.println("Umul FAIL "+a+"*"+b+" got="+mul+" exp="+expMul); }
            if (Math.abs(div-expDiv) < 1e-9*Math.max(1,Math.abs(expDiv))) pass++; else { fail++; if(fail<5) System.out.println("Udiv FAIL"); }
            checked++;
        }
        System.out.println("UFloat256 随机对照 " + checked + " 组: " + pass + " pass, " + fail + " fail");

        // DynamicNumber 集成
        pass=0; fail=0;
        DynamicNumber d1 = DynamicNumber.of(0.1), d2 = DynamicNumber.of(0.2);
        DynamicNumber ds = d1.add(d2);
        if (Math.abs(ds.doubleValue()-0.30000000000000004) < 1e-15) pass++; else fail++;
        DynamicNumber di = DynamicNumber.of(Int256.ONE.shiftLeft(100));
        if (di.multiply(di).doubleValue() == Math.pow(2,200)) pass++; else fail++;
        DynamicNumber dd = DynamicNumber.of(1.0).divide(DynamicNumber.of(3.0));
        if (Math.abs(dd.doubleValue()-1.0/3.0) < 1e-15) pass++; else fail++;
        System.out.println("DynamicNumber: " + pass + " pass, " + fail + " fail");
    }
}
