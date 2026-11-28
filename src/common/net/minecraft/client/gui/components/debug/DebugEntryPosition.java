package net.minecraft.client.gui.components.debug;

import java.util.Locale;
import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math._256bit_Refactoring.FloatRefactor256;
import net.MinecraftTools.Math.DynamicAccuracy.BigInteger;
import net.MinecraftTools.precision.PrecisePosition;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.WorldReposition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 🔧 MCRe：Position 条目 —— 移植 26.4 Snapshot 2 全新架构（{@link DebugFact} 名字右对齐 + 值的对齐显示），
 * <b>完整保留 MCRe 全部自定义</b>（26 处）：
 * <ul>
 *   <li>double 精确十进制显示（{@code new BigDecimal(double)} + 限长省略）</li>
 *   <li>XYZ(Camera)（正确的摄像机获取：{@code mainCamera()}）</li>
 *   <li>Terrain XYZ（自研 BigDecimal 全链 + 显示层纯整数截断）</li>
 *   <li>Current precision（基于 Terrain XYZ 的 bit 长度推算 double/float 精度 + 颜色码）</li>
 * </ul>
 *
 * @since 2026-10-01
 */
@OnlyIn(Dist.CLIENT)
public class DebugEntryPosition implements DebugScreenEntry {

    /** 26.2 旧组名常量（过渡期兼容：DebugEntrySectionPosition 引用；迁移完成后移除） */
    @Deprecated
    public static final Identifier GROUP = Identifier.withDefaultNamespace("position");

    // ==================== 🔧 MCRe 自定义：精确显示辅助 ====================

    /**
     * 定点显示（复刻原版 {@code String.format("%.3f / %.5f / %.3f")} 位数语义：
     * digits 位小数 + HALF_UP 舍入——走 {@link FloatRefactor256#toFixedString}（256 浮点
     * 精确值展开，±0 经位模式保号；与原版在"精确值恰落舍入边界"时可能差 1 末位）。
     */
    private static String fmtFixed(final double value, final int digits) {
        return FloatRefactor256.of(value).toFixedString(digits);
    }

    /**
     * 🔧 MCRe：BigInteger 整数显示（Terrain XYZ 用，无小数点、无精度损失，直接 toString）。
     * 跟 XYZ/XYZ(Camera) 的 fmtExact 输出格式对齐（不省略、无限位数）。
     */
    private static String fmtBigInt(final BigInteger value) {
        return value.toString();
    }

    /** BigDecimal → 纯整数字符串（Terrain XYZ 显示用：截断小数，无小数点） */
    private static String fmtBigInt(final BigDecimal value) {
        return value.toBigInteger().toString();
    }

    /**
     * 🔧 MCRe：计算 Terrain XYZ（玩家坐标经 WorldReposition 偏移缩放 → BigDecimal → BigInteger 截断）。
     * <p><b>精度关键：</b>用自研 {@code new BigDecimal(double)} 直接吃下 double 的全部 64-bit 信息——
     * 该构造器是完整 IEEE 754 精确转换（significand 去尾零 + 5^n 缩放，无舍入），
     * 不经 Float256 中转、也不走 Double.toString（后者对 ≥2^53 的大整数只保留 17 位有效数字，
     * 会造成 9223372036854776000 这类精度丢失）。
     * <p>无大小限制：scale/shift 即使是 1e49 也能精确算出 BigInteger 整数地形坐标。
     * <p>无精度损失：double → BigDecimal（精确）→ reposition BigDecimal（精确乘加）→ toBigInteger（截断小数部分）。
     */
    private static BigDecimal[] computeTerrainXYZ(final double playerX, final double playerY, final double playerZ) {
        // 全 BigDecimal 链（new BigDecimal(double) 精确 → reposition 精确乘加）；小数保留在内部，显示层截断
        return new BigDecimal[] {
            WorldReposition.reposition(new BigDecimal(playerX), Direction.Axis.X),
            WorldReposition.reposition(new BigDecimal(playerY), Direction.Axis.Y),
            WorldReposition.reposition(new BigDecimal(playerZ), Direction.Axis.Z)
        };
    }

    /**
     * 🔧 MCRe：计算 Terrain XYZ（优先用<b>高精度位置</b>）。
     *
     * <p>玩家启用高精度位置后（{@code PrecisePosition}），坐标真值是 237 位尾数的二进制浮点，
     * 其精确十进制展开与 double 的量化结果不同 —— 远坐标下 double 已被 ulp 截断（1e46 处 ulp≈1.27e30），
     * 而这里给出的是**未被截断**的真值。
     *
     * <p>无高精度位置时回落原 double 路径（行为不变）。
     */
    private static BigDecimal[] computeTerrainXYZ(final net.minecraft.world.entity.Entity entity) {
        final PrecisePosition precise = entity.getPrecisePosition();

        if (precise == null) {
            return computeTerrainXYZ(entity.getX(), entity.getY(), entity.getZ());
        }

        return new BigDecimal[] {
            WorldReposition.reposition(new BigDecimal(precise.exactX().toExactString()), Direction.Axis.X),
            WorldReposition.reposition(new BigDecimal(precise.exactY().toExactString()), Direction.Axis.Y),
            WorldReposition.reposition(new BigDecimal(precise.exactZ().toExactString()), Direction.Axis.Z)
        };
    }

    private static char getColorCodeFromPrecision(double precision) {
        if (precision <= 0.03125) {
            return 'a';
        } else if (precision > 0.25) {
            return 'c';
        } else {
            return 'e';
        }
    }

    // ==================== 26.4 新架构：display（Fact 对齐） ====================

    @Override
    public void display(
        final DebugScreenDisplayer displayer,
        final @Nullable Level serverOrClientLevel,
        final @Nullable LevelChunk clientChunk,
        final @Nullable LevelChunk serverChunk
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity entity = minecraft.getCameraEntity();
        if (entity != null) {
            BlockPos feetPos = minecraft.getCameraEntity().blockPosition();
            ChunkPos chunkPos = ChunkPos.containing(feetPos);
            Direction direction = entity.getDirection();

            // ===== 🔧 MCRe：正确的摄像机获取方式 =====
            Camera camera = minecraft.gameRenderer.mainCamera();
            double camX = camera.position().x;
            double camY = camera.position().y;
            double camZ = camera.position().z;

            String faceString = switch (direction) {
                case NORTH -> "Towards negative Z";
                case SOUTH -> "Towards positive Z";
                case WEST -> "Towards negative X";
                case EAST -> "Towards positive X";
                default -> "Invalid";
            };
            java.util.Set<ChunkPos> chunks = serverOrClientLevel instanceof ServerLevel serverLevel
                    ? serverLevel.getForceLoadedChunks()
                    : java.util.Set.of();

            // ===== 🔧 MCRe：Terrain XYZ + 精度计算 =====
            final BigDecimal[] terrainXYZ = computeTerrainXYZ(entity);
            // 🔧 MCRe：高精度位置（玩家启用后，坐标真值是 237 位尾数的二进制浮点）
            final PrecisePosition precisePosition = entity.getPrecisePosition();
            final BigInteger maxAbs = terrainXYZ[0].abs().toBigInteger().max(terrainXYZ[1].abs().toBigInteger()).max(terrainXYZ[2].abs().toBigInteger());
            final int bitLen = maxAbs.bitLength();  // 最高位 1 的位置（等价于 64 - Long.numberOfLeadingZeros）
            final double doublePrecision = Math.pow(2.0, (double) (bitLen - 53));
            final double floatPrecision = Math.pow(2.0, (double) (bitLen - 24));
            // 🔧 MCRe：FloatRefactor256（237 位精度：1 隐含 + 236 显式小数）的最小可分辨步长
            //   —— 与 double(2^(bitLen-53)) / float(2^(bitLen-24)) 完全同款公式族，PRECISION=237
            final double floatRefactorPrecision = Math.pow(2.0, (double) (bitLen - FloatRefactor256.PRECISION));

            // ===== 26.4 Fact 风格：名字右对齐 + 值（DebugGroups.POSITION，白强调色）=====
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "XYZ",
                fact -> {
                    // 🔧 MCRe：显示**精确坐标**（237 位尾数），小数位与原版一致（x/z 3 位、y 5 位）
                    //   —— double 投影在远坐标下已被 ulp 量化（1e46 处 ulp≈1.27e30），精确值能多显示被吞掉的小数
                    if (precisePosition != null) {
                        fact.value(precisePosition.exactX().toFixedString(3))
                                .text(" / ")
                                .value(precisePosition.exactY().toFixedString(5))
                                .text(" / ")
                                .value(precisePosition.exactZ().toFixedString(3));
                    } else {
                        fact.value(fmtFixed(entity.getX(), 3)).text(" / ").value(fmtFixed(entity.getY(), 5)).text(" / ").value(fmtFixed(entity.getZ(), 3));
                    }
                }
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "XYZ(Camera)",
                fact -> {
                    // 🔧 MCRe：显示**精确的摄像机坐标** = 精确实体位置 + （摄像机 double 位置 − 实体 double 位置）的偏移
                    //   （第一人称时偏移就是眼睛高度；第三人称拉远的偏移受 double 限制，远坐标下本就丢失 → 偏移为 0）
                    if (precisePosition != null) {
                        fact.value(precisePosition.exactX().add(FloatRefactor256.of(camX - entity.getX())).toFixedString(3))
                                .text(" / ")
                                .value(precisePosition.exactY().add(FloatRefactor256.of(camY - entity.getY())).toFixedString(5))
                                .text(" / ")
                                .value(precisePosition.exactZ().add(FloatRefactor256.of(camZ - entity.getZ())).toFixedString(3));
                    } else {
                        fact.value(fmtFixed(camX, 3)).text(" / ").value(fmtFixed(camY, 5)).text(" / ").value(fmtFixed(camZ, 3));
                    }
                }
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Terrain XYZ(FloatRefactor256)",
                fact -> fact.value(fmtBigInt(terrainXYZ[0])).text(" / ").value(fmtBigInt(terrainXYZ[1])).text(" / ").value(fmtBigInt(terrainXYZ[2]))
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Block",
                fact -> fact.value(feetPos.getX()).text(" ").value(feetPos.getY()).text(" ").value(feetPos.getZ())
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Chunk",
                fact -> fact.value(chunkPos.x())
                    .text(" ")
                    .value(SectionPos.blockToSectionCoord(feetPos.getY()))
                    .text(" ")
                    .value(chunkPos.z())
                    .text(" [")
                    .value(chunkPos.getRegionLocalX())
                    .text(" ")
                    .value(chunkPos.getRegionLocalZ())
                    .text(" in ")
                    .formattedValue("r.%d.%d.mca", chunkPos.getRegionX(), chunkPos.getRegionZ())
                    .text("]")
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Current precision",
                fact -> fact.text(
                    "§" + getColorCodeFromPrecision(doublePrecision) + doublePrecision
                    + "§r (float: §" + getColorCodeFromPrecision(floatPrecision) + floatPrecision + "§r)"
                    + ", (FloatRefactor256: §" + getColorCodeFromPrecision(floatRefactorPrecision) + floatRefactorPrecision + "§r)"
                )
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Facing",
                fact -> fact.value(direction.toString())
                    .text(" (")
                    .value(faceString)
                    .text(") (")
                    .formattedValue("%.1f", Mth.wrapDegrees(entity.getYRot()))
                    .text(" / ")
                    .formattedValue("%.1f", Mth.wrapDegrees(entity.getXRot()))
                    .text(")")
            );
            displayer.addFactToGroup(
                DebugGroups.POSITION,
                "Dimension",
                fact -> fact.value(minecraft.level.dimension().identifier().toString())
            );
            if (!chunks.isEmpty()) {
                displayer.addFactToGroup(DebugGroups.POSITION, "Forced Chunks", fact -> fact.value(chunks.size()));
            }
        }
    }
}