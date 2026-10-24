package net.MinecraftTools.terrain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

/**
 * 🔧 MCRe 地形管线系统（MinecraftChunkPyramid）——管线注册表。
 *
 * <p>{@link #active()} 返回 {@code null} 表示原版管线（config 未设置或 "vanilla"）——
 * 分派处据此走原版路径，<b>不注册 VanillaTerrainPipeline 假对象</b>（避免委托递归）。
 * 自定义管线注册后即可被 config 的 terrainPipeline 字段选中。
 */
public final class TerrainPipelineRegistry {

    /** 原版管线保留 ID（active() 对它返回 null） */
    public static final String VANILLA_ID = "vanilla";

    private static final Map<String, TerrainPipeline> PIPELINES = new ConcurrentHashMap<>();

    static {
        register(FlatTestPipeline.INSTANCE);
    }

    private TerrainPipelineRegistry() {
    }

    public static void register(final TerrainPipeline pipeline) {
        if (VANILLA_ID.equals(pipeline.id())) {
            throw new IllegalArgumentException("vanilla id is reserved for the built-in pipeline");
        }
        PIPELINES.put(pipeline.id(), pipeline);
    }

    /** 当前激活管线（null = 原版）。每次调用即读全局配置——世界创建前设置即可生效。 */
    @Nullable
    public static TerrainPipeline active() {
        final WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        final String id = config == null ? null : config.terrainPipeline;
        if (id == null || id.isEmpty() || VANILLA_ID.equals(id)) {
            return null;
        }
        return PIPELINES.get(id);
    }

    /** 全部已注册管线（UI 列表用；vanilla 条目由 UI 自行拼接） */
    public static List<TerrainPipeline> registered() {
        return new ArrayList<>(PIPELINES.values());
    }
}
