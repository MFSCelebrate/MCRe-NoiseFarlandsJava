package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.MinecraftTools.render.BedrockFloatVertex;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FluidRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;
    private final FluidStateModelSet fluidModels;

    // 🔧 MCRe「基岩版 条纹之地与切片之地」：携带版 0.6.1 的 TileRenderer 用【绝对 float32】坐标写流体顶点
    // （(float)x + 角点），2^24 起角点偏移被 round-half-to-even 吃掉 → 流体表面同样出现条纹/切片。
    // 关闭时（默认）全部为 0 → 行为与原版逐位一致。
    private boolean bedrockAbsoluteCoords = false;
    private long originBlockX;
    private long originBlockY;
    private long originBlockZ;
    private float originXf;
    private float originYf;
    private float originZf;

    public FluidRenderer(final FluidStateModelSet fluidModels) {
        this.fluidModels = fluidModels;
    }

    /**
     * 🔧 MCRe：切换到携带版 0.6.1 的 float32 绝对坐标流体渲染（见 {@link BedrockFloatVertex}）。
     *
     * @param originBlockX 区块（section）原点 X（方块坐标）
     * @param originBlockY 区块（section）原点 Y（方块坐标）
     * @param originBlockZ 区块（section）原点 Z（方块坐标）
     */
    public void setBedrockAbsoluteCoords(final long originBlockX, final long originBlockY, final long originBlockZ) {
        this.bedrockAbsoluteCoords = true;
        this.originBlockX = originBlockX;
        this.originBlockY = originBlockY;
        this.originBlockZ = originBlockZ;
        this.originXf = BedrockFloatVertex.quantizeBlockCoord(originBlockX);
        this.originYf = BedrockFloatVertex.quantizeBlockCoord(originBlockY);
        this.originZf = BedrockFloatVertex.quantizeBlockCoord(originBlockZ);
    }

    private static boolean isNeighborSameFluid(final FluidState fluidState, final FluidState neighborFluidState) {
        return neighborFluidState.getType().isSame(fluidState.getType());
    }

    private static boolean isFaceOccludedByState(final Direction direction, final float height, final BlockState state) {
        VoxelShape occluder = state.getFaceOcclusionShape(direction.getOpposite());
        if (occluder == Shapes.empty()) {
            return false;
        } else if (occluder == Shapes.block()) {
            boolean fullBlock = height == 1.0F;
            return direction != Direction.UP || fullBlock;
        } else {
            VoxelShape shape = Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0);
            return Shapes.blockOccludes(shape, occluder, direction);
        }
    }

    private static boolean isFaceOccludedByNeighbor(final Direction direction, final float height, final BlockState neighborState) {
        return isFaceOccludedByState(direction, height, neighborState);
    }

    private static boolean isFaceOccludedBySelf(final BlockState state, final Direction direction) {
        return isFaceOccludedByState(direction.getOpposite(), 1.0F, state);
    }

    public static boolean shouldRenderFace(
        final FluidState fluidState, final BlockState blockState, final Direction direction, final FluidState neighborFluidState
    ) {
        return !isNeighborSameFluid(fluidState, neighborFluidState) && !isFaceOccludedBySelf(blockState, direction);
    }

    public void tesselate(
        final BlockAndTintGetter level, final BlockPos pos, final FluidRenderer.Output output, final BlockState blockState, final FluidState fluidState
    ) {
        BlockState blockStateDown = level.getBlockState(pos.relative(Direction.DOWN));
        FluidState fluidStateDown = blockStateDown.getFluidState();
        BlockState blockStateUp = level.getBlockState(pos.relative(Direction.UP));
        FluidState fluidStateUp = blockStateUp.getFluidState();
        BlockState blockStateNorth = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidStateNorth = blockStateNorth.getFluidState();
        BlockState blockStateSouth = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidStateSouth = blockStateSouth.getFluidState();
        BlockState blockStateWest = level.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidStateWest = blockStateWest.getFluidState();
        BlockState blockStateEast = level.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidStateEast = blockStateEast.getFluidState();
        boolean renderUp = !isNeighborSameFluid(fluidState, fluidStateUp);
        boolean renderDown = shouldRenderFace(fluidState, blockState, Direction.DOWN, fluidStateDown)
            && !isFaceOccludedByNeighbor(Direction.DOWN, 0.8888889F, blockStateDown);
        boolean renderNorth = shouldRenderFace(fluidState, blockState, Direction.NORTH, fluidStateNorth);
        boolean renderSouth = shouldRenderFace(fluidState, blockState, Direction.SOUTH, fluidStateSouth);
        boolean renderWest = shouldRenderFace(fluidState, blockState, Direction.WEST, fluidStateWest);
        boolean renderEast = shouldRenderFace(fluidState, blockState, Direction.EAST, fluidStateEast);
        if (renderUp || renderDown || renderEast || renderWest || renderNorth || renderSouth) {
            FluidModel model = this.fluidModels.get(fluidState);
            VertexConsumer builder = output.getBuilder(model.layer());
            int tintColor = model.tintSource() != null ? model.tintSource().colorInWorld(blockState, level, pos) : -1;
            CardinalLighting cardinalLighting = level.cardinalLighting();
            Fluid type = fluidState.getType();
            float heightSelf = this.getHeight(level, type, pos, blockState, fluidState);
            float heightNorthEast;
            float heightNorthWest;
            float heightSouthEast;
            float heightSouthWest;
            if (heightSelf >= 1.0F) {
                heightNorthEast = 1.0F;
                heightNorthWest = 1.0F;
                heightSouthEast = 1.0F;
                heightSouthWest = 1.0F;
            } else {
                float heightNorth = this.getHeight(level, type, pos.north(), blockStateNorth, fluidStateNorth);
                float heightSouth = this.getHeight(level, type, pos.south(), blockStateSouth, fluidStateSouth);
                float heightEast = this.getHeight(level, type, pos.east(), blockStateEast, fluidStateEast);
                float heightWest = this.getHeight(level, type, pos.west(), blockStateWest, fluidStateWest);
                heightNorthEast = this.calculateAverageHeight(
                    level, type, heightSelf, heightNorth, heightEast, pos.relative(Direction.NORTH).relative(Direction.EAST)
                );
                heightNorthWest = this.calculateAverageHeight(
                    level, type, heightSelf, heightNorth, heightWest, pos.relative(Direction.NORTH).relative(Direction.WEST)
                );
                heightSouthEast = this.calculateAverageHeight(
                    level, type, heightSelf, heightSouth, heightEast, pos.relative(Direction.SOUTH).relative(Direction.EAST)
                );
                heightSouthWest = this.calculateAverageHeight(
                    level, type, heightSelf, heightSouth, heightWest, pos.relative(Direction.SOUTH).relative(Direction.WEST)
                );
            }

            float x;
            float y;
            float z;
            if (this.bedrockAbsoluteCoords) {
                // 携带版语义：绝对坐标先量化成 float32，后面的「+1 格 / 0.001 内缩」都在绝对量级上做
                x = BedrockFloatVertex.quantizeBlockCoord(this.originBlockX + (pos.getX() & 15));
                y = BedrockFloatVertex.quantizeBlockCoord(this.originBlockY + (pos.getY() & 15));
                z = BedrockFloatVertex.quantizeBlockCoord(this.originBlockZ + (pos.getZ() & 15));
            } else {
                x = pos.getX() & 15;
                y = pos.getY() & 15;
                z = pos.getZ() & 15;
            }
            float offs = 0.001F;
            float bottomOffs = renderDown ? 0.001F : 0.0F;
            if (renderUp
                && !isFaceOccludedByNeighbor(
                    Direction.UP, Math.min(Math.min(heightNorthWest, heightSouthWest), Math.min(heightSouthEast, heightNorthEast)), blockStateUp
                )) {
                heightNorthWest -= 0.001F;
                heightSouthWest -= 0.001F;
                heightSouthEast -= 0.001F;
                heightNorthEast -= 0.001F;
                Vec3 flow = fluidState.getFlow(level, pos);
                float u00;
                float u01;
                float u10;
                float u11;
                float v00;
                float v01;
                float v10;
                float v11;
                if (flow.x == 0.0 && flow.z == 0.0) {
                    TextureAtlasSprite stillSprite = model.stillMaterial().sprite();
                    u00 = stillSprite.getU0();
                    v00 = stillSprite.getV0();
                    u01 = u00;
                    v01 = stillSprite.getV1();
                    u10 = stillSprite.getU1();
                    v10 = v01;
                    u11 = u10;
                    v11 = v00;
                } else {
                    float angle = (float)Mth.atan2(flow.z, flow.x) - (float) (Math.PI / 2);
                    float s = Mth.sin(angle) * 0.25F;
                    float c = Mth.cos(angle) * 0.25F;
                    float cc = 0.5F;
                    TextureAtlasSprite flowingSprite = model.flowingMaterial().sprite();
                    u00 = flowingSprite.getU(0.5F + (-c - s));
                    v00 = flowingSprite.getV(0.5F + (-c + s));
                    u01 = flowingSprite.getU(0.5F + (-c + s));
                    v01 = flowingSprite.getV(0.5F + (c + s));
                    u10 = flowingSprite.getU(0.5F + (c + s));
                    v10 = flowingSprite.getV(0.5F + (c - s));
                    u11 = flowingSprite.getU(0.5F + (c - s));
                    v11 = flowingSprite.getV(0.5F + (-c - s));
                }

                int topLightCoords = this.getLightCoords(level, pos);
                int topColor = ARGB.scaleRGB(tintColor, cardinalLighting.up());
                this.addFace(
                    builder,
                    x + 0.0F,
                    y + heightNorthWest,
                    z + 0.0F,
                    u00,
                    v00,
                    x + 0.0F,
                    y + heightSouthWest,
                    z + 1.0F,
                    u01,
                    v01,
                    x + 1.0F,
                    y + heightSouthEast,
                    z + 1.0F,
                    u10,
                    v10,
                    x + 1.0F,
                    y + heightNorthEast,
                    z + 0.0F,
                    u11,
                    v11,
                    topColor,
                    topLightCoords,
                    fluidState.shouldRenderBackwardUpFace(level, pos.above())
                );
            }

            if (renderDown) {
                TextureAtlasSprite stillSprite = model.stillMaterial().sprite();
                float u0 = stillSprite.getU0();
                float u1 = stillSprite.getU1();
                float v0 = stillSprite.getV0();
                float v1 = stillSprite.getV1();
                int belowLightCoords = this.getLightCoords(level, pos.below());
                int belowColor = ARGB.scaleRGB(tintColor, cardinalLighting.down());
                this.addFace(
                    builder,
                    x,
                    y + bottomOffs,
                    z,
                    u0,
                    v0,
                    x + 1.0F,
                    y + bottomOffs,
                    z,
                    u1,
                    v0,
                    x + 1.0F,
                    y + bottomOffs,
                    z + 1.0F,
                    u1,
                    v1,
                    x,
                    y + bottomOffs,
                    z + 1.0F,
                    u0,
                    v1,
                    belowColor,
                    belowLightCoords,
                    false
                );
            }

            int sideLightCoords = this.getLightCoords(level, pos);

            for (Direction faceDir : Direction.Plane.HORIZONTAL) {
                float hh0;
                float hh1;
                float x0;
                float z0;
                float x1;
                float z1;
                boolean renderCondition;
                BlockState faceState;
                switch (faceDir) {
                    case NORTH:
                        hh0 = heightNorthWest;
                        hh1 = heightNorthEast;
                        x0 = x;
                        x1 = x + 1.0F;
                        z0 = z + 0.001F;
                        z1 = z + 0.001F;
                        renderCondition = renderNorth;
                        faceState = blockStateNorth;
                        break;
                    case SOUTH:
                        hh0 = heightSouthEast;
                        hh1 = heightSouthWest;
                        x0 = x + 1.0F;
                        x1 = x;
                        z0 = z + 1.0F - 0.001F;
                        z1 = z + 1.0F - 0.001F;
                        renderCondition = renderSouth;
                        faceState = blockStateSouth;
                        break;
                    case WEST:
                        hh0 = heightSouthWest;
                        hh1 = heightNorthWest;
                        x0 = x + 0.001F;
                        x1 = x + 0.001F;
                        z0 = z + 1.0F;
                        z1 = z;
                        renderCondition = renderWest;
                        faceState = blockStateWest;
                        break;
                    case EAST:
                        hh0 = heightNorthEast;
                        hh1 = heightSouthEast;
                        x0 = x + 1.0F - 0.001F;
                        x1 = x + 1.0F - 0.001F;
                        z0 = z;
                        z1 = z + 1.0F;
                        renderCondition = renderEast;
                        faceState = blockStateEast;
                        break;
                    default:
                        throw new UnsupportedOperationException();
                }

                if (renderCondition && !isFaceOccludedByNeighbor(faceDir, Math.max(hh0, hh1), faceState)) {
                    TextureAtlasSprite sprite = model.flowingMaterial().sprite();
                    boolean isOverlay = false;
                    if (model.overlayMaterial() != null) {
                        Block relativeBlock = faceState.getBlock();
                        if (relativeBlock instanceof HalfTransparentBlock || relativeBlock instanceof LeavesBlock) {
                            sprite = model.overlayMaterial().sprite();
                            isOverlay = true;
                        }
                    }

                    float u0 = sprite.getU(0.0F);
                    float u1 = sprite.getU(0.5F);
                    float v01 = sprite.getV((1.0F - hh0) * 0.5F);
                    float v02 = sprite.getV((1.0F - hh1) * 0.5F);
                    float v1 = sprite.getV(0.5F);
                    float shadeSide = faceDir.getAxis() == Direction.Axis.Z ? cardinalLighting.north() : cardinalLighting.west();
                    int faceColor = ARGB.scaleRGB(tintColor, cardinalLighting.up() * shadeSide);
                    this.addFace(
                        builder,
                        x0,
                        y + hh0,
                        z0,
                        u0,
                        v01,
                        x1,
                        y + hh1,
                        z1,
                        u1,
                        v02,
                        x1,
                        y + bottomOffs,
                        z1,
                        u1,
                        v1,
                        x0,
                        y + bottomOffs,
                        z0,
                        u0,
                        v1,
                        faceColor,
                        sideLightCoords,
                        !isOverlay
                    );
                }
            }
        }
    }

    private void addFace(
        final VertexConsumer builder,
        final float x0,
        final float y0,
        final float z0,
        final float u0,
        final float v0,
        final float x1,
        final float y1,
        final float z1,
        final float u1,
        final float v1,
        final float x2,
        final float y2,
        final float z2,
        final float u2,
        final float v2,
        final float x3,
        final float y3,
        final float z3,
        final float u3,
        final float v3,
        final int color,
        final int lightCoords,
        final boolean addBackFace
    ) {
        this.vertex(builder, x0, y0, z0, color, u0, v0, lightCoords);
        this.vertex(builder, x1, y1, z1, color, u1, v1, lightCoords);
        this.vertex(builder, x2, y2, z2, color, u2, v2, lightCoords);
        this.vertex(builder, x3, y3, z3, color, u3, v3, lightCoords);
        if (addBackFace) {
            this.vertex(builder, x0, y0, z0, color, u0, v0, lightCoords);
            this.vertex(builder, x3, y3, z3, color, u3, v3, lightCoords);
            this.vertex(builder, x2, y2, z2, color, u2, v2, lightCoords);
            this.vertex(builder, x1, y1, z1, color, u1, v1, lightCoords);
        }
    }

    private float calculateAverageHeight(
        final BlockAndTintGetter level, final Fluid type, final float heightSelf, final float height2, final float height1, final BlockPos cornerPos
    ) {
        if (!(height1 >= 1.0F) && !(height2 >= 1.0F)) {
            float[] weightedHeight = new float[2];
            if (height1 > 0.0F || height2 > 0.0F) {
                float heightCorner = this.getHeight(level, type, cornerPos);
                if (heightCorner >= 1.0F) {
                    return 1.0F;
                }

                this.addWeightedHeight(weightedHeight, heightCorner);
            }

            this.addWeightedHeight(weightedHeight, heightSelf);
            this.addWeightedHeight(weightedHeight, height1);
            this.addWeightedHeight(weightedHeight, height2);
            return weightedHeight[0] / weightedHeight[1];
        } else {
            return 1.0F;
        }
    }

    private void addWeightedHeight(final float[] weightedHeight, final float height) {
        if (height >= 0.8F) {
            weightedHeight[0] += height * 10.0F;
            weightedHeight[1] += 10.0F;
        } else if (height >= 0.0F) {
            weightedHeight[0] += height;
            weightedHeight[1]++;
        }
    }

    private float getHeight(final BlockAndTintGetter level, final Fluid fluidType, final BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return this.getHeight(level, fluidType, pos, state, state.getFluidState());
    }

    private float getHeight(final BlockAndTintGetter level, final Fluid fluidType, final BlockPos pos, final BlockState state, final FluidState fluidState) {
        if (fluidType.isSame(fluidState.getType())) {
            BlockState aboveState = level.getBlockState(pos.above());
            return fluidType.isSame(aboveState.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        } else {
            return !state.isSolid() ? 0.0F : -1.0F;
        }
    }

    private void vertex(
        final VertexConsumer builder, final float x, final float y, final float z, final int color, final float u, final float v, final int lightCoords
    ) {
        // 🔧 MCRe「基岩版 条纹之地与切片之地」：携带版的顶点最终是「绝对 float32 角点 + float32(-区块原点)」
        // （Chunk.cpp 的 t.offset((float)(-chunk->x), …)）→ 这里等价地减去区块原点。
        // 非基岩模式下原点为 0 → 与原版逐位一致。
        builder.addVertex(x - this.originXf, y - this.originYf, z - this.originZf, color, u, v, OverlayTexture.NO_OVERLAY, lightCoords, 0.0F, 1.0F, 0.0F);
    }

    private int getLightCoords(final BlockAndTintGetter level, final BlockPos pos) {
        return LightCoordsUtil.max(LightCoordsUtil.getLightCoords(level, pos), LightCoordsUtil.getLightCoords(level, pos.above()));
    }

    @FunctionalInterface
    @OnlyIn(Dist.CLIENT)
    public interface Output {
        VertexConsumer getBuilder(ChunkSectionLayer layer);
    }
}