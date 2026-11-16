package net.MinecraftTools.terrain.pipeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.mojang.logging.LogUtils;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;

import org.slf4j.Logger;

/**
 * 🔧 MCRe 地形管线系统（MinecraftChunkPyramid）——管线注册表。
 *
 * <p>{@link #active()} 返回 {@code null} 表示原版管线（config 未设置或 "vanilla"）——
 * 分派处据此走原版路径，<b>不注册 VanillaTerrainPipeline 假对象</b>（避免委托递归）。
 * 自定义管线注册后即可被 config 的 terrainPipeline 字段选中。
 */
public final class TerrainPipelineRegistry {

    /** 原版管线保留 ID（active() 对它返回 null） */
    public static final String VANILLA_ID = "Java-1.18.2+";

    private static final Map<String, TerrainPipeline> PIPELINES = new ConcurrentHashMap<>();

    /** 保序注册列表（ConcurrentHashMap 无序——UI 选项顺序 = 注册顺序） */
    private static final List<TerrainPipeline> ORDERED = new ArrayList<>();

    /** 已打过"未知 id"日志的 id（active() 每 chunk 生成多次调用，按 id 去重防刷屏） */
    private static final Set<String> LOGGED_UNKNOWN_IDS = ConcurrentHashMap.newKeySet();

    private static final Logger LOGGER = LogUtils.getLogger();

    static {
        register(MCVanilla263Pipeline.INSTANCE);
        register(MCBedrockGenPipeline.INSTANCE);
        register(MCVanilla1_18_1Pipeline.INSTANCE);
        register(MCBedrock0601GenPipeline.INSTANCE);
    }

    private TerrainPipelineRegistry() {
    }

    public static void register(final TerrainPipeline pipeline) {
        if (VANILLA_ID.equals(pipeline.id())) {
            throw new IllegalArgumentException("vanilla id is reserved for the built-in pipeline");
        }
        PIPELINES.put(pipeline.id(), pipeline);
        ORDERED.add(pipeline);
    }

    /** 当前激活管线（null = 原版）。每次调用即读全局配置——世界创建前设置即可生效。 */
    @Nullable
    public static TerrainPipeline active() {
        final WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        final String id = config == null ? null : config.farlandsStyle;
        if (id == null || id.isEmpty() || VANILLA_ID.equals(id)) {
            return null;
        }
        final TerrainPipeline pipeline = PIPELINES.get(id);
        // 🔧 防"静默回落"：config 有值但注册表查不到（id 拼错 / 管线被删 / id 改名）→ WARN 一声。
        // active() 在 NoiseBasedChunkGenerator 有多个调用点、每 chunk 触发，按 id 去重只打一次。
        if (pipeline == null && LOGGED_UNKNOWN_IDS.add(id)) {
            LOGGER.warn(
                "Unknown terrain pipeline id in farlands_config.json: \"{}\" -- falling back to the vanilla pipeline (registered: {})",
                id, ORDERED.stream().map(TerrainPipeline::id).toList());
        }
        return pipeline;
    }

    /** 全部已注册管线（保序 = 注册顺序；vanilla 条目由 UI 自行拼接） */
    public static List<TerrainPipeline> registered() {
        return new ArrayList<>(ORDERED);
    }

    /** 注册表内管线的显示名（UI 动态选项用；查不到返回 null） */
    @Nullable
    public static String displayNameOf(@Nullable final String id) {
        for (final TerrainPipeline pipeline : ORDERED) {
            if (pipeline.id().equals(id)) {
                return pipeline.displayName();
            }
        }
        return null;
    }
}
