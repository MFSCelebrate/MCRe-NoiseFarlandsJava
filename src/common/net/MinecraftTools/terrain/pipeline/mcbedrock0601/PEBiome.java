package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 的生物群系（biome/Biome.cpp 的 _getBiome 判定树逐字）
 * + 26.2 群系映射（大佬裁定：PE 群系特质由管线 surface 自持，映射只管 chunk 的群系标记）。
 *
 * <p>判定树（_getBiome 逐字，downfall 先乘 temperature）：
 * t<0.10→tundra；d<0.20{t<0.50→tundra/t<0.95→savanna/else→desert}；
 * d>0.5&&t<0.7→swampland；t<0.50→taiga；t<0.97{d<0.35→shrubland/else→forest}；
 * else{d<0.45→plains/d<0.90→seasonalForest/else→rainForest}。
 *
 * <p>iceDesert 已注册但判定树不产出（PE 源码残留），跳过。shrubland→plains、
 * seasonalForest→forest（候选已向大佬报备，映射表可调）。
 */
public enum PEBiome {
    TUNDRA("snowy_plains"),
    DESERT("desert"),
    SAVANNA("savanna"),
    SWAMPLAND("swamp"),
    TAIGA("taiga"),
    SHRUBLAND("plains"),
    FOREST("forest"),
    PLAINS("plains"),
    SEASONAL_FOREST("forest"),
    RAINFOREST("jungle");

    /** 26.2 群系路径（minecraft:<path>）——从 vanilla BiomeSource.possibleBiomes() 按 key 匹配 */
    public final String vanillaPath;

    PEBiome(final String vanillaPath) {
        this.vanillaPath = vanillaPath;
    }

    /** PE _getBiome 判定树逐字（biome/Biome.cpp:161） */
    public static PEBiome judge(float temperature, float downfall) {
        downfall *= temperature;
        if (temperature < 0.10f) {
            return TUNDRA;
        }
        if (downfall < 0.20f) {
            if (temperature < 0.50f) {
                return TUNDRA;
            }
            if (temperature < 0.95f) {
                return SAVANNA;
            }
            return DESERT;
        }
        if (downfall > 0.5f && temperature < 0.7f) {
            return SWAMPLAND;
        }
        if (temperature < 0.50f) {
            return TAIGA;
        }
        if (temperature < 0.97f) {
            if (downfall < 0.35f) {
                return SHRUBLAND;
            }
            return FOREST;
        }
        if (downfall < 0.45f) {
            return PLAINS;
        }
        if (downfall < 0.90f) {
            return SEASONAL_FOREST;
        }
        return RAINFOREST;
    }
}
