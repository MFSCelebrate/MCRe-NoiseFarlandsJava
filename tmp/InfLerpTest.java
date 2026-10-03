import net.minecraft.util.MathUtil;

public class InfLerpTest {
    static int pass = 0, fail = 0;

    static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] args) {
        double INF = Double.POSITIVE_INFINITY;
        double NINF = Double.NEGATIVE_INFINITY;

        // 1. Inf-Inf 反向（大佬点名的元凶场景）
        double r1 = MathUtil.lerp(0.5, INF, NINF);
        check("start=+Inf,end=-Inf -> +Inf (no NaN)", r1 == INF && !Double.isNaN(r1));

        double r2 = MathUtil.lerp(0.5, NINF, INF);
        check("start=-Inf,end=+Inf -> -Inf (no NaN)", r2 == NINF && !Double.isNaN(r2));

        // 2. 同向 Inf
        double r3 = MathUtil.lerp(0.5, INF, INF);
        check("start=+Inf,end=+Inf -> +Inf", r3 == INF && !Double.isNaN(r3));

        // 3. end=Inf、start 有限：自然传播
        double r4 = MathUtil.lerp(0.5, 1.0, INF);
        check("start=1.0,end=+Inf -> +Inf", r4 == INF && !Double.isNaN(r4));
        double r4b = MathUtil.lerp(0.5, 1.0, NINF);
        check("start=1.0,end=-Inf -> -Inf", r4b == NINF && !Double.isNaN(r4b));

        // 4. delta=0：纯 start 点（优先级最高，与原版 Mth.lerp 一致）
        double r5 = MathUtil.lerp(0.0, INF, NINF);
        check("delta=0,start=+Inf -> +Inf", r5 == INF);
        double r6 = MathUtil.lerp(0.0, 3.5, INF);
        check("delta=0,start=3.5,end=+Inf -> 3.5 (原版语义)", r6 == 3.5);

        // 5. 正常有限值：与原版 Mth.lerp 一致
        double r7 = MathUtil.lerp(0.25, 2.0, 6.0);
        check("lerp(0.25,2,6)=3.0", r7 == 3.0);
        double r8 = MathUtil.lerp(0.5, 2.0, 6.0);
        check("lerp(0.5,2,6)=4.0", r8 == 4.0);
        double r8b = MathUtil.lerp(1.0, 2.0, 6.0);
        check("lerp(1.0,2,6)=6.0", r8b == 6.0);

        // 6. lerp2/lerp3 组合（Inf 防护自动继承）
        double r9 = MathUtil.lerp2(0.5, 0.5, INF, NINF, 1.0, 2.0);
        check("lerp2 组合 Inf -> +Inf", r9 == INF && !Double.isNaN(r9));

        double r10 = MathUtil.lerp3(0.5, 0.5, 0.5, INF, NINF, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        check("lerp3 组合 Inf -> +Inf", r10 == INF && !Double.isNaN(r10));

        // 7. lerp3 全正常值
        double r11 = MathUtil.lerp3(0.5, 0.5, 0.5, 0.0, 2.0, 0.0, 2.0, 0.0, 2.0, 0.0, 2.0);
        check("lerp3 全 0/2 = 1.0", r11 == 1.0);

        // 8. clamp 的 Inf（已有防护，回归确认）
        double r12 = MathUtil.clamp(INF, -1.0, 1.0);
        check("clamp(+Inf)=+Inf (不限制无穷)", r12 == INF);
        double r13 = MathUtil.clamp(5.0, -1.0, 1.0);
        check("clamp(5,-1,1)=1.0", r13 == 1.0);
        double r14 = MathUtil.clamp(-5.0, -1.0, 1.0);
        check("clamp(-5,-1,1)=-1.0", r14 == -1.0);

        System.out.println("---");
        System.out.println("PASS " + pass + " / " + (pass + fail));
        if (fail > 0) System.exit(1);
    }
}