package net.MinecraftTools.terrain.pipeline.mcbedrockgen.noise;

/*
 * 🔧 MCRe MCBedrockGenPipeline 噪声底层副本（2026-10-24 快照）。
 * 从主线 net.minecraft.world.level.levelgen.synth 完整复制——含全部改版魔改
 * （BedrockMode wrap/wrapExact、expandSinglePrecision、exact 精确路径、WorldReposition 接线）。
 * 用途：主线柏林噪声底层即将大改，本副本锁定改版现状语义，供本管线独立使用/对照。
 * 依赖（保留主线引用，同 jar 跨包）：WorldMainSettingScreen.FarLandsConfigData（config 联动照旧）、
 * ExactNoiseMath、WorldReposition、Mth、RandomSource、DynamicAccuracy.BigDecimal。
 */

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.doubles.DoubleList;
import it.unimi.dsi.fastutil.doubles.DoubleListIterator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;

public class NormalNoise extends net.minecraft.world.level.levelgen.synth.NormalNoise {
    private static final double INPUT_FACTOR = 1.0181268882175227;
    private static final double TARGET_DEVIATION = 0.3333333333333333;

    /** 🔧 MCRe 精确路径：INPUT_FACTOR 的精确 IEEE 754 值。 */
    private static final BigDecimal INPUT_FACTOR_BD = new BigDecimal(INPUT_FACTOR);

    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;
    private final double maxValue;
    private final net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters parameters;

    @Deprecated
    public static NormalNoise createLegacyNetherBiome(final RandomSource random, final net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, false);
    }

    public static NormalNoise create(final RandomSource random, final int firstOctave, final double
                    ... amplitudes) {
        return create(random, new net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters(firstOctave, new DoubleArrayList(amplitudes)));
    }

    public static NormalNoise create(final RandomSource random, final net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters parameters) {
        return new NormalNoise(random, parameters, true);
    }

    public NormalNoise(final RandomSource random, final net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters parameters, final boolean useNewInitialization) {
        super(); // 🔧 MCRe：主线 protected 空构造（字段由本副本类遮蔽初始化）
        int firstOctave = parameters.firstOctave(); // 🔧 record accessor（主线组件 private）
        DoubleList amplitudes = parameters.amplitudes(); // 🔧 record accessor
        this.parameters = parameters;

        // 🔧 MCRe：无条件将振幅截断到 float 精度，模拟基岩单精度行为，
        //          用于计算 octave 范围和 valueFactor
        DoubleArrayList truncated = new DoubleArrayList(amplitudes.size());
        for (int i = 0; i < amplitudes.size(); i++) {
            truncated.add((float) amplitudes.getDouble(i));
        }
        DoubleList effectiveAmplitudes = truncated;

        // 使用 effectiveAmplitudes 创建 PerlinNoise（create 方法内部会再次截断，保持一致性）
        if (useNewInitialization) {
            this.first = PerlinNoise.create(random, firstOctave, effectiveAmplitudes);
            this.second = PerlinNoise.create(random, firstOctave, effectiveAmplitudes);
        } else {
            this.first = PerlinNoise.createLegacyForLegacyNetherBiome(random, firstOctave, effectiveAmplitudes);
            this.second = PerlinNoise.createLegacyForLegacyNetherBiome(random, firstOctave, effectiveAmplitudes);
        }

        int minOctave = Integer.MAX_VALUE;
        int maxOctave = Integer.MIN_VALUE;
        DoubleListIterator iterator = effectiveAmplitudes.iterator();

        while (iterator.hasNext()) {
            int i = iterator.nextIndex();
            double amplitude = iterator.nextDouble();
            if (amplitude != 0.0) {
                minOctave = Math.min(minOctave, i);
                maxOctave = Math.max(maxOctave, i);
            }
        }

        double rawExpectedDeviation = expectedDeviation(maxOctave - minOctave);
        double rawValueFactor = 0.16666666666666666 / rawExpectedDeviation;

        this.valueFactor = (float) rawValueFactor;

        double rawMaxValue = (this.first.maxValue() + this.second.maxValue()) * this.valueFactor;

        this.maxValue = (float) rawMaxValue;
    }

    @Override
    public double maxValue() {
        double result = this.maxValue;

        return (float) result;
    }

    private static double expectedDeviation(final int octaveSpan) {

        // === Bedrock 模式：全 float 计算 ===
        float fOctaveSpan = (float) octaveSpan;
        float result = 0.1f * (1.0f + 1.0f / (fOctaveSpan + 1.0f));
        return (float) result;
    }

    @Override
    public double getValue(final double x, final double y, final double z) {

        // === Bedrock 模式：全 float 精度 ===
        float fx = (float) x;
        float fy = (float) y;
        float fz = (float) z;

        // INPUT_FACTOR 转 float 后再乘，模拟单精度
        float factor = (float) INPUT_FACTOR;
        float x2 = fx * factor;
        float y2 = fy * factor;
        float z2 = fz * factor;

        // 调用 PerlinNoise.getValue（内部会根据模式切换精度）
        // 但为了确保输入精度已被截断，我们将 float 转回 double 传入（PerlinNoise 内部会再次判断）
        float val1 = (float) this.first.getValue((double) fx, (double) fy, (double) fz);
        float val2 = (float) this.second.getValue((double) x2, (double) y2, (double) z2);

        float result = (val1 + val2) * (float) this.valueFactor;
        return (float) result;
    }

    /**
     * 🔧 MCRe「使用 BigDecimal / BigInteger 重写地形」——精确版 {@link #getValue(double, double, double)}。
     *
     * <p>第二组八度的坐标偏移 {@code × 1.0181268882175227} 改为精确乘法（原版这一步的舍入误差 会被 PerlinNoise 内部的 {@code × 2^k}
     * 放大 2^15 倍）。噪声输出仍用 double 加权求和。
     */
    @Override
    public double getValueExact(final BigDecimal x, final BigDecimal y, final BigDecimal z) {
        // 🔧 Bedrock 模式适配：输入先量化到 float（模拟基岩单精度输入坐标），再精确计算
        // float 的值可精确表示为 BigDecimal → 精确计算无灾难性抵消 → 无地形拉伸
        final BigDecimal bx = BigDecimal.valueOf(x.floatValue());
        final BigDecimal by = BigDecimal.valueOf(y.floatValue());
        final BigDecimal bz = BigDecimal.valueOf(z.floatValue());
        final BigDecimal x2 = bx.multiply(INPUT_FACTOR_BD);
        final BigDecimal y2 = by.multiply(INPUT_FACTOR_BD);
        final BigDecimal z2 = bz.multiply(INPUT_FACTOR_BD);
        return (this.first.getValueExact(bx, by, bz) + this.second.getValueExact(x2, y2, z2)) * this.valueFactor;
    }

    @Override
    public net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters parameters() {
        return this.parameters;
    }

    @VisibleForTesting
    public void parityConfigString(final StringBuilder sb) {
        sb.append("NormalNoise {");
        sb.append("first: ");
        this.first.parityConfigString(sb);
        sb.append(", second: ");
        this.second.parityConfigString(sb);
        sb.append("}");
    }

    // 🔧 MCRe 副本注：NoiseParameters record 已删——直接用主线
    // net.minecraft.world.level.levelgen.synth.net.minecraft.world.level.levelgen.synth.NormalNoise.NoiseParameters（参数数据共享、实现隔离）
}