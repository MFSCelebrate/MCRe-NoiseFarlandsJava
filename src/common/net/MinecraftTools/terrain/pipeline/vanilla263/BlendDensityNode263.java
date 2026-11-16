package net.MinecraftTools.terrain.pipeline.vanilla263;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.blending.Blender;

/**
 * blending 密度节点，对应 26.3 的 {@code BlendDensityFunction}。
 *
 * <p>blender 为空时原样返回，这是常态，也是逐位一致的那一支。非空时走 26.1.2 的
 * {@code Blender.blendDensity}，它收 double 返 double，所以 blending 生效的那一片只能是近似一致。
 * 26.1.2 没有逐点 alpha 与 offset 的取数口，这一层补不回来。
 */
final class BlendDensityNode263 extends FloatNode263 {

    private final FloatNode263 input;

    BlendDensityNode263(FloatNode263 input) {
        this.input = input;
    }

    @Override
    float computeF(DensityFunction.FunctionContext context) {
        // 🔧 MCRe 移植适配：26.2 的 FunctionContext 无 getBlender（blend 由 NoiseChunk 内部的
        // BlendDensityFunction 处理）。263 自研节点拿不到 blender；Test263 场景为全新生成、
        // blender 恒 empty——纯透传即 26.2 等效语义（旧世界升级边缘的 blend 不适用本管线）。
        return this.input.computeF(context);
    }
}
