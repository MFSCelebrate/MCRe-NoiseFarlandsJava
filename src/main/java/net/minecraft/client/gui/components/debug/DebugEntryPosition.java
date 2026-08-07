package net.minecraft.client.gui.components.debug;

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
import net.MinecraftTools.Math._256Bit.Float256;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugEntryPosition implements DebugScreenEntry {
    public static final Identifier GROUP = Identifier.withDefaultNamespace("position");

    /** 精确显示小数部分的最大位数（超出截断 + 省略号） */
    private static final int MAX_FRAC_DIGITS = 25;

    /** double → Float256 → 精确十进制（完整 52-bit 尾数展开，限长显示） */
    private static String fmtExact(final double value) {
        String s = Float256.of(value).toExactString();
        int dot = s.indexOf('.');
        if (dot < 0) return s;
        int frac = s.length() - dot - 1;
        if (frac <= MAX_FRAC_DIGITS) return s;
        return s.substring(0, dot + MAX_FRAC_DIGITS + 1) + "…";
    }

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

            // ===== 正确的摄像机获取方式 =====
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
            java.util.Set<ChunkPos> chunks = serverOrClientLevel instanceof ServerLevel serverLevel ? serverLevel.getForceLoadedChunks() : java.util.Set.of();

            // ===== 精度计算 =====
            long maxAbs = (long)Math.max(
                Math.abs(entity.getX()),
                Math.max(Math.abs(entity.getY()), Math.abs(entity.getZ()))
            );
            int shift = 64 - Long.numberOfLeadingZeros(maxAbs);
            double doublePrecision = Math.pow(2.0, (double)(shift - 53));
            double floatPrecision = Math.pow(2.0, (double)(shift - 24));
            String precisionString = "Current precision: §" + getColorCodeFromPrecision(doublePrecision) + doublePrecision
                                   + "§r (float: §" + getColorCodeFromPrecision(floatPrecision) + floatPrecision + "§r)";

            displayer.addToGroup(
                GROUP,
                List.of(
                    // ===== 256-bit 精确坐标（完整展开 double 的 52-bit 尾数） =====
                    "XYZ: " + fmtExact(entity.getX()) + " / " + fmtExact(entity.getY()) + " / " + fmtExact(entity.getZ()),
                    // ===== 256-bit 精确摄像机坐标 =====
                    "XYZ(Camera): " + fmtExact(camX) + " / " + fmtExact(camY) + " / " + fmtExact(camZ),
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

    private static char getColorCodeFromPrecision(double precision) {
        if (precision <= 0.03125) {
            return 'a';
        } else if (precision > 0.25) {
            return 'c';
        } else {
            return 'e';
        }
    }
}