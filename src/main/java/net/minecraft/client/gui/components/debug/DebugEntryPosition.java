package net.minecraft.client.gui.components.debug;

import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.Locale;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugEntryPosition implements DebugScreenEntry {
    public static final Identifier GROUP = Identifier.withDefaultNamespace("position");

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

            // ===== 获取摄像机坐标 =====
            Camera camera = minecraft.getCamera();
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
            LongSet chunks = serverOrClientLevel instanceof ServerLevel serverLevel ? serverLevel.getForceLoadedChunks() : LongSets.EMPTY_SET;

            // ===== 新增：计算当前精度 =====
            long maxAbs = (long)Math.max(
                Math.abs(entity.getX()),
                Math.max(Math.abs(entity.getY()), Math.abs(entity.getZ()))
            );
            int shift = 64 - Long.numberOfLeadingZeros(maxAbs);
            double doublePrecision = Math.pow(2.0, (double)(shift - 53));
            double floatPrecision = Math.pow(2.0, (double)(shift - 24));
            String precisionString = "Current precision: §" + getColorCodeFromPrecision(doublePrecision) + doublePrecision
                                   + "§r (float: §" + getColorCodeFromPrecision(floatPrecision) + floatPrecision + "§r)";
            // ===== 新增结束 =====

            displayer.addToGroup(
                GROUP,
                List.of(
                    String.format(
                        Locale.ROOT,
                        "XYZ: %.15f / %.10f / %.15f",
                        entity.getX(),
                        entity.getY(),
                        entity.getZ()
                    ),
                    // ===== 新增：摄像机坐标行（放在 XYZ 下面，Chunk 上面） =====
                    String.format(
                        Locale.ROOT,
                        "XYZ(Camera): %.15f / %.10f / %.15f",
                        camX,
                        camY,
                        camZ
                    ),
                    String.format(Locale.ROOT, "Block: %d %d %d", feetPos.getX(), feetPos.getY(), feetPos.getZ()),
                    String.format(
                        Locale.ROOT,
                        "Chunk: %d %d %d [%d %d in r.%d.%d.mca]",
                        chunkPos.x(),
                        SectionPos.blockToSectionCoord(feetPos.getY()),
                        chunkPos.z(),
                        chunkPos.getRegionLocalX(),
                        chunkPos.getRegionLocalZ(),
                        chunkPos.getRegionX(),
                        chunkPos.getRegionZ()
                    ),
                    precisionString,
                    String.format(
                        Locale.ROOT,
                        "Facing: %s (%s) (%.1f / %.1f)",
                        direction,
                        faceString,
                        Mth.wrapDegrees(entity.getYRot()),
                        Mth.wrapDegrees(entity.getXRot())
                    ),
                    minecraft.level.dimension().identifier() + " FC: " + chunks.size()
                )
            );
        }
    }

    // ===== 私有辅助方法 =====
    private static char getColorCodeFromPrecision(double precision) {
        if (precision <= 0.03125) {
            return 'a';   // 绿色（高精度）
        } else if (precision > 0.25) {
            return 'c';   // 红色（低精度）
        } else {
            return 'e';   // 黄色（中等精度）
        }
    }
}