package net.MinecraftTools.diagnostics;

import java.util.Locale;

import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;

/**
 * 🔧 MCRe：启动期 GPU fp64 能力检测报告（大佬 2026-11-15 指派）。
 *
 * <p><b>为什么需要专门做</b>：
 * <ul>
 *   <li>GPU 加速密度链若走 f64，需要设备真的能跑 f64 shader；<b>各后端的 fp64 能力差异极大</b>
 *       （Vulkan 1.2 core feature {@code shaderFloat64} / DX12 SM5+ 原生 double / Metal f64 / GL 的
 *       {@code GL_ARB_gpu_shader_fp64} 与 GLES 的 {@code GL_EXT_shader_fp64}）。</li>
 *   <li>⚠️ <b>移动端特殊：Turnip（Vulkan→GLES 软件翻译层）会"谎报"特性</b> —— 它能把 API 版本报到 1.3/1.4，
 *       但底层是 GLES，{@code shaderFloat64} 往往不可用甚至编译期就失败 → <b>静态特性不可全信</b>，
 *       本报告对 Turnip 明确标注"需运行时编译+派发复验"。</li>
 * </ul>
 *
 * <p><b>探测方式</b>：只读查询当前<b>已实例化</b>的后端（不额外创建设备实例——避免启动开销，
 * 也避免在 Android/Turnip 上枚举/建实例触发驱动问题）。其余后端按平台标注 N/A。
 */
public final class GpuFp64Report {

    private static final String HEADER = "====================== GPU Accelerate ======================";
    private static final String FOOTER = "=============================^=============================";

    private GpuFp64Report() {
    }

    /** 生成报告文本（多行）。 */
    public static String probe(final GpuDevice device, final String gameVersion) {
        final StringBuilder sb = new StringBuilder();
        sb.append(HEADER).append('\n');
        sb.append("Platform        : ")
            .append(System.getProperty("os.name"))
            .append(' ')
            .append(System.getProperty("os.version"))
            .append(" (")
            .append(System.getProperty("os.arch"))
            .append(") / MC ")
            .append(gameVersion)
            .append('\n');

        final GpuDeviceBackend backend = device == null ? null : device.backend();

        if (backend != null) {
            backend.appendFp64Capability(sb);
        } else {
            sb.append("Active backend  : NONE (device not initialized)\n");
        }

        appendOtherBackends(sb, backend);
        sb.append(FOOTER);
        return sb.toString();
    }

    private static void appendOtherBackends(final StringBuilder sb, final GpuDeviceBackend active) {
        final String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        final String vendor = System.getProperty("java.vendor", "").toLowerCase(Locale.ROOT);
        final boolean windows = os.contains("win");
        final boolean mac = os.contains("mac") || os.contains("darwin");
        final boolean android = os.contains("android") || vendor.contains("android");
        final String activeName = active == null ? "N/A" : active.getClass().getSimpleName();
        sb.append("Other backends  : only the instantiated backend is probed (no extra device instances are created)\n");
        sb.append("  Active class  : ").append(activeName).append('\n');
        sb.append("  Metal         : ").append(mac ? "Apple platform, MCRe has no Metal backend -> N/A" : "not an Apple platform -> N/A").append('\n');
        sb.append("  DX12          : ").append(windows ? "Windows platform, MCRe has no DX12 backend -> N/A" : "not a Windows platform -> N/A").append('\n');

        if (android) {
            sb.append("  Mobile path   : official MC on Android uses OpenGL ES (no fp64 in practice);\n");
            sb.append("                   Vulkan is only available through the Turnip extension, whose\n");
            sb.append("                   feature advertisement is unreliable -> runtime probe required.\n");
        }
    }
}
