package net.MinecraftTools.render;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;

/**
 * 🔧 MCRe「基岩版 条纹之地与切片之地」——用携带版 0.6.1 {@code TileRenderer} 的 <b>float32 绝对坐标顶点</b>
 * 接管<b>方块表面贴图渲染</b>，逐位一致地还原由 Float 精度丢失带来的纯客户端条纹之地与切片之地。
 *
 * <p><b>为什么需要</b>：Java 26.2（以及本改版）的区块网格是<b>section 局部坐标</b>构建的
 * （{@code SectionCompiler} → {@code SectionPos.sectionRelative(...)} → {@code putBlockBakedQuad}），
 * 顶点缓冲里从头到尾<b>没有绝对世界坐标</b> → 2<sup>24</sup> 的 float32 塌缩<b>结构上不会发生</b>。
 * 携带版 0.6.1 则是把绝对方块坐标直接塞进 float32 顶点（见 {@link BedrockFloatVertex} 的完整推导），
 * 于是 16777216 处出现周期 3 的<b>条纹之地</b>，2<sup>25</sup> 起变成<b>切片之地</b>。
 *
 * <p><b>本类做什么</b>：在开关开启时，把方块四边形的顶点位置改成携带版的算法 ——
 * <b>角点在绝对世界坐标量级上以 float32 相加</b>（+1 格偏移会被 round-half-to-even 吃掉），
 * 再减去区块原点得到局部量。数学见 {@link BedrockFloatVertex}。
 *
 * <p><b>已知边界（与携带版的差异）</b>：携带版的<b>相机减法</b>也是 float32
 * （{@code RenderList.init(xOff,yOff,zOff)} + {@code float32(chunkPos)}），
 * 所以它在 2<sup>24</sup> 处还会有「相机按 2 格跳」的抖动；本改版的相机相对化是
 * <b>double 相减 → int 相对量</b>（{@code LevelRenderer} → {@code ChunkSectionInfo}），
 * 保持精确、不抖。<b>条纹/切片的几何本身是逐位一致的</b>，抖不抖属于相机侧，不属于「方块表面贴图渲染」。
 *
 * @author MCRe NoiseFarlandsJava
 * @since 2026-11-28
 */
public final class BedrockTileRenderer {

    private BedrockTileRenderer() {
    }

    /**
     * 「基岩版 条纹之地与切片之地」开关是否开启。
     *
     * @return true = 用携带版 0.6.1 的 float32 绝对坐标算法渲染方块表面
     */
    public static boolean isEnabled() {
        final WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        return config != null && config.enabledBedrockStripeLands;
    }

    /**
     * 逐位复刻携带版 0.6.1 {@code TileRenderer} 的方块表面顶点写出。
     *
     * <p>携带版：{@code 顶点 = float32(绝对方块坐标) + 角点 + float32(-区块原点)}；
     * 本方法：{@code 局部 = (float32(绝对方块坐标) + 角点) - float32(区块原点)} —— 二者逐位相同。
     *
     * @param consumer     顶点写入器
     * @param absBlockX    绝对方块坐标 X
     * @param absBlockY    绝对方块坐标 Y
     * @param absBlockZ    绝对方块坐标 Z
     * @param originBlockX 区块（section）原点 X（方块坐标）
     * @param originBlockY 区块（section）原点 Y（方块坐标）
     * @param originBlockZ 区块（section）原点 Z（方块坐标）
     * @param quad         四边形
     * @param instance     顶点实例（颜色 / 光照）
     */
    public static void putBlockBakedQuad(
        final VertexConsumer consumer,
        final long absBlockX,
        final long absBlockY,
        final long absBlockZ,
        final long originBlockX,
        final long originBlockY,
        final long originBlockZ,
        final BakedQuad quad,
        final QuadInstance instance
    ) {
        final Vector3fc normal = quad.direction().getUnitVec3f();
        final int lightEmission = quad.materialInfo().lightEmission();
        final float originXf = BedrockFloatVertex.quantizeBlockCoord(originBlockX);
        final float originYf = BedrockFloatVertex.quantizeBlockCoord(originBlockY);
        final float originZf = BedrockFloatVertex.quantizeBlockCoord(originBlockZ);
        final float absXf = BedrockFloatVertex.quantizeBlockCoord(absBlockX);
        final float absYf = BedrockFloatVertex.quantizeBlockCoord(absBlockY);
        final float absZf = BedrockFloatVertex.quantizeBlockCoord(absBlockZ);

        for (int vertex = 0; vertex < 4; vertex++) {
            final Vector3fc pos = quad.position(vertex);
            final long packedUv = quad.packedUV(vertex);
            final int vertexColor = instance.getColor(vertex);
            final int light = instance.getLightCoordsWithEmission(vertex, lightEmission);
            final float u = UVPair.unpackU(packedUv);
            final float v = UVPair.unpackV(packedUv);
            // ★ 携带版语义：角点在「绝对世界坐标量级」上以 float32 相加 → 2^24 起 +1 格偏移被吃掉
            final float vx = (absXf + pos.x()) - originXf;
            final float vy = (absYf + pos.y()) - originYf;
            final float vz = (absZf + pos.z()) - originZf;
            consumer.addVertex(vx, vy, vz, vertexColor, u, v, instance.overlayCoords(), light, normal.x(), normal.y(), normal.z());
        }
    }
}
