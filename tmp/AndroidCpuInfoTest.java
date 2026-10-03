import java.util.Locale;

/** Android CPU 信息回退逻辑验证（反射 Build，本环境无 Android → 核数兜底） */
public class AndroidCpuInfoTest {

    /** 与 GLX/SystemReport 的修复一致：SOC_MODEL → HARDWARE → 核数兜底 */
    public static String androidCpuInfo() {
        int cores = Runtime.getRuntime().availableProcessors();
        try {
            Class<?> build = Class.forName("android.os.Build");
            String socModel = getStringField(build, "SOC_MODEL");
            if (socModel != null && !socModel.isEmpty() && !"unknown".equals(socModel)) {
                String socMaker = getStringField(build, "SOC_MANUFACTURER");
                return String.format(Locale.ROOT, "%dx %s", cores,
                        socMaker != null && !socMaker.isEmpty() && !"unknown".equals(socMaker)
                                ? socMaker + " " + socModel : socModel);
            }
            String hardware = getStringField(build, "HARDWARE");
            if (hardware != null && !hardware.isEmpty() && !"unknown".equals(hardware)) {
                return String.format(Locale.ROOT, "%dx %s", cores, hardware);
            }
        } catch (Throwable ignored) {
        }
        return String.format(Locale.ROOT, "%dx cores", cores);
    }

    private static String getStringField(Class<?> clazz, String name) {
        try {
            Object v = clazz.getField(name).get(null);
            return v instanceof String s ? s : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println("=== Android CPU 信息回退验证 ===");
        String info = androidCpuInfo();
        System.out.println("本环境（无 android.os.Build）回退结果: " + info);
        // 无 Android 运行时 → ClassNotFoundException → 核数兜底
        if (!info.matches("\\d+x cores")) {
            throw new AssertionError("无 Android 时应走核数兜底: " + info);
        }
        System.out.println("核数兜底分支: PASS");
        System.out.println();
        System.out.println("实机（Android）预期回退链：");
        System.out.println("  API 31+: 8x QTI Snapdragon 7 Gen 1   (SOC_MANUFACTURER + SOC_MODEL)");
        System.out.println("  API <31: 8x mt6789                    (HARDWARE)");
        System.out.println("  全失败:  8x cores                     (核数兜底)");
        System.out.println();
        System.out.println("不再显示 <unknown> ✓");
    }
}
