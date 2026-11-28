package net.MinecraftTools.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;

/**
 * 🔧 MCRe「高精度坐标参数」：把 {@code x y z} 三个坐标当<b>字符串</b>整体吃下（greedyString），
 * 再由 {@link PreciseCoordinates} 用十进制精确解析。
 *
 * <p>对比 vanilla 的 {@code Vec3Argument.vec3()}：后者内部按 double 解析 → 坐标超过 2^53 就被 ulp 量化
 * （实测传送 1e35 → 落点 999999999999999996863366107917975729，差 3.1e18）。
 *
 * <p>参数名保持 {@code location} 不变 → 命令用法提示的参数名不变（只是类型显示由 Vec3 变 String）。
 */
public final class PreciseVec3Argument {

    private PreciseVec3Argument() {
    }

    /** 取出并解析高精度坐标。 */
    public static PreciseCoordinates getCoordinates(final CommandContext<CommandSourceStack> context, final String name)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final String raw = StringArgumentType.getString(context, name).trim();
        final String[] tokens = raw.isEmpty() ? new String[0] : raw.split("\\s+");
        return PreciseCoordinates.parse(context.getSource(), tokens);
    }
}
