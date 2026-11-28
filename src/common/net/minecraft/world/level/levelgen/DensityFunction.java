package net.minecraft.world.level.levelgen;

import com.mojang.serialization.Codec;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jspecify.annotations.Nullable;

public interface DensityFunction {
    Codec<DensityFunction> CODEC = RegistryFileCodec.create(Registries.DENSITY_FUNCTION, DensityFunctions.DIRECT_CODEC).xmap(holder -> {
        return switch (holder) {
            case Holder.Direct<DensityFunction> direct -> (DensityFunction)direct.value();
            case Holder.Reference<DensityFunction> reference -> new DensityFunctions.HolderHolder(reference);
            default -> throw new MatchException(null, null);
        };
    }, value -> {
        return switch (value) {
            case DensityFunctions.HolderHolder(Holder<DensityFunction> function) -> function;
            default -> Holder.direct(value);
        };
    });

    double compute(final DensityFunction.FunctionContext context);

    void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider);

    DensityFunction mapChildren(final DensityFunction.Visitor visitor);

    default DensityFunction mapAll(final DensityFunction.Visitor visitor) {
        class RecursiveVisitor implements DensityFunction.Visitor {
            @Override
            public DensityFunction apply(final DensityFunction input) {
                return visitor.apply(input.mapChildren(this));
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(final DensityFunction.NoiseHolder noise) {
                return visitor.visitNoise(noise);
            }
        }

        return new RecursiveVisitor().apply(this);
    }

    double minValue();

    double maxValue();

    KeyDispatchDataCodec<? extends DensityFunction> codec();

    default DensityFunction clamp(final double min, final double max) {
        return new DensityFunctions.Clamp(this, min, max);
    }

    default DensityFunction abs() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.ABS);
    }

    default DensityFunction square() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUARE);
    }

    default DensityFunction cube() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.CUBE);
    }

    default DensityFunction halfNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.HALF_NEGATIVE);
    }

    default DensityFunction quarterNegative() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
    }

    default DensityFunction invert() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.INVERT);
    }

    default DensityFunction squeeze() {
        return DensityFunctions.map(this, DensityFunctions.Mapped.Type.SQUEEZE);
    }

    interface ContextProvider {
        DensityFunction.FunctionContext forIndex(int index);

        void fillAllDirectly(double[] output, DensityFunction function);
    }

    interface FunctionContext {
        int blockX();

        int blockY();

        int blockZ();

        /**
         * 🔧 MCRe：<b>精确采样坐标</b>（BigDecimal）。
         *
         * <p>为什么需要：{@link #blockX()} 返回 <b>int</b> → 采样坐标的天花板只有 2^31 格（±21.4 亿），
         * 这正是「平面边境之地」的直接成因（坐标再大也会被 int 截断回绕）。
         * 本方法给出不受 int 限制的精确坐标，供偏移缩放（WorldReposition）与精确噪声链使用。
         *
         * <p>默认实现回落到 int 坐标（保持 vanilla 语义与第三方实现兼容）；
         * {@code NoiseChunk} 覆写为「精确区块原点 + 区块内偏移」。
         *
         * <p>⚠️ 用全限定名：{@code net.MinecraftTools.Math.DynamicAccuracy.BigDecimal} 与
         * {@code java.math.BigDecimal} 同名，本文件已 import 后者。
         */
        default net.MinecraftTools.Math.DynamicAccuracy.BigDecimal blockXExact() {
            return net.MinecraftTools.Math.DynamicAccuracy.BigDecimal.valueOf(this.blockX());
        }

        /** 🔧 MCRe：精确采样 Y 坐标（见 {@link #blockXExact()}）。 */
        default net.MinecraftTools.Math.DynamicAccuracy.BigDecimal blockYExact() {
            return net.MinecraftTools.Math.DynamicAccuracy.BigDecimal.valueOf(this.blockY());
        }

        /** 🔧 MCRe：精确采样 Z 坐标（见 {@link #blockXExact()}）。 */
        default net.MinecraftTools.Math.DynamicAccuracy.BigDecimal blockZExact() {
            return net.MinecraftTools.Math.DynamicAccuracy.BigDecimal.valueOf(this.blockZ());
        }
    }

    record NoiseHolder(Holder<NormalNoise.NoiseParameters> noiseData, @Nullable NormalNoise noise) {
        public static final Codec<DensityFunction.NoiseHolder> CODEC = NormalNoise.NoiseParameters.CODEC
            .xmap(data -> new DensityFunction.NoiseHolder((Holder<NormalNoise.NoiseParameters>)data, null), DensityFunction.NoiseHolder::noiseData);

        public NoiseHolder(final Holder<NormalNoise.NoiseParameters> noiseData) {
            this(noiseData, null);
        }

        public double getValue(final double x, final double y, final double z) {
            return this.noise == null ? 0.0 : this.noise.getValue(x, y, z);
        }

        /**
         * 🔧 MCRe「使用 BigDecimal / BigInteger 重写地形」——精确版取值。
         * <p>语义与原版一致（noise 未解析时返回 0.0），只是坐标以精确值传入，
         * 由 {@link NormalNoise#getValueExact} 一路精确到晶格定位。
         */
        public double getValueExact(final BigDecimal x, final BigDecimal y, final BigDecimal z) {
            return this.noise == null ? 0.0 : this.noise.getValueExact(x, y, z);
        }

        public double maxValue() {
            return this.noise == null ? 2.0 : this.noise.maxValue();
        }
    }

    interface SimpleFunction extends DensityFunction {
        @Override
        default void fillArray(final double[] output, final DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(output, this);
        }

        @Override
        default DensityFunction mapChildren(final DensityFunction.Visitor visitor) {
            return this;
        }
    }

    record SinglePointContext(int blockX, int blockY, int blockZ) implements DensityFunction.FunctionContext {
    }

    interface Visitor {
        DensityFunction apply(DensityFunction input);

        default DensityFunction.NoiseHolder visitNoise(final DensityFunction.NoiseHolder noise) {
            return noise;
        }
    }
}