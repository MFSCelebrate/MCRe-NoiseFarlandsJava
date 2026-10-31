package net.MinecraftTools.terrain.pipeline.mcbedrock0601;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 🔧 MCBedrock0601GenPipeline：PE 0.6.1 的 feature 算法直译（levelgen/feature/*.h，照源码逐字）。
 *
 * <p>与 Java 版 feature 的差异全部照 PE 原样：OreFeature 是线状矿脉（dir 随机方向 + 椭球扫描、
 * 只替换石头）；TreeFeature 与 Java Beta 同源（treeHeight=nextInt(3)+4/树叶角落裁剪/树干
 * trunkType）；getTreeFeature 基类只返回普通橡树（PE 0.6.1 群系 override 未实装）；泉水是
 * "3 石 1 空"检测 + 流体源（instaTick 流动语义简化为放置源方块——26.2 生成期不跑 tick）。
 *
 * <p>方块写走 {@code region.setBlock(pos, state, UPDATE_CLIENTS)}（PE 的 setTileNoUpdate 生成期语义）。
 */
public final class PEFeature0601 {

    private PEFeature0601() {
    }

    private static void place(final WorldGenRegion region, final int x, final int y, final int z, final BlockState state) {
        region.setBlock(new BlockPos(x, y, z), state, Block.UPDATE_CLIENTS);
    }

    private static BlockState blockAt(final WorldGenRegion region, final int x, final int y, final int z) {
        return region.getBlockState(new BlockPos(x, y, z));
    }

    private static boolean isEmpty(final WorldGenRegion region, final int x, final int y, final int z) {
        return blockAt(region, x, y, z).isAir();
    }

    private static boolean isStone(final WorldGenRegion region, final int x, final int y, final int z) {
        return blockAt(region, x, y, z).is(Blocks.STONE);
    }

    /** PE OreFeature：线状矿脉 + 椭球扫描（只替换石头） */
    public static void ore(final WorldGenRegion region, final Mt19937Random random,
                           final int x, final int y, final int z, final BlockState ore, final int count) {
        final float dir = random.nextFloat() * (float) Math.PI;

        final float x0 = x + 8 + (float) Math.sin(dir) * count / 8;
        final float x1 = x + 8 - (float) Math.sin(dir) * count / 8;
        final float z0 = z + 8 + (float) Math.cos(dir) * count / 8;
        final float z1 = z + 8 - (float) Math.cos(dir) * count / 8;

        final float y0 = y + random.nextInt(3) + 2;
        final float y1 = y + random.nextInt(3) + 2;

        for (int d = 0; d <= count; d++) {
            final float dd = d;
            final float xx = x0 + (x1 - x0) * dd / count;
            final float yy = y0 + (y1 - y0) * dd / count;
            final float zz = z0 + (z1 - z0) * dd / count;

            final float ss = random.nextFloat() * count / 16;
            final float r = ((float) Math.sin(dd * Math.PI / count) + 1) * ss + 1;
            final float hr = ((float) Math.sin(dd * Math.PI / count) + 1) * ss + 1;

            final int xt0 = (int) (xx - r / 2);
            final int yt0 = (int) (yy - hr / 2);
            final int zt0 = (int) (zz - r / 2);
            final int xt1 = (int) (xx + r / 2);
            final int yt1 = (int) (yy + hr / 2);
            final int zt1 = (int) (zz + r / 2);

            for (int x2 = xt0; x2 <= xt1; x2++) {
                final float xd = (x2 + 0.5f - xx) / (r / 2);
                if (xd * xd >= 1) {
                    continue;
                }
                for (int y2 = yt0; y2 <= yt1; y2++) {
                    final float yd = (y2 + 0.5f - yy) / (hr / 2);
                    if (xd * xd + yd * yd >= 1) {
                        continue;
                    }
                    for (int z2 = zt0; z2 <= zt1; z2++) {
                        final float zd = (z2 + 0.5f - zz) / (r / 2);
                        if (xd * xd + yd * yd + zd * zd < 1 && isStone(region, x2, y2, z2)) {
                            place(region, x2, y2, z2, ore);
                        }
                    }
                }
            }
        }
    }

    /** PE ClayFeature：水中粘土球（同 Ore 骨架，位置须为水） */
    public static void clay(final WorldGenRegion region, final Mt19937Random random,
                            final int x, final int y, final int z, final int count) {
        final BlockState clay = Blocks.CLAY.defaultBlockState();
        final BlockState water = Blocks.WATER.defaultBlockState();
        final float dir = random.nextFloat() * (float) Math.PI;
        final float x0 = x + 8 + (float) Math.sin(dir) * count / 8;
        final float x1 = x + 8 - (float) Math.sin(dir) * count / 8;
        final float z0 = z + 8 + (float) Math.cos(dir) * count / 8;
        final float z1 = z + 8 - (float) Math.cos(dir) * count / 8;
        final float y0 = y + random.nextInt(3) + 2;
        final float y1 = y + random.nextInt(3) + 2;
        for (int d = 0; d <= count; d++) {
            final float dd = d;
            final float xx = x0 + (x1 - x0) * dd / count;
            final float yy = y0 + (y1 - y0) * dd / count;
            final float zz = z0 + (z1 - z0) * dd / count;
            final float ss = random.nextFloat() * (count >> 4);
            final float r = ((float) Math.sin(dd * Math.PI / count) + 1) * ss + 1;
            final float hr = ((float) Math.sin(dd * Math.PI / count) + 1) * ss + 1;
            for (int x2 = (int) (xx - r / 2); x2 <= (int) (xx + r * 0.5f); x2++) {
                for (int y2 = (int) (yy - r / 2); y2 <= (int) (yy + r * 0.5f); y2++) {
                    for (int z2 = (int) (zz - r / 2); z2 <= (int) (zz + r * 0.5f); z2++) {
                        if (blockAt(region, x2, y2, z2).getBlock() == Blocks.WATER) {
                            place(region, x2, y2, z2, clay);
                        }
                    }
                }
            }
        }
    }

    /** PE TreeFeature：橡树（Java Beta 同源——treeHeight/树叶角落裁剪/树干；trunkType 简化为 oak） */
    public static boolean tree(final WorldGenRegion region, final Mt19937Random random,
                               final int x, final int y, final int z) {
        final int treeHeight = random.nextInt(3) + 4;
        if (y < 1 || y + treeHeight + 1 > 128) {
            return false;
        }
        boolean free = true;
        for (int yy = y; yy <= y + 1 + treeHeight && free; yy++) {
            int r = 1;
            if (yy == y) r = 0;
            if (yy >= y + 1 + treeHeight - 2) r = 2;
            for (int xx = x - r; xx <= x + r && free; xx++) {
                for (int zz = z - r; zz <= z + r && free; zz++) {
                    if (yy >= 0 && yy < 128) {
                        final BlockState st = blockAt(region, xx, yy, zz);
                        if (!st.isAir() && !st.is(Blocks.OAK_LEAVES)) {
                            free = false;
                        }
                    } else {
                        free = false;
                    }
                }
            }
        }
        if (!free) {
            return false;
        }
        final BlockState below = blockAt(region, x, y - 1, z);
        if (!below.is(Blocks.GRASS_BLOCK) && !below.is(Blocks.DIRT)) {
            return false;
        }
        place(region, x, y - 1, z, Blocks.DIRT.defaultBlockState());

        final BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        for (int yy = y - 3 + treeHeight; yy <= y + treeHeight; yy++) {
            final int yo = yy - (y + treeHeight);
            final int offs = 1 - yo / 2;
            for (int xx = x - offs; xx <= x + offs; xx++) {
                final int xo = xx - x;
                for (int zz = z - offs; zz <= z + offs; zz++) {
                    final int zo = zz - z;
                    if (Math.abs(xo) == offs && Math.abs(zo) == offs && (random.nextInt(2) == 0 || yo == 0)) {
                        continue;
                    }
                    if (!blockAt(region, xx, yy, zz).isAir()) {
                        continue;
                    }
                    place(region, xx, yy, zz, leaves);
                }
            }
        }
        final BlockState trunk = Blocks.OAK_LOG.defaultBlockState();
        for (int hh = 0; hh < treeHeight; hh++) {
            final BlockState t = blockAt(region, x, y + hh, z);
            if (t.isAir() || t.is(Blocks.OAK_LEAVES)) {
                place(region, x, y + hh, z, trunk);
            }
        }
        return true;
    }

    /** PE FlowerFeature：64 次随机撒（±8/±4）——canSurvive 简化为下方 solid（草/土） */
    public static void bush(final WorldGenRegion region, final Mt19937Random random,
                            final int x, final int y, final int z, final BlockState bush) {
        for (int i = 0; i < 64; i++) {
            final int x2 = x + random.nextInt(8) - random.nextInt(8);
            final int y2 = y + random.nextInt(4) - random.nextInt(4);
            final int z2 = z + random.nextInt(8) - random.nextInt(8);
            if (isEmpty(region, x2, y2, z2)) {
                final BlockState below = blockAt(region, x2, y2 - 1, z2);
                if (below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.DIRT)) {
                    place(region, x2, y2, z2, bush);
                }
            }
        }
    }

    /** PE ReedsFeature：20 次随机（±4）——水邻检查（4 方向 y-1）+ 高度 2+nextInt(nextInt(3)+1) */
    public static void reeds(final WorldGenRegion region, final Mt19937Random random, final int x, final int y, final int z) {
        final BlockState reeds = Blocks.SUGAR_CANE.defaultBlockState();
        for (int i = 0; i < 20; i++) {
            final int x2 = x + random.nextInt(4) - random.nextInt(4);
            final int z2 = z + random.nextInt(4) - random.nextInt(4);
            if (!isEmpty(region, x2, y, z2)) {
                continue;
            }
            boolean nearWater = blockAt(region, x2 - 1, y - 1, z2).is(Blocks.WATER)
                || blockAt(region, x2 + 1, y - 1, z2).is(Blocks.WATER)
                || blockAt(region, x2, y - 1, z2 - 1).is(Blocks.WATER)
                || blockAt(region, x2, y - 1, z2 + 1).is(Blocks.WATER);
            if (nearWater) {
                final int h = 2 + random.nextInt(random.nextInt(3) + 1);
                for (int yy = 0; yy < h; yy++) {
                    if (isEmpty(region, x2, y + yy, z2)) {
                        place(region, x2, y + yy, z2, reeds);
                    }
                }
            }
        }
    }

    /** PE CactusFeature：10 次随机撒（±8/±4）+ 高度 1+nextInt(nextInt(3)+1)——canSurvive 简化为下方 solid */
    public static void cactus(final WorldGenRegion region, final Mt19937Random random, final int x, final int y, final int z) {
        final BlockState cactus = Blocks.CACTUS.defaultBlockState();
        for (int i = 0; i < 10; i++) {
            final int x2 = x + random.nextInt(8) - random.nextInt(8);
            final int y2 = y + random.nextInt(4) - random.nextInt(4);
            final int z2 = z + random.nextInt(8) - random.nextInt(8);
            if (isEmpty(region, x2, y2, z2)) {
                final int h = 1 + random.nextInt(random.nextInt(3) + 1);
                for (int yy = 0; yy < h; yy++) {
                    if (isEmpty(region, x2, y2 + yy, z2)) {
                        place(region, x2, y2 + yy, z2, cactus);
                    }
                }
            }
        }
    }

    /** PE SpringFeature：泉（上下石头 + 4 邻 3 石 1 空 → 流体源；instaTick 流动简化为放置源方块） */
    public static boolean spring(final WorldGenRegion region, final Mt19937Random random,
                                 final int x, final int y, final int z, final BlockState fluid) {
        if (!isStone(region, x, y + 1, z) || !isStone(region, x, y - 1, z)) {
            return false;
        }
        final BlockState here = blockAt(region, x, y, z);
        if (!here.isAir() && !isStone(region, x, y, z)) {
            return false;
        }
        int rockCount = 0;
        if (isStone(region, x - 1, y, z)) rockCount++;
        if (isStone(region, x + 1, y, z)) rockCount++;
        if (isStone(region, x, y, z - 1)) rockCount++;
        if (isStone(region, x, y, z + 1)) rockCount++;
        int holeCount = 0;
        if (isEmpty(region, x - 1, y, z)) holeCount++;
        if (isEmpty(region, x + 1, y, z)) holeCount++;
        if (isEmpty(region, x, y, z - 1)) holeCount++;
        if (isEmpty(region, x, y, z + 1)) holeCount++;
        if (rockCount == 3 && holeCount == 1) {
            place(region, x, y, z, fluid);
            return true;
        }
        return false;
    }

    /** 水邻判定辅助（甘蔗用） */
    public static boolean isWaterAt(final WorldGenRegion region, final int x, final int y, final int z) {
        final FluidState fs = region.getBlockState(new BlockPos(x, y, z)).getFluidState();
        return fs.is(Fluids.WATER) && !fs.isEmpty();
    }
}
