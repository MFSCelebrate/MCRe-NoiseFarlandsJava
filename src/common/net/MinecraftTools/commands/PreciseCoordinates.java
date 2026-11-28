package net.MinecraftTools.commands;

import java.math.BigInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.Entity;

/**
 * 🔧 MCRe「高精度坐标」：{@code /tp} 等命令的高精度坐标参数。
 *
 * <p>为什么不用 vanilla 的 {@link net.minecraft.commands.arguments.coordinates.Vec3Argument}：
 * 它内部按 <b>double</b> 解析 → 坐标一旦超过 2^53 就被 ulp 量化
 * （实测：传送 1e35 → 落点变成 999999999999999996863366107917975729，差 3.1e18）。
 *
 * <p>本类用<b>十进制字符串</b>解析 → 整数坐标走 {@code BigInteger} 路径**完全精确**；
 * 带小数的坐标其小数部分按 double 舍入（相对误差 ~1e-17）。
 *
 * <p>相对坐标（{@code ~} / {@code ~offset}）的基准：执行者若是启用高精度位置的玩家，
 * 则用其<b>精确位置</b>；其它实体用 double。
 */
public record PreciseCoordinates(
        java.math.BigDecimal x,
        java.math.BigDecimal y,
        java.math.BigDecimal z,
        boolean xRelative,
        boolean yRelative,
        boolean zRelative
) {

    /** X 轴是否相对。 */
    public boolean isXRelative() {
        return this.xRelative;
    }

    /** Y 轴是否相对。 */
    public boolean isYRelative() {
        return this.yRelative;
    }

    /** Z 轴是否相对。 */
    public boolean isZRelative() {
        return this.zRelative;
    }

    /**
     * 解析三个坐标 token（支持绝对十进制、科学记数法、{@code ~} 与 {@code ~offset}）。
     *
     * @param source 执行者（相对坐标的基准）
     * @param tokens 三个坐标 token
     * @return 解析结果
     * @throws com.mojang.brigadier.exceptions.CommandSyntaxException token 非法时抛出
     */
    public static PreciseCoordinates parse(final CommandSourceStack source, final String[] tokens)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (tokens.length != 3) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    net.minecraft.network.chat.Component.literal("坐标需要三个值（x y z）")).create();
        }

        final boolean[] relative = new boolean[3];
        final java.math.BigDecimal[] values = new java.math.BigDecimal[3];
        final Entity executor = source.getEntity();

        for (int axis = 0; axis < 3; axis++) {
            final String token = tokens[axis].trim();
            final int tilde = token.indexOf('~');

            if (tilde < 0) {
                values[axis] = parseDecimal(token, axis);
                relative[axis] = false;
                continue;
            }

            relative[axis] = true;
            final java.math.BigDecimal offset = token.substring(tilde + 1).isEmpty()
                    ? java.math.BigDecimal.ZERO
                    : parseDecimal(token.substring(tilde + 1), axis);

            // 基准：执行者的精确位置（玩家）或 double
            final java.math.BigDecimal base = executorBase(executor, axis);
            values[axis] = base.add(offset);
        }

        return new PreciseCoordinates(values[0], values[1], values[2], relative[0], relative[1], relative[2]);
    }

    /** 解析单个十进制 token（允许科学记数法）。 */
    private static java.math.BigDecimal parseDecimal(final String token, final int axis)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        try {
            return new java.math.BigDecimal(token);
        } catch (final NumberFormatException e) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    net.minecraft.network.chat.Component.literal("坐标无法解析: " + token)).create();
        }
    }

    /** 执行者在该轴上的基准坐标（玩家用精确位置，其它用 double）。 */
    private static java.math.BigDecimal executorBase(final Entity executor, final int axis) {
        if (executor == null) {
            return java.math.BigDecimal.ZERO;
        }

        final net.MinecraftTools.precision.PrecisePosition precise = executor.getPrecisePosition();

        if (precise != null) {
            final net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256 exact = switch (axis) {
                case 0 -> precise.exactX();
                case 1 -> precise.exactY();
                default -> precise.exactZ();
            };
            // 精确值 → 十进制（整数值走 BigInteger 精确；带小数经十进制展开）
            return new java.math.BigDecimal(exact.toExactString());
        }

        final double value = switch (axis) {
            case 0 -> executor.getX();
            case 1 -> executor.getY();
            default -> executor.getZ();
        };
        return java.math.BigDecimal.valueOf(value);
    }
}
