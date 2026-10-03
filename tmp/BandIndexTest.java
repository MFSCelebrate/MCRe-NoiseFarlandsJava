public class BandIndexTest {
    static int pass = 0, fail = 0;
    static void check(String name, boolean cond) {
        if (cond) { pass++; System.out.println("PASS " + name); }
        else { fail++; System.out.println("FAIL " + name); }
    }

    // 防护版索引（fixChunkOutOfBounds 路径）
    static int protectedIndex(int y, int offset, int len) {
        return Math.floorMod(y + offset, len); // 修复版：纯 floorMod（与 getBand 一致）
    }

    // 原版索引
    static int vanillaIndex(int y, int offset, int len) {
        return (y + offset + len) % len;
    }

    public static void main(String[] args) {
        int LEN = 192;
        double INF = Double.POSITIVE_INFINITY;

        // 1. 正常 offset（±4）：两路径一致且合法
        boolean allSame = true;
        for (int y = 0; y <= 384; y += 7) {
            for (int offset = -4; offset <= 4; offset += 2) {
                int a = protectedIndex(y, offset, LEN);
                int b = vanillaIndex(y, offset, LEN);
                if (a != b || a < 0 || a >= LEN) allSame = false;
            }
        }
        check("正常 offset 场景两路径全部一致且索引合法", allSame);

        // 2. 野值 offset（-294，实测崩溃场景 y+offset+192=-2 → % 返回 -2 越界）
        int a2 = protectedIndex(100, -294, LEN);
        check("野值 offset=-294 防护后索引合法（floorMod=-2+192=190）", a2 >= 0 && a2 < LEN);
        boolean vanillaBad = false;
        try {
            int b2 = vanillaIndex(100, -294, LEN);
            if (b2 < 0 || b2 >= LEN) vanillaBad = true;
        } catch (Exception e) { vanillaBad = true; }
        check("野值 offset=-294 原版 % 越界（崩）", vanillaBad);

        // 3. Inf 场景：round(+Inf)=Long.MAX → (int) 截断
        int offsetInf = (int) Math.round(INF * 4.0);
        int a3 = protectedIndex(50, offsetInf, LEN);
        check("Inf → (int) 截断 → 防护后索引合法", a3 >= 0 && a3 < LEN);

        // 4. NaN 场景：round(NaN)=0
        int offsetNaN = (int) Math.round(Double.NaN * 4.0);
        check("NaN → round=0 → offset=0", offsetNaN == 0);

        // 5. clamp 边界
        check("clamp(+Inf,-len,len)=+len", Math.max(-LEN, Math.min(Double.POSITIVE_INFINITY, LEN)) == LEN);
        check("clamp(-1e18,-len,len)=-len", Math.max(-LEN, Math.min(-1.0e18, LEN)) == -LEN);

        // 6. 大正 offset（+294）：原版本就是合法环形取模，防护（纯 floorMod）与原版一致
        int a6 = protectedIndex(100, 294, LEN);
        int b6 = vanillaIndex(100, 294, LEN);
        check("正 offset=+294 防护与原版一致（环形语义）", a6 == b6 && a6 >= 0 && a6 < LEN);

        System.out.println("---");
        System.out.println("PASS " + pass + " / " + (pass + fail));
        if (fail > 0) System.exit(1);
    }
}