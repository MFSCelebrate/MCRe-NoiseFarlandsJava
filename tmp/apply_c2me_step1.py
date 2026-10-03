#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""C2ME step1 / B1 结构生成线程安全地基：精确文本替换 + 次数断言（dry-run 默认，--apply 落盘）"""
import sys

BASE = "/workspace/src/main/java"
SP = BASE + "/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java"
NF = BASE + "/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java"
MS = BASE + "/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java"
SW = BASE + "/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java"
DP = BASE + "/net/minecraft/world/level/levelgen/structure/structures/DesertPyramidPiece.java"
JT = BASE + "/net/minecraft/world/level/levelgen/structure/structures/JungleTemplePiece.java"
LV = BASE + "/net/minecraft/world/level/Level.java"

EDITS = [
    # ============ StrongholdPieces：3 个 static 共享 → ThreadLocal 副本 ============
    (SP,
"""    private static List<StrongholdPieces.PieceWeight> currentPieces;
    private static @Nullable Class<? extends StrongholdPieces.StrongholdPiece> imposedPiece;
    private static int totalWeight;""",
"""    // 🔧 C2ME 多线程地基：static 共享状态 → ThreadLocal 副本（多线程同时生成要塞 chunk 时隔离竞争，防结构概率偏差）
    private static final ThreadLocal<List<StrongholdPieces.PieceWeight>> currentPieces = ThreadLocal.withInitial(Lists::newArrayList);
    private static final ThreadLocal<@Nullable Class<? extends StrongholdPieces.StrongholdPiece>> imposedPiece = new ThreadLocal<>();
    private static final ThreadLocal<Integer> totalWeight = ThreadLocal.withInitial(() -> 0);""",
     1),
    (SP,
"""    public static void resetPieces() {
        currentPieces = Lists.newArrayList();

        for (StrongholdPieces.PieceWeight piece : STRONGHOLD_PIECE_WEIGHTS) {
            piece.placeCount = 0;
            currentPieces.add(piece);
        }

        imposedPiece = null;
    }""",
"""    public static void resetPieces() {
        currentPieces.set(Lists.newArrayList());

        for (StrongholdPieces.PieceWeight piece : STRONGHOLD_PIECE_WEIGHTS) {
            piece.setPlaceCount(0);
            currentPieces.get().add(piece);
        }

        imposedPiece.set(null);
    }""",
     1),
    (SP,
"""    private static boolean updatePieceWeight() {
        boolean hasAnyPieces = false;
        totalWeight = 0;

        for (StrongholdPieces.PieceWeight piece : currentPieces) {
            if (piece.maxPlaceCount > 0 && piece.placeCount < piece.maxPlaceCount) {
                hasAnyPieces = true;
            }

            totalWeight = totalWeight + piece.weight;
        }""",
"""    private static boolean updatePieceWeight() {
        boolean hasAnyPieces = false;
        int weight = 0;

        for (StrongholdPieces.PieceWeight piece : currentPieces.get()) {
            if (piece.maxPlaceCount > 0 && piece.getPlaceCount() < piece.maxPlaceCount) {
                hasAnyPieces = true;
            }

            weight = weight + piece.weight;
        }

        totalWeight.set(weight);""",
     1),
    (SP,
"""        if (imposedPiece != null) {
            StrongholdPieces.StrongholdPiece strongholdPiece = findAndCreatePieceFactory(
                imposedPiece, structurePieceAccessor, random, footX, footY, footZ, direction, depth
            );
            imposedPiece = null;""",
"""        if (imposedPiece.get() != null) {
            StrongholdPieces.StrongholdPiece strongholdPiece = findAndCreatePieceFactory(
                imposedPiece.get(), structurePieceAccessor, random, footX, footY, footZ, direction, depth
            );
            imposedPiece.set(null);""",
     1),
    (SP,
"""            int weightSelection = random.nextInt(totalWeight);

            for (StrongholdPieces.PieceWeight piece : currentPieces) {
                weightSelection -= piece.weight;
                if (weightSelection < 0) {
                    if (!piece.doPlace(depth) || piece == startPiece.previousPiece) {
                        break;
                    }

                    StrongholdPieces.StrongholdPiece strongholdPiece = findAndCreatePieceFactory(
                        piece.pieceClass, structurePieceAccessor, random, footX, footY, footZ, direction, depth
                    );
                    if (strongholdPiece != null) {
                        piece.placeCount++;
                        startPiece.previousPiece = piece;
                        if (!piece.isValid()) {
                            currentPieces.remove(piece);
                        }""",
"""            int weightSelection = random.nextInt(totalWeight.get());

            for (StrongholdPieces.PieceWeight piece : currentPieces.get()) {
                weightSelection -= piece.weight;
                if (weightSelection < 0) {
                    if (!piece.doPlace(depth) || piece == startPiece.previousPiece) {
                        break;
                    }

                    StrongholdPieces.StrongholdPiece strongholdPiece = findAndCreatePieceFactory(
                        piece.pieceClass, structurePieceAccessor, random, footX, footY, footZ, direction, depth
                    );
                    if (strongholdPiece != null) {
                        piece.setPlaceCount(piece.getPlaceCount() + 1);
                        startPiece.previousPiece = piece;
                        if (!piece.isValid()) {
                            currentPieces.get().remove(piece);
                        }""",
     1),
    (SP,
"""    private static class PieceWeight {
        public final Class<? extends StrongholdPieces.StrongholdPiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;""",
"""    private static class PieceWeight {
        public final Class<? extends StrongholdPieces.StrongholdPiece> pieceClass;
        public final int weight;
        // 🔧 C2ME 多线程地基：placeCount → 每实例每线程独立计数（PieceWeight 实例是 static 共享，防概率竞争）
        private final ThreadLocal<Integer> placeCount = ThreadLocal.withInitial(() -> 0);
        public final int maxPlaceCount;""",
     1),
    (SP,
"""        public boolean doPlace(final int depth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }""",
"""        public int getPlaceCount() {
            return this.placeCount.get();
        }

        public void setPlaceCount(final int value) {
            if (value == 0) {
                this.placeCount.remove();
            } else {
                this.placeCount.set(value);
            }
        }

        public boolean doPlace(final int depth) {
            return this.maxPlaceCount == 0 || this.getPlaceCount() < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.getPlaceCount() < this.maxPlaceCount;
        }
    }""",
     1),
    (SP,
"""            if (this.isSource) {
                StrongholdPieces.imposedPiece = StrongholdPieces.FiveCrossing.class;
            }""",
"""            if (this.isSource) {
                StrongholdPieces.imposedPiece.set(StrongholdPieces.FiveCrossing.class);
            }""",
     1),
    # ============ NetherFortressPieces：PieceWeight.placeCount → 每实例每线程 ============
    (NF,
"""    private static class PieceWeight {
        public final Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass;
        public final int weight;
        public int placeCount;
        public final int maxPlaceCount;
        public final boolean allowInRow;""",
"""    private static class PieceWeight {
        public final Class<? extends NetherFortressPieces.NetherBridgePiece> pieceClass;
        public final int weight;
        // 🔧 C2ME 多线程地基：placeCount → 每实例每线程独立计数（BRIDGE/CASTLE_PIECE_WEIGHTS 是 static 共享实例，防概率竞争）
        private final ThreadLocal<Integer> placeCount = ThreadLocal.withInitial(() -> 0);
        public final int maxPlaceCount;
        public final boolean allowInRow;""",
     1),
    (NF,
"""        public boolean doPlace(final int depth) {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.placeCount < this.maxPlaceCount;
        }
    }""",
"""        public int getPlaceCount() {
            return this.placeCount.get();
        }

        public void setPlaceCount(final int value) {
            if (value == 0) {
                this.placeCount.remove();
            } else {
                this.placeCount.set(value);
            }
        }

        public boolean doPlace(final int depth) {
            return this.maxPlaceCount == 0 || this.getPlaceCount() < this.maxPlaceCount;
        }

        public boolean isValid() {
            return this.maxPlaceCount == 0 || this.getPlaceCount() < this.maxPlaceCount;
        }
    }""",
     1),
    (NF,
"""            for (NetherFortressPieces.PieceWeight piece : currentPieces) {
                if (piece.maxPlaceCount > 0 && piece.placeCount < piece.maxPlaceCount) {""",
"""            for (NetherFortressPieces.PieceWeight piece : currentPieces) {
                if (piece.maxPlaceCount > 0 && piece.getPlaceCount() < piece.maxPlaceCount) {""",
     1),
    (NF,
"""                        if (structurePiece != null) {
                            piece.placeCount++;
                            startPiece.previousPiece = piece;
                            if (!piece.isValid()) {
                                currentPieces.remove(piece);""",
"""                        if (structurePiece != null) {
                            piece.setPlaceCount(piece.getPlaceCount() + 1);
                            startPiece.previousPiece = piece;
                            if (!piece.isValid()) {
                                currentPieces.remove(piece);""",
     1),
    (NF,
"""                piece.placeCount = 0;""",
"""                piece.setPlaceCount(0);""",
     2),
    # ============ volatile 实例字段（C2ME MakeVolatile 清单 → 26.2 对应） ============
    (NF,
"""        private boolean isNeedingChest;""",
"""        private volatile boolean isNeedingChest;""",
     2),
    (NF,
"""        private boolean hasPlacedSpawner;""",
"""        private volatile boolean hasPlacedSpawner;""",
     1),
    (MS,
"""        private boolean hasPlacedSpider;""",
"""        private volatile boolean hasPlacedSpider;""",
     1),
    (SW,
"""    private boolean spawnedWitch;
    private boolean spawnedCat;""",
"""    private volatile boolean spawnedWitch;
    private volatile boolean spawnedCat;""",
     1),
    (SP,
"""        private boolean hasPlacedChest;""",
"""        private volatile boolean hasPlacedChest;""",
     1),
    (SP,
"""        private boolean hasPlacedSpawner;""",
"""        private volatile boolean hasPlacedSpawner;""",
     1),
    # ============ DesertPyramidPiece：boolean[4] → AtomicIntegerArray（含 NBT 读写同步适配） ============
    (DP,
"""import java.util.ArrayList;
import java.util.List;""",
"""import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;""",
     1),
    (DP,
"""    private final boolean[] hasPlacedChest = new boolean[4];""",
"""    private final AtomicIntegerArray hasPlacedChest = new AtomicIntegerArray(4);""",
     1),
    (DP,
"""        this.hasPlacedChest[0] = tag.getBooleanOr("hasPlacedChest0", false);
        this.hasPlacedChest[1] = tag.getBooleanOr("hasPlacedChest1", false);
        this.hasPlacedChest[2] = tag.getBooleanOr("hasPlacedChest2", false);
        this.hasPlacedChest[3] = tag.getBooleanOr("hasPlacedChest3", false);""",
"""        this.hasPlacedChest.set(0, tag.getBooleanOr("hasPlacedChest0", false) ? 1 : 0);
        this.hasPlacedChest.set(1, tag.getBooleanOr("hasPlacedChest1", false) ? 1 : 0);
        this.hasPlacedChest.set(2, tag.getBooleanOr("hasPlacedChest2", false) ? 1 : 0);
        this.hasPlacedChest.set(3, tag.getBooleanOr("hasPlacedChest3", false) ? 1 : 0);""",
     1),
    (DP,
"""        tag.putBoolean("hasPlacedChest0", this.hasPlacedChest[0]);
        tag.putBoolean("hasPlacedChest1", this.hasPlacedChest[1]);
        tag.putBoolean("hasPlacedChest2", this.hasPlacedChest[2]);
        tag.putBoolean("hasPlacedChest3", this.hasPlacedChest[3]);""",
"""        tag.putBoolean("hasPlacedChest0", this.hasPlacedChest.get(0) != 0);
        tag.putBoolean("hasPlacedChest1", this.hasPlacedChest.get(1) != 0);
        tag.putBoolean("hasPlacedChest2", this.hasPlacedChest.get(2) != 0);
        tag.putBoolean("hasPlacedChest3", this.hasPlacedChest.get(3) != 0);""",
     1),
    (DP,
"""                if (!this.hasPlacedChest[direction.get2DDataValue()]) {
                    int xo = direction.getStepX() * 2;
                    int zo = direction.getStepZ() * 2;
                    this.hasPlacedChest[direction.get2DDataValue()] = this.createChest(
                        level, chunkBB, random, 10 + xo, -11, 10 + zo, BuiltInLootTables.DESERT_PYRAMID
                    );
                }""",
"""                if (this.hasPlacedChest.get(direction.get2DDataValue()) == 0) {
                    int xo = direction.getStepX() * 2;
                    int zo = direction.getStepZ() * 2;
                    this.hasPlacedChest.set(
                        direction.get2DDataValue(), this.createChest(
                            level, chunkBB, random, 10 + xo, -11, 10 + zo, BuiltInLootTables.DESERT_PYRAMID
                        ) ? 1 : 0
                    );
                }""",
     1),
    # ============ JungleTemplePiece：4 个 boolean → AtomicBoolean（含 NBT 读写同步适配） ============
    (JT,
"""import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;""",
"""import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;""",
     1),
    (JT,
"""    private boolean placedMainChest;
    private boolean placedHiddenChest;
    private boolean placedTrap1;
    private boolean placedTrap2;""",
"""    private final AtomicBoolean placedMainChest = new AtomicBoolean();
    private final AtomicBoolean placedHiddenChest = new AtomicBoolean();
    private final AtomicBoolean placedTrap1 = new AtomicBoolean();
    private final AtomicBoolean placedTrap2 = new AtomicBoolean();""",
     1),
    (JT,
"""        this.placedMainChest = tag.getBooleanOr("placedMainChest", false);
        this.placedHiddenChest = tag.getBooleanOr("placedHiddenChest", false);
        this.placedTrap1 = tag.getBooleanOr("placedTrap1", false);
        this.placedTrap2 = tag.getBooleanOr("placedTrap2", false);""",
"""        this.placedMainChest.set(tag.getBooleanOr("placedMainChest", false));
        this.placedHiddenChest.set(tag.getBooleanOr("placedHiddenChest", false));
        this.placedTrap1.set(tag.getBooleanOr("placedTrap1", false));
        this.placedTrap2.set(tag.getBooleanOr("placedTrap2", false));""",
     1),
    (JT,
"""        tag.putBoolean("placedMainChest", this.placedMainChest);
        tag.putBoolean("placedHiddenChest", this.placedHiddenChest);
        tag.putBoolean("placedTrap1", this.placedTrap1);
        tag.putBoolean("placedTrap2", this.placedTrap2);""",
"""        tag.putBoolean("placedMainChest", this.placedMainChest.get());
        tag.putBoolean("placedHiddenChest", this.placedHiddenChest.get());
        tag.putBoolean("placedTrap1", this.placedTrap1.get());
        tag.putBoolean("placedTrap2", this.placedTrap2.get());""",
     1),
    (JT,
"""            if (!this.placedTrap1) {
                this.placedTrap1 = this.createDispenser(level, chunkBB, random, 3, -2, 1, Direction.NORTH, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER);""",
"""            if (!this.placedTrap1.get()) {
                this.placedTrap1.set(this.createDispenser(level, chunkBB, random, 3, -2, 1, Direction.NORTH, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER));""",
     1),
    (JT,
"""            if (!this.placedTrap2) {
                this.placedTrap2 = this.createDispenser(level, chunkBB, random, 9, -2, 3, Direction.WEST, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER);""",
"""            if (!this.placedTrap2.get()) {
                this.placedTrap2.set(this.createDispenser(level, chunkBB, random, 9, -2, 3, Direction.WEST, BuiltInLootTables.JUNGLE_TEMPLE_DISPENSER));""",
     1),
    (JT,
"""            if (!this.placedMainChest) {
                this.placedMainChest = this.createChest(level, chunkBB, random, 8, -3, 3, BuiltInLootTables.JUNGLE_TEMPLE);""",
"""            if (!this.placedMainChest.get()) {
                this.placedMainChest.set(this.createChest(level, chunkBB, random, 8, -3, 3, BuiltInLootTables.JUNGLE_TEMPLE));""",
     1),
    (JT,
"""            if (!this.placedHiddenChest) {
                this.placedHiddenChest = this.createChest(level, chunkBB, random, 9, -3, 10, BuiltInLootTables.JUNGLE_TEMPLE);""",
"""            if (!this.placedHiddenChest.get()) {
                this.placedHiddenChest.set(this.createChest(level, chunkBB, random, 9, -3, 10, BuiltInLootTables.JUNGLE_TEMPLE));""",
     1),
    # ============ Level.java：B2 世界随机线程属主检查 ============
    (LV,
"""import net.minecraft.util.RandomSource;""",
"""import net.minecraft.util.CheckedRandomSource;
import net.minecraft.util.RandomSource;""",
     1),
    (LV,
"""    protected final RandomSource random = RandomSource.create();""",
"""    // 🔧 MCRe（C2ME 多线程地基移植）：世界随机带线程属主检查（owner = 世界主线程；异步线程乱用自动 FALLBACK + 去重报警）
    protected final RandomSource random = new CheckedRandomSource(RandomSource.create(), () -> this.thread);""",
     1),
]

def main():
    apply = "--apply" in sys.argv
    files = {}
    failures = 0
    for path, old, new, cnt in EDITS:
        if path not in files:
            with open(path, encoding="utf-8") as f:
                files[path] = f.read()
        n = files[path].count(old)
        if n != cnt:
            print("MISMATCH %s expect=%d got=%d :: %r" % (path.split("/")[-1], cnt, n, old[:70]))
            failures += 1
    if failures:
        print("ABORT: %d mismatches, nothing written" % failures)
        sys.exit(1)
    if not apply:
        print("DRY RUN OK: %d edits, all counts match" % len(EDITS))
        return
    for path, old, new, cnt in EDITS:
        files[path] = files[path].replace(old, new)
    for path, text in files.items():
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
        print("APPLIED %s" % path.split("/")[-1])
    print("ALL APPLIED: %d edits across %d files" % (len(EDITS), len(files)))

main()