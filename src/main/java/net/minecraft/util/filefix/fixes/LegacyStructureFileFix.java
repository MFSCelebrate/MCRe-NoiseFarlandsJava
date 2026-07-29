package net.minecraft.util.filefix.fixes;

import com.google.common.collect.Maps;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.util.filefix.CanceledFileFixException;
import net.minecraft.util.filefix.FileFix;
import net.minecraft.util.filefix.access.ChunkNbt;
import net.minecraft.util.filefix.access.CompressedNbt;
import net.minecraft.util.filefix.access.FileAccess;
import net.minecraft.util.filefix.access.FileAccessProvider;
import net.minecraft.util.filefix.access.FileRelation;
import net.minecraft.util.filefix.access.FileResourceTypes;
import net.minecraft.util.filefix.access.LevelDat;
import net.minecraft.util.filefix.access.SavedDataNbt;
import net.minecraft.util.worldupdate.UpgradeProgress;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public class LegacyStructureFileFix extends FileFix {
    public static final int STRUCTURE_RANGE = 8;
    public static final List<String> OVERWORLD_LEGACY_STRUCTURES = List.of("Monument", "Stronghold", "Mineshaft", "Temple", "Mansion");
    public static final Map<String, String> LEGACY_TO_CURRENT_MAP = Util.make(Maps.newHashMap(), map -> {
        map.put("Iglu", "Igloo");
        map.put("TeDP", "Desert_Pyramid");
        map.put("TeJP", "Jungle_Pyramid");
        map.put("TeSH", "Swamp_Hut");
    });
    public static final List<String> NETHER_LEGACY_STRUCTURES = List.of("Fortress");
    public static final List<String> END_LEGACY_STRUCTURES = List.of("EndCity");
    private static final ResourceKey<Level> OVERWORLD_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("overworld"));
    private static final ResourceKey<Level> NETHER_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_nether"));
    private static final ResourceKey<Level> END_KEY = ResourceKey.create(Registries.DIMENSION, Identifier.withDefaultNamespace("the_end"));

    public LegacyStructureFileFix(final Schema schema) {
        super(schema);
    }

    @Override
    public void makeFixer() {
        this.addFileContentFix(
            files -> {
                List<FileAccess<SavedDataNbt>> overworldStructureData = OVERWORLD_LEGACY_STRUCTURES.stream()
                    .map(structureId -> getLegacyStructureData(files, structureId))
                    .toList();
                RegionStorageInfo overworldInfo = new RegionStorageInfo("overworld", OVERWORLD_KEY, "chunk");
                List<FileAccess<SavedDataNbt>> netherStructureData = NETHER_LEGACY_STRUCTURES.stream()
                    .map(structureId -> getLegacyStructureData(files, structureId))
                    .toList();
                RegionStorageInfo netherInfo = new RegionStorageInfo("the_nether", NETHER_KEY, "chunk");
                List<FileAccess<SavedDataNbt>> endStructureData = END_LEGACY_STRUCTURES.stream()
                    .map(structureId -> getLegacyStructureData(files, structureId))
                    .toList();
                RegionStorageInfo endInfo = new RegionStorageInfo("the_end", END_KEY, "chunk");
                FileAccess<LevelDat> levelDat = files.getFileAccess(FileResourceTypes.LEVEL_DAT, FileRelation.ORIGIN.forFile("level.dat"));
                FileAccess<ChunkNbt> overworldChunks = files.getFileAccess(
                    FileResourceTypes.chunk(DataFixTypes.CHUNK, overworldInfo), FileRelation.OLD_OVERWORLD.resolve(FileRelation.REGION)
                );
                FileAccess<ChunkNbt> netherChunks = files.getFileAccess(
                    FileResourceTypes.chunk(DataFixTypes.CHUNK, netherInfo), FileRelation.OLD_NETHER.resolve(FileRelation.REGION)
                );
                FileAccess<ChunkNbt> endChunks = files.getFileAccess(
                    FileResourceTypes.chunk(DataFixTypes.CHUNK, endInfo), FileRelation.OLD_END.resolve(FileRelation.REGION)
                );
                return upgradeProgress -> {
                    Optional<Dynamic<Tag>> levelData = levelDat.getOnlyFile().read();
                    if (!levelData.isEmpty()) {
                        upgradeProgress.setType(UpgradeProgress.Type.LEGACY_STRUCTURES);
                        extractAndStoreLegacyStructureData(
                            levelData.get(),
                            List.of(
                                new LegacyStructureFileFix.DimensionFixEntry(
                                    OVERWORLD_KEY, overworldStructureData, overworldChunks, new Object2ObjectOpenHashMap<>()
                                ),
                                new LegacyStructureFileFix.DimensionFixEntry(NETHER_KEY, netherStructureData, netherChunks, new Object2ObjectOpenHashMap<>()),
                                new LegacyStructureFileFix.DimensionFixEntry(END_KEY, endStructureData, endChunks, new Object2ObjectOpenHashMap<>())
                            ),
                            upgradeProgress
                        );
                    }
                };
            }
        );
    }

    private static void extractAndStoreLegacyStructureData(
        final Dynamic<Tag> levelData, final List<LegacyStructureFileFix.DimensionFixEntry> dimensionFixEntries, final UpgradeProgress upgradeProgress
    ) throws IOException {
        upgradeProgress.setStatus(UpgradeProgress.Status.COUNTING);

        for (LegacyStructureFileFix.DimensionFixEntry dimensionFixEntry : dimensionFixEntries) {
            // ===== 修改：使用 Object2ObjectMap<ChunkPos, LegacyStructureData> =====
            Object2ObjectMap<ChunkPos, LegacyStructureFileFix.LegacyStructureData> structures = dimensionFixEntry.structures;

            for (FileAccess<SavedDataNbt> structureDataFileAccess : dimensionFixEntry.structureFileAccess) {
                SavedDataNbt targetFile = structureDataFileAccess.getOnlyFile();
                Optional<Dynamic<Tag>> structureData = targetFile.read();
                if (!structureData.isEmpty()) {
                    extractLegacyStructureData(structureData.get(), structures);
                }
            }

            upgradeProgress.addTotalFileFixOperations(structures.size());
        }

        upgradeProgress.setStatus(UpgradeProgress.Status.UPGRADING);

        for (LegacyStructureFileFix.DimensionFixEntry dimensionFixEntry : dimensionFixEntries) {
            ResourceKey<Level> dimensionKey = dimensionFixEntry.dimensionKey;
            ChunkNbt chunkNbt = dimensionFixEntry.chunkFileAccess.getOnlyFile();
            String chunkGeneratorType;
            if (dimensionKey == OVERWORLD_KEY) {
                String generatorName = levelData.get("generatorName").asString("buffet");

                chunkGeneratorType = switch (generatorName) {
                    case "flat" -> "minecraft:flat";
                    case "debug_all_block_states" -> "minecraft:debug";
                    default -> "minecraft:noise";
                };
            } else {
                chunkGeneratorType = "minecraft:noise";
            }

            Optional<Identifier> generatorIdentifier = Optional.ofNullable(Identifier.tryParse(chunkGeneratorType));
            CompoundTag dataFixContext = ChunkMap.getChunkDataFixContextTag(dimensionKey, generatorIdentifier);
            storeLegacyStructureDataToChunks(dimensionFixEntry.structures, chunkNbt, dataFixContext, upgradeProgress);
        }
    }

    private static FileAccess<SavedDataNbt> getLegacyStructureData(final FileAccessProvider files, final String structureId) {
        return files.getFileAccess(
            FileResourceTypes.savedData(References.SAVED_DATA_STRUCTURE_FEATURE_INDICES, CompressedNbt.MissingSeverity.MINOR),
            FileRelation.DATA.forFile(structureId + ".dat")
        );
    }

    // ===== 修改：参数类型改为 Object2ObjectMap<ChunkPos, ...> =====
    private static void extractLegacyStructureData(
        final Dynamic<Tag> structureData, final Object2ObjectMap<ChunkPos, LegacyStructureFileFix.LegacyStructureData> extractedDataContainer
    ) {
        OptionalDynamic<Tag> features = structureData.get("Features");
        Map<Dynamic<Tag>, Dynamic<Tag>> map = features.asMap(Function.identity(), Function.identity());

        for (Dynamic<Tag> value : map.values()) {
            int chunkX = value.get("ChunkX").asInt(0);
            int chunkZ = value.get("ChunkZ").asInt(0);
            ChunkPos pos = new ChunkPos(chunkX, chunkZ); // ===== 使用 new ChunkPos =====
            List<Dynamic<Tag>> childList = value.get("Children").asList(Function.identity());
            if (!childList.isEmpty()) {
                Optional<String> id = childList.getFirst().get("id").asString().result().map(LEGACY_TO_CURRENT_MAP::get);
                if (id.isPresent()) {
                    value = value.set("id", value.createString(id.get()));
                }
            }

            Dynamic<Tag> finalValue = value;
            value.get("id")
                .asString()
                .ifSuccess(
                    id -> {
                        // ===== computeIfAbsent 使用 ChunkPos 键 =====
                        extractedDataContainer.computeIfAbsent(pos, l -> new LegacyStructureFileFix.LegacyStructureData()).addStart(id, finalValue);

                        for (int neighborX = pos.x - 8; neighborX <= pos.x + 8; neighborX++) {
                            for (int neighborZ = pos.z - 8; neighborZ <= pos.z + 8; neighborZ++) {
                                // ===== 使用 new ChunkPos 作为键 =====
                                ChunkPos neighborPos = new ChunkPos(neighborX, neighborZ);
                                extractedDataContainer.computeIfAbsent(neighborPos, l -> new LegacyStructureFileFix.LegacyStructureData())
                                    .addIndex(id, pos.pack()); // 存储 pack 用于 long 数组
                            }
                        }
                    }
                );
        }
    }

    // ===== 修改：参数类型改为 Object2ObjectMap<ChunkPos, ...> =====
    private static void storeLegacyStructureDataToChunks(
        final Object2ObjectMap<ChunkPos, LegacyStructureFileFix.LegacyStructureData> structures,
        final ChunkNbt chunksAccess,
        final CompoundTag dataFixContext,
        final UpgradeProgress upgradeProgress
    ) {
        // ===== 遍历 Object2ObjectMap 的 entry set =====
        List<Object2ObjectMap.Entry<ChunkPos, LegacyStructureFileFix.LegacyStructureData>> entries = new ArrayList<>(structures.object2ObjectEntrySet());
        // ===== 排序：按 region 顺序，使用 pack 仅用于排序 =====
        entries.sort(Comparator.comparingLong(entry -> {
            ChunkPos cp = entry.getKey();
            return (cp.getRegionX() << 32) | (cp.getRegionZ() & 0xFFFFFFFFL);
        }));
        LegacyStructureFileFix.IncrementalFutureSequence futures = new LegacyStructureFileFix.IncrementalFutureSequence(8);

        for (Object2ObjectMap.Entry<ChunkPos, LegacyStructureFileFix.LegacyStructureData> entry : entries) {
            if (upgradeProgress.isCanceled()) {
                throw new CanceledFileFixException();
            }

            ChunkPos pos = entry.getKey();
            LegacyStructureFileFix.LegacyStructureData legacyData = entry.getValue();
            int finished = futures.push(chunksAccess.updateChunk(pos, dataFixContext, tag -> {
                CompoundTag levelTag = tag.getCompoundOrEmpty("Level");
                CompoundTag structureTag = levelTag.getCompoundOrEmpty("Structures");
                CompoundTag startTag = structureTag.getCompoundOrEmpty("Starts");
                CompoundTag referencesTag = structureTag.getCompoundOrEmpty("References");
                legacyData.starts().forEach((id, value) -> startTag.put(id, value.convert(NbtOps.INSTANCE).getValue()));
                legacyData.indexes().forEach((id, indexes) -> referencesTag.putLongArray(id, indexes.toLongArray()));
                structureTag.put("Starts", startTag);
                structureTag.put("References", referencesTag);
                levelTag.put("Structures", structureTag);
                tag.put("Level", levelTag);
                return tag;
            }));
            upgradeProgress.incrementFinishedOperationsBy(finished);
        }

        upgradeProgress.incrementFinishedOperationsBy(futures.waitForAll());
    }

    // ===== 修改：DimensionFixEntry 的 structures 字段类型 =====
    private record DimensionFixEntry(
        ResourceKey<Level> dimensionKey,
        List<FileAccess<SavedDataNbt>> structureFileAccess,
        FileAccess<ChunkNbt> chunkFileAccess,
        Object2ObjectOpenHashMap<ChunkPos, LegacyStructureFileFix.LegacyStructureData> structures
    ) {
    }

    private static class IncrementalFutureSequence {
        private final int maxConcurrency;
        private final List<CompletableFuture<?>> futures;

        public IncrementalFutureSequence(final int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            this.futures = new ArrayList<>(maxConcurrency);
        }

        public int push(final CompletableFuture<?> future) {
            int finished = 0;
            if (this.futures.size() >= this.maxConcurrency) {
                finished += this.waitOnAny();
            }

            this.futures.add(future);
            return finished;
        }

        private int waitOnAny() {
            int oldSize = this.futures.size();
            CompletableFuture.anyOf(this.futures.toArray(CompletableFuture[]::new)).join();
            this.futures.removeIf(CompletableFuture::isDone);
            return oldSize - this.futures.size();
        }

        public int waitForAll() {
            int oldSize = this.futures.size();
            CompletableFuture.allOf(this.futures.toArray(CompletableFuture[]::new)).join();
            this.futures.clear();
            return oldSize;
        }
    }

    public record LegacyStructureData(Map<String, Dynamic<?>> starts, Map<String, LongList> indexes) {
        public LegacyStructureData() {
            this(new HashMap<>(), new HashMap<>());
        }

        public void addStart(final String id, final Dynamic<Tag> data) {
            this.starts.put(id, data);
        }

        // ===== 修改：参数改为 ChunkPos，内部调用 pack() 存储 long =====
        public void addIndex(final String id, final ChunkPos pos) {
            this.indexes.computeIfAbsent(id, l -> new LongArrayList()).add(pos.pack());
        }

        // 为了兼容旧调用，保留一个接收 long 的重载（但避免使用）
        @Deprecated
        public void addIndex(final String id, final long sourcePos) {
            this.indexes.computeIfAbsent(id, l -> new LongArrayList()).add(sourcePos);
        }
    }
}