package net.minecraft.server.level;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ByteMap;
import it.unimi.dsi.fastutil.objects.Object2ByteOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtException;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.Mth;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.TriState;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.util.thread.ConsecutiveExecutor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.boss.enderdragon.EnderDragonPart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;
import net.minecraft.world.level.chunk.status.ChunkType;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class ChunkMap extends SimpleRegionStorage implements ChunkHolder.PlayerProvider, GeneratingChunkMap {
    private static final ChunkResult<List<ChunkAccess>> UNLOADED_CHUNK_LIST_RESULT = ChunkResult.error("Unloaded chunks found in range");
    private static final CompletableFuture<ChunkResult<List<ChunkAccess>>> UNLOADED_CHUNK_LIST_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK_LIST_RESULT);
    private static final byte CHUNK_TYPE_REPLACEABLE = -1;
    private static final byte CHUNK_TYPE_UNKNOWN = 0;
    private static final byte CHUNK_TYPE_FULL = 1;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int CHUNK_SAVED_PER_TICK = 200;
    private static final int CHUNK_SAVED_EAGERLY_PER_TICK = 20;
    private static final int EAGER_CHUNK_SAVE_COOLDOWN_IN_MILLIS = 10000;
    private static final int MAX_ACTIVE_CHUNK_WRITES = 128;
    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;
    public static final int FORCED_TICKET_LEVEL = ChunkLevel.byStatus(FullChunkStatus.ENTITY_TICKING);

    // ===== 键改为 ChunkPos 对象 =====
    private final Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> updatingChunkMap = new Object2ObjectLinkedOpenHashMap<>();
    private volatile Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> visibleChunkMap = this.updatingChunkMap.clone();
    private final Object2ObjectLinkedOpenHashMap<ChunkPos, ChunkHolder> pendingUnloads = new Object2ObjectLinkedOpenHashMap<>();
    private final List<ChunkGenerationTask> pendingGenerationTasks = new ArrayList<>();
    private final ServerLevel level;
    private final ThreadedLevelLightEngine lightEngine;
    private final BlockableEventLoop<Runnable> mainThreadExecutor;
    private final RandomState randomState;
    private final ChunkGeneratorStructureState chunkGeneratorState;
    private final TicketStorage ticketStorage;
    private final PoiManager poiManager;
    private final ObjectOpenHashSet<ChunkPos> toDrop = new ObjectOpenHashSet<>();
    private boolean modified;
    private final ChunkTaskDispatcher worldgenTaskDispatcher;
    private final ChunkTaskDispatcher lightTaskDispatcher;
    private final ChunkStatusUpdateListener chunkStatusListener;
    private final ChunkMap.DistanceManager distanceManager;
    private final String storageName;
    private final PlayerMap playerMap = new PlayerMap();
    private final Int2ObjectMap<ChunkMap.TrackedEntity> entityMap = new Int2ObjectOpenHashMap<>();
    private final Object2ByteMap<ChunkPos> chunkTypeCache = new Object2ByteOpenHashMap<>();
    private final Object2LongMap<ChunkPos> nextChunkSaveTime = new Object2LongOpenHashMap<>();
    private final ObjectLinkedOpenHashSet<ChunkPos> chunksToEagerlySave = new ObjectLinkedOpenHashSet<>();
    private final Queue<Runnable> unloadQueue = Queues.newConcurrentLinkedQueue();
    private final AtomicInteger activeChunkWrites = new AtomicInteger();
    private int serverViewDistance;
    private final WorldGenContext worldGenContext;

    // ... 构造函数保持不变，但内部使用的 ChunkPos 对象替换 ...

    public ChunkMap(
            final ServerLevel level,
            final LevelStorageSource.LevelStorageAccess levelStorage,
            final DataFixer dataFixer,
            final StructureTemplateManager structureManager,
            final Executor executor,
            final BlockableEventLoop<Runnable> mainThreadExecutor,
            final LightChunkGetter chunkGetter,
            final ChunkGenerator generator,
            final ChunkStatusUpdateListener chunkStatusListener,
            final Supplier<SavedDataStorage> overworldDataStorage,
            final TicketStorage ticketStorage,
            final int serverViewDistance,
            final boolean syncWrites
    ) {
        super(
                new RegionStorageInfo(levelStorage.getLevelId(), level.dimension(), "chunk"),
                levelStorage.getDimensionPath(level.dimension()).resolve("region"),
                dataFixer,
                syncWrites,
                DataFixTypes.CHUNK
        );
        Path storageFolder = levelStorage.getDimensionPath(level.dimension());
        this.storageName = storageFolder.getFileName().toString();
        this.level = level;
        RegistryAccess registryAccess = level.registryAccess();
        long levelSeed = level.getSeed();
        if (generator instanceof NoiseBasedChunkGenerator noiseGenerator) {
            this.randomState = RandomState.create(noiseGenerator.generatorSettings().value(), registryAccess.lookupOrThrow(Registries.NOISE), levelSeed);
        } else {
            this.randomState = RandomState.create(NoiseGeneratorSettings.dummy(), registryAccess.lookupOrThrow(Registries.NOISE), levelSeed);
        }
        this.chunkGeneratorState = generator.createState(registryAccess.lookupOrThrow(Registries.STRUCTURE_SET), this.randomState, levelSeed);
        this.mainThreadExecutor = mainThreadExecutor;
        ConsecutiveExecutor worldgen = new ConsecutiveExecutor(executor, "worldgen");
        this.chunkStatusListener = chunkStatusListener;
        ConsecutiveExecutor light = new ConsecutiveExecutor(executor, "light");
        this.worldgenTaskDispatcher = new ChunkTaskDispatcher(worldgen, executor);
        this.lightTaskDispatcher = new ChunkTaskDispatcher(light, executor);
        this.lightEngine = new ThreadedLevelLightEngine(chunkGetter, this, this.level.dimensionType().hasSkyLight(), light, this.lightTaskDispatcher);
        this.distanceManager = new ChunkMap.DistanceManager(ticketStorage, executor, mainThreadExecutor);
        this.ticketStorage = ticketStorage;
        this.poiManager = new PoiManager(
                new RegionStorageInfo(levelStorage.getLevelId(), level.dimension(), "poi"),
                storageFolder.resolve("poi"),
                dataFixer,
                syncWrites,
                registryAccess,
                level.getServer(),
                level
        );
        this.setServerViewDistance(serverViewDistance);
        this.worldGenContext = new WorldGenContext(level, generator, structureManager, this.lightEngine, mainThreadExecutor, this::setChunkUnsaved);
    }

    private void setChunkUnsaved(final ChunkPos chunkPos) {
        this.chunksToEagerlySave.add(chunkPos);
    }

    // ===== 方法签名全部改为 ChunkPos =====

    public boolean isChunkTracked(final ServerPlayer player, final ChunkPos pos) {
        return player.getChunkTrackingView().contains(pos) && !player.connection.chunkSender.isPending(pos);
    }

    private boolean isChunkOnTrackedBorder(final ServerPlayer player, final ChunkPos pos) {
        if (!this.isChunkTracked(player, pos)) return false;
        int x = pos.x, z = pos.z;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && !this.isChunkTracked(player, new ChunkPos(x + dx, z + dz))) {
                    return true;
                }
            }
        }
        return false;
    }

    protected ThreadedLevelLightEngine getLightEngine() { return this.lightEngine; }

    public @Nullable ChunkHolder getUpdatingChunkIfPresent(final ChunkPos pos) {
        return this.updatingChunkMap.get(pos);
    }

    protected @Nullable ChunkHolder getVisibleChunkIfPresent(final ChunkPos pos) {
        return this.visibleChunkMap.get(pos);
    }

    public @Nullable ChunkStatus getLatestStatus(final ChunkPos pos) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        return holder != null ? holder.getLatestStatus() : null;
    }

    protected IntSupplier getChunkQueueLevel(final ChunkPos pos) {
        return () -> {
            ChunkHolder chunk = this.getVisibleChunkIfPresent(pos);
            return chunk == null ? ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1
                    : Math.min(chunk.getQueueLevel(), ChunkTaskPriorityQueue.PRIORITY_LEVEL_COUNT - 1);
        };
    }

    public String getChunkDebugData(final ChunkPos pos) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        if (holder == null) return "null";
        String result = holder.getTicketLevel() + "\n";
        ChunkStatus status = holder.getLatestStatus();
        ChunkAccess chunk = holder.getLatestChunk();
        if (status != null) result = result + "St: §" + status.getIndex() + status + "§r\n";
        if (chunk != null) result = result + "Ch: §" + chunk.getPersistedStatus().getIndex() + chunk.getPersistedStatus() + "§r\n";
        FullChunkStatus fullStatus = holder.getFullStatus();
        result = result + '§' + fullStatus.ordinal() + fullStatus;
        return result + "§r";
    }

    CompletableFuture<ChunkResult<List<ChunkAccess>>> getChunkRangeFuture(
            final ChunkHolder centerChunk, final int range, final IntFunction<ChunkStatus> distanceToStatus
    ) {
        if (range == 0) {
            ChunkStatus status = distanceToStatus.apply(0);
            return centerChunk.scheduleChunkGenerationTask(status, this).thenApply(r -> r.map(List::of));
        }
        int chunkCount = Mth.square(range * 2 + 1);
        List<CompletableFuture<ChunkResult<ChunkAccess>>> deps = new ArrayList<>(chunkCount);
        ChunkPos centerPos = centerChunk.getPos();
        for (int z = -range; z <= range; z++) {
            for (int x = -range; x <= range; x++) {
                int distance = Math.max(Math.abs(x), Math.abs(z));
                ChunkPos pos = new ChunkPos(centerPos.x + x, centerPos.z + z);
                ChunkHolder chunk = this.getUpdatingChunkIfPresent(pos);
                if (chunk == null) return UNLOADED_CHUNK_LIST_FUTURE;
                ChunkStatus depStatus = distanceToStatus.apply(distance);
                deps.add(chunk.scheduleChunkGenerationTask(depStatus, this));
            }
        }
        return Util.sequence(deps).thenApply(chunkResults -> {
            List<ChunkAccess> chunks = new ArrayList<>(chunkResults.size());
            for (ChunkResult<ChunkAccess> result : chunkResults) {
                if (result == null) throw this.debugFuturesAndCreateReportedException(new IllegalStateException("Null chunk future"), "n/a");
                ChunkAccess c = result.orElse(null);
                if (c == null) return UNLOADED_CHUNK_LIST_RESULT;
                chunks.add(c);
            }
            return ChunkResult.of(chunks);
        });
    }

    public ReportedException debugFuturesAndCreateReportedException(final IllegalStateException exception, final String details) {
        StringBuilder sb = new StringBuilder();
        Consumer<ChunkHolder> addToDebug = holder -> holder.getAllFutures().forEach(pair -> {
            ChunkStatus status = pair.getFirst();
            CompletableFuture<ChunkResult<ChunkAccess>> future = pair.getSecond();
            if (future != null && future.isDone() && future.join() == null) {
                sb.append(holder.getPos()).append(" - status: ").append(status).append(" future: ").append(future).append(System.lineSeparator());
            }
        });
        sb.append("Updating:").append(System.lineSeparator());
        this.updatingChunkMap.values().forEach(addToDebug);
        sb.append("Visible:").append(System.lineSeparator());
        this.visibleChunkMap.values().forEach(addToDebug);
        CrashReport report = CrashReport.forThrowable(exception, "Chunk loading");
        CrashReportCategory category = report.addCategory("Chunk loading");
        category.setDetail("Details", details);
        category.setDetail("Futures", sb);
        return new ReportedException(report);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareEntityTickingChunk(final ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 2, distance -> ChunkStatus.FULL)
                .thenApply(chunkResult -> chunkResult.map(list -> (LevelChunk) list.get(list.size() / 2)));
    }

    private @Nullable ChunkHolder updateChunkScheduling(final ChunkPos pos, final int level, @Nullable ChunkHolder chunk, final int oldLevel) {
        if (!ChunkLevel.isLoaded(oldLevel) && !ChunkLevel.isLoaded(level)) return chunk;
        if (chunk != null) chunk.setTicketLevel(level);
        if (chunk != null) {
            if (!ChunkLevel.isLoaded(level)) this.toDrop.add(pos);
            else this.toDrop.remove(pos);
        }
        if (ChunkLevel.isLoaded(level) && chunk == null) {
            chunk = this.pendingUnloads.remove(pos);
            if (chunk != null) chunk.setTicketLevel(level);
            else chunk = new ChunkHolder(pos, level, this.level, this.lightEngine, this::onLevelChange, this);
            this.updatingChunkMap.put(pos, chunk);
            this.modified = true;
        }
        return chunk;
    }

    private void onLevelChange(final ChunkPos pos, final IntSupplier oldLevel, final int newLevel, final IntConsumer setQueueLevel) {
        this.worldgenTaskDispatcher.onLevelChange(pos, oldLevel, newLevel, setQueueLevel);
        this.lightTaskDispatcher.onLevelChange(pos, oldLevel, newLevel, setQueueLevel);
    }

    @Override
    public void close() throws IOException {
        try {
            this.worldgenTaskDispatcher.close();
            this.lightTaskDispatcher.close();
            this.poiManager.close();
        } finally {
            super.close();
        }
    }

    protected void saveAllChunks(final boolean flushStorage) {
        if (flushStorage) {
            List<ChunkHolder> chunksToSave = this.visibleChunkMap.values().stream()
                    .filter(ChunkHolder::wasAccessibleSinceLastSave)
                    .peek(ChunkHolder::refreshAccessibility)
                    .toList();
            MutableBoolean didWork = new MutableBoolean();
            do {
                didWork.setFalse();
                chunksToSave.stream()
                        .map(chunk -> {
                            this.mainThreadExecutor.managedBlock(chunk::isReadyForSaving);
                            return chunk.getLatestChunk();
                        })
                        .filter(c -> c instanceof ImposterProtoChunk || c instanceof LevelChunk)
                        .filter(this::save)
                        .forEach(c -> didWork.setTrue());
            } while (didWork.isTrue());
            this.poiManager.flushAll();
            this.processUnloads(() -> true);
            this.synchronize(true).join();
        } else {
            this.nextChunkSaveTime.clear();
            long now = Util.getMillis();
            for (ChunkHolder chunk : this.visibleChunkMap.values()) {
                this.saveChunkIfNeeded(chunk, now);
            }
        }
    }

    protected void tick(final BooleanSupplier haveTime) {
        ProfilerFiller profiler = Profiler.get();
        profiler.push("poi");
        this.poiManager.tick(haveTime);
        profiler.popPush("chunk_unload");
        if (!this.level.noSave()) this.processUnloads(haveTime);
        profiler.pop();
    }

    public boolean hasWork() {
        return this.lightEngine.hasLightWork()
                || !this.pendingUnloads.isEmpty()
                || !this.updatingChunkMap.isEmpty()
                || this.poiManager.hasWork()
                || !this.toDrop.isEmpty()
                || !this.unloadQueue.isEmpty()
                || this.worldgenTaskDispatcher.hasWork()
                || this.lightTaskDispatcher.hasWork()
                || this.distanceManager.hasTickets();
    }

    private void processUnloads(final BooleanSupplier haveTime) {
        for (ObjectIterator<ChunkPos> iterator = this.toDrop.iterator(); iterator.hasNext(); ) {
            ChunkPos pos = iterator.next();
            ChunkHolder holder = this.updatingChunkMap.get(pos);
            if (holder != null) {
                this.updatingChunkMap.remove(pos);
                this.pendingUnloads.put(pos, holder);
                this.modified = true;
                this.scheduleUnload(pos, holder);
            }
            iterator.remove();
        }
        int minimal = Math.max(0, this.unloadQueue.size() - 2000);
        Runnable task;
        while ((minimal > 0 || haveTime.getAsBoolean()) && (task = this.unloadQueue.poll()) != null) {
            minimal--;
            task.run();
        }
        this.saveChunksEagerly(haveTime);
    }

    private void saveChunksEagerly(final BooleanSupplier haveTime) {
        long now = Util.getMillis();
        int saved = 0;
        ObjectIterator<ChunkPos> iterator = this.chunksToEagerlySave.iterator();
        while (saved < 20 && this.activeChunkWrites.get() < 128 && haveTime.getAsBoolean() && iterator.hasNext()) {
            ChunkPos pos = iterator.next();
            ChunkHolder holder = this.visibleChunkMap.get(pos);
            ChunkAccess latest = holder != null ? holder.getLatestChunk() : null;
            if (latest == null || !latest.isUnsaved()) {
                iterator.remove();
            } else if (this.saveChunkIfNeeded(holder, now)) {
                saved++;
                iterator.remove();
            }
        }
    }

    private void scheduleUnload(final ChunkPos pos, final ChunkHolder holder) {
        CompletableFuture<?> saveFuture = holder.getSaveSyncFuture();
        saveFuture.thenRunAsync(() -> {
            CompletableFuture<?> current = holder.getSaveSyncFuture();
            if (current != saveFuture) {
                this.scheduleUnload(pos, holder);
            } else {
                ChunkAccess chunk = holder.getLatestChunk();
                if (this.pendingUnloads.remove(pos, holder) && chunk != null) {
                    if (chunk instanceof LevelChunk lc) lc.setLoaded(false);
                    this.save(chunk);
                    if (chunk instanceof LevelChunk lc) this.level.unload(lc);
                    this.lightEngine.updateChunkStatus(chunk.getPos());
                    this.lightEngine.tryScheduleUpdate();
                    this.nextChunkSaveTime.remove(pos);
                }
            }
        }, this.unloadQueue::add).whenComplete((ignored, throwable) -> {
            if (throwable != null) LOGGER.error("Failed to save chunk {}", holder.getPos(), throwable);
        });
    }

    protected boolean promoteChunkMap() {
        if (!this.modified) return false;
        this.visibleChunkMap = this.updatingChunkMap.clone();
        this.modified = false;
        return true;
    }

    private CompletableFuture<ChunkAccess> scheduleChunkLoad(final ChunkPos pos) {
        CompletableFuture<Optional<SerializableChunkData>> dataFuture = this.readChunk(pos).thenApplyAsync(tag -> tag.map(t -> {
            SerializableChunkData parsed = SerializableChunkData.parse(this.level, this.level.palettedContainerFactory(), t);
            if (parsed == null) LOGGER.error("Chunk file at {} is missing level data, skipping", pos);
            return parsed;
        }), Util.backgroundExecutor().forName("parseChunk"));
        CompletableFuture<?> poiFuture = this.poiManager.prefetch(pos);
        return dataFuture.thenCombine(poiFuture, (data, ignored) -> data)
                .thenApplyAsync(data -> {
                    Profiler.get().incrementCounter("chunkLoad");
                    if (data.isPresent()) {
                        ChunkAccess chunk = data.get().read(this.level, this.poiManager, this.storageInfo(), pos);
                        this.markPosition(pos, chunk.getPersistedStatus().getChunkType());
                        return chunk;
                    } else {
                        return this.createEmptyChunk(pos);
                    }
                }, this.mainThreadExecutor)
                .exceptionallyAsync(t -> this.handleChunkLoadFailure(t, pos), this.mainThreadExecutor);
    }

    private ChunkAccess handleChunkLoadFailure(final Throwable t, final ChunkPos pos) {
        Throwable unwrapped = t instanceof CompletionException e ? e.getCause() : t;
        Throwable cause = unwrapped instanceof ReportedException e ? e.getCause() : unwrapped;
        boolean fatal = cause instanceof Error;
        if (!fatal) {
            this.level.getServer().reportChunkLoadFailure(cause, this.storageInfo(), pos);
            return this.createEmptyChunk(pos);
        } else {
            CrashReport report = CrashReport.forThrowable(t, "Exception loading chunk");
            CrashReportCategory cat = report.addCategory("Chunk being loaded");
            cat.setDetail("pos", pos);
            this.markPositionReplaceable(pos);
            throw new ReportedException(report);
        }
    }

    private ChunkAccess createEmptyChunk(final ChunkPos pos) {
        this.markPositionReplaceable(pos);
        return new ProtoChunk(pos, UpgradeData.EMPTY, this.level, this.level.palettedContainerFactory(), null);
    }

    private void markPositionReplaceable(final ChunkPos pos) {
        this.chunkTypeCache.put(pos, (byte) -1);
    }

    private byte markPosition(final ChunkPos pos, final ChunkType type) {
        return this.chunkTypeCache.put(pos, (byte) (type == ChunkType.PROTOCHUNK ? -1 : 1));
    }

    @Override
    public GenerationChunkHolder acquireGeneration(final ChunkPos chunkPos) {
        ChunkHolder holder = this.updatingChunkMap.get(chunkPos);
        holder.increaseGenerationRefCount();
        return holder;
    }

    @Override
    public void releaseGeneration(final GenerationChunkHolder holder) {
        holder.decreaseGenerationRefCount();
    }

    @Override
    public CompletableFuture<ChunkAccess> applyStep(
            final GenerationChunkHolder holder, final ChunkStep step, final StaticCache2D<GenerationChunkHolder> cache
    ) {
        ChunkPos pos = holder.getPos();
        if (step.targetStatus() == ChunkStatus.EMPTY) {
            return this.scheduleChunkLoad(pos);
        }
        try {
            GenerationChunkHolder h = cache.get(pos.x, pos.z);
            ChunkAccess center = h.getChunkIfPresentUnchecked(step.targetStatus().getParent());
            if (center == null) throw new IllegalStateException("Parent chunk missing");
            return step.apply(this.worldGenContext, cache, center);
        } catch (Exception e) {
            CrashReport report = CrashReport.forThrowable(e, "Exception generating new chunk");
            CrashReportCategory cat = report.addCategory("Chunk to be generated");
            cat.setDetail("Status being generated", () -> step.targetStatus().getName());
            cat.setDetail("Location", String.format(Locale.ROOT, "%d,%d", pos.x, pos.z));
            cat.setDetail("Generator", this.generator());
            throw new ReportedException(report);
        }
    }

    @Override
    public ChunkGenerationTask scheduleGenerationTask(final ChunkStatus targetStatus, final ChunkPos pos) {
        ChunkGenerationTask task = ChunkGenerationTask.create(this, targetStatus, pos);
        this.pendingGenerationTasks.add(task);
        return task;
    }

    private void runGenerationTask(final ChunkGenerationTask task) {
        GenerationChunkHolder chunk = task.getCenter();
        this.worldgenTaskDispatcher.submit(() -> {
            CompletableFuture<?> future = task.runUntilWait();
            if (future != null) future.thenRun(() -> this.runGenerationTask(task));
        }, chunk.getPos().pack(), chunk::getQueueLevel);
    }

    @Override
    public void runGenerationTasks() {
        this.pendingGenerationTasks.forEach(this::runGenerationTask);
        this.pendingGenerationTasks.clear();
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareTickingChunk(final ChunkHolder chunk) {
        CompletableFuture<ChunkResult<List<ChunkAccess>>> future = this.getChunkRangeFuture(chunk, 1, distance -> ChunkStatus.FULL);
        return future.thenApplyAsync(listResult -> listResult.map(list -> {
            LevelChunk levelChunk = (LevelChunk) list.get(list.size() / 2);
            levelChunk.postProcessGeneration(this.level);
            this.level.startTickingChunk(levelChunk);
            CompletableFuture<?> sendFuture = chunk.getSendSyncFuture();
            if (sendFuture.isDone()) this.onChunkReadyToSend(chunk, levelChunk);
            else sendFuture.thenAcceptAsync(ignored -> this.onChunkReadyToSend(chunk, levelChunk), this.mainThreadExecutor);
            return levelChunk;
        }), this.mainThreadExecutor);
    }

    private void onChunkReadyToSend(final ChunkHolder holder, final LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        for (ServerPlayer player : this.playerMap.getAllPlayers()) {
            if (player.getChunkTrackingView().contains(pos)) markChunkPendingToSend(player, chunk);
        }
        this.level.getChunkSource().onChunkReadyToSend(holder);
        this.level.debugSynchronizers().registerChunk(chunk);
    }

    public CompletableFuture<ChunkResult<LevelChunk>> prepareAccessibleChunk(final ChunkHolder chunk) {
        return this.getChunkRangeFuture(chunk, 1, ChunkLevel::getStatusAroundFullChunk)
                .thenApply(r -> r.map(list -> (LevelChunk) list.get(list.size() / 2)));
    }

    Stream<ChunkHolder> allChunksWithAtLeastStatus(final ChunkStatus status) {
        int level = ChunkLevel.byStatus(status);
        return this.visibleChunkMap.values().stream().filter(chunk -> chunk.getTicketLevel() <= level);
    }

    private boolean saveChunkIfNeeded(final ChunkHolder chunk, final long now) {
        if (chunk.wasAccessibleSinceLastSave() && chunk.isReadyForSaving()) {
            ChunkAccess access = chunk.getLatestChunk();
            if (!(access instanceof ImposterProtoChunk) && !(access instanceof LevelChunk)) return false;
            if (!access.isUnsaved()) return false;
            ChunkPos pos = access.getPos();
            long next = this.nextChunkSaveTime.getOrDefault(pos, -1L);
            if (now < next) return false;
            boolean saved = this.save(access);
            chunk.refreshAccessibility();
            if (saved) this.nextChunkSaveTime.put(pos, now + 10000L);
            return saved;
        }
        return false;
    }

    private boolean save(final ChunkAccess chunk) {
        this.poiManager.flush(chunk.getPos());
        if (!chunk.tryMarkSaved()) return false;
        ChunkPos pos = chunk.getPos();
        try {
            ChunkStatus status = chunk.getPersistedStatus();
            if (status.getChunkType() != ChunkType.LEVELCHUNK) {
                if (this.isExistingChunkFull(pos)) return false;
                if (status == ChunkStatus.EMPTY && chunk.getAllStarts().values().stream().noneMatch(StructureStart::isValid)) return false;
            }
            Profiler.get().incrementCounter("chunkSave");
            this.activeChunkWrites.incrementAndGet();
            SerializableChunkData data = SerializableChunkData.copyOf(this.level, chunk);
            CompletableFuture<CompoundTag> encoded = CompletableFuture.supplyAsync(data::write, Util.backgroundExecutor());
            this.write(pos, encoded::join).handle((ignored, throwable) -> {
                if (throwable != null) this.level.getServer().reportChunkSaveFailure(throwable, this.storageInfo(), pos);
                this.activeChunkWrites.decrementAndGet();
                return null;
            });
            this.markPosition(pos, status.getChunkType());
            return true;
        } catch (Exception e) {
            this.level.getServer().reportChunkSaveFailure(e, this.storageInfo(), pos);
            return false;
        }
    }

    private boolean isExistingChunkFull(final ChunkPos pos) {
        byte cached = this.chunkTypeCache.getOrDefault(pos, (byte) 0);
        if (cached != 0) return cached == 1;
        CompoundTag tag;
        try {
            tag = this.readChunk(pos).join().orElse(null);
            if (tag == null) { this.markPositionReplaceable(pos); return false; }
        } catch (Exception e) {
            LOGGER.error("Failed to read chunk {}", pos, e);
            this.markPositionReplaceable(pos);
            return false;
        }
        ChunkType type = SerializableChunkData.getChunkStatusFromTag(tag).getChunkType();
        return this.markPosition(pos, type) == 1;
    }

    protected void setServerViewDistance(final int newViewDistance) {
        int actual = Mth.clamp(newViewDistance, 2, 32);
        if (actual != this.serverViewDistance) {
            this.serverViewDistance = actual;
            this.distanceManager.updatePlayerTickets(this.serverViewDistance);
            for (ServerPlayer player : this.playerMap.getAllPlayers()) this.updateChunkTracking(player);
        }
    }

    private int getPlayerViewDistance(final ServerPlayer player) {
        return Mth.clamp(player.requestedViewDistance(), 2, this.serverViewDistance);
    }

    private void markChunkPendingToSend(final ServerPlayer player, final ChunkPos pos) {
        LevelChunk chunk = this.getChunkToSend(pos);
        if (chunk != null) markChunkPendingToSend(player, chunk);
    }

    private static void markChunkPendingToSend(final ServerPlayer player, final LevelChunk chunk) {
        player.connection.chunkSender.markChunkPendingToSend(chunk);
    }

    private static void dropChunk(final ServerPlayer player, final ChunkPos pos) {
        player.connection.chunkSender.dropChunk(player, pos);
    }

    public @Nullable LevelChunk getChunkToSend(final ChunkPos pos) {
        ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
        return holder == null ? null : holder.getChunkToSend();
    }

    public int size() {
        return this.visibleChunkMap.size();
    }

    public net.minecraft.server.level.DistanceManager getDistanceManager() {
        return this.distanceManager;
    }

    void dumpChunks(final Writer output) throws IOException {
        CsvOutput csv = CsvOutput.builder()
                .addColumn("x").addColumn("z").addColumn("level").addColumn("in_memory")
                .addColumn("status").addColumn("full_status").addColumn("accessible_ready")
                .addColumn("ticking_ready").addColumn("entity_ticking_ready")
                .addColumn("ticket").addColumn("spawning").addColumn("block_entity_count")
                .addColumn("ticking_ticket").addColumn("ticking_level")
                .addColumn("block_ticks").addColumn("fluid_ticks")
                .build(output);
        for (Object2ObjectMap.Entry<ChunkPos, ChunkHolder> entry : this.visibleChunkMap.object2ObjectEntrySet()) {
            ChunkPos pos = entry.getKey();
            ChunkHolder holder = entry.getValue();
            Optional<ChunkAccess> chunk = Optional.ofNullable(holder.getLatestChunk());
            Optional<LevelChunk> full = chunk.flatMap(c -> c instanceof LevelChunk lc ? Optional.of(lc) : Optional.empty());
            csv.writeRow(
                    pos.x, pos.z, holder.getTicketLevel(),
                    chunk.isPresent(),
                    chunk.map(ChunkAccess::getPersistedStatus).orElse(null),
                    full.map(LevelChunk::getFullStatus).orElse(null),
                    printFuture(holder.getFullChunkFuture()),
                    printFuture(holder.getTickingChunkFuture()),
                    printFuture(holder.getEntityTickingChunkFuture()),
                    this.ticketStorage.getTicketDebugString(pos, false),
                    this.anyPlayerCloseEnoughForSpawning(pos),
                    full.map(c -> c.getBlockEntities().size()).orElse(0),
                    this.ticketStorage.getTicketDebugString(pos, true),
                    this.distanceManager.getChunkLevel(pos, true),
                    full.map(c -> c.getBlockTicks().count()).orElse(0),
                    full.map(c -> c.getFluidTicks().count()).orElse(0)
            );
        }
    }

    private static String printFuture(final CompletableFuture<ChunkResult<LevelChunk>> future) {
        try {
            ChunkResult<LevelChunk> result = future.getNow(null);
            if (result != null) return result.isSuccess() ? "done" : "unloaded";
            return "not completed";
        } catch (CompletionException e) { return "failed " + e.getCause().getMessage(); }
        catch (CancellationException e) { return "cancelled"; }
    }

    private CompletableFuture<Optional<CompoundTag>> readChunk(final ChunkPos pos) {
        return this.read(pos).thenApplyAsync(tag -> tag.map(this::upgradeChunkTag), Util.backgroundExecutor().forName("upgradeChunk"));
    }

    private CompoundTag upgradeChunkTag(final CompoundTag tag) {
        return this.upgradeChunkTag(tag, -1, getChunkDataFixContextTag(this.level.dimension(), this.generator().getTypeNameForDataFixer()),
                SharedConstants.getCurrentVersion().dataVersion().version());
    }

    public static CompoundTag getChunkDataFixContextTag(final ResourceKey<Level> dimension, final Optional<Identifier> generatorIdentifier) {
        CompoundTag context = new CompoundTag();
        context.putString("dimension", dimension.identifier().toString());
        generatorIdentifier.ifPresent(id -> context.putString("generator", id.toString()));
        return context;
    }

    public void collectSpawningChunks(final List<LevelChunk> output) {
        ObjectIterator<ChunkPos> iter = this.distanceManager.getSpawnCandidateChunks();
        while (iter.hasNext()) {
            ChunkPos pos = iter.next();
            ChunkHolder holder = this.visibleChunkMap.get(pos);
            if (holder != null) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk != null && this.anyPlayerCloseEnoughForSpawningInternal(holder.getPos())) {
                    output.add(chunk);
                }
            }
        }
    }

    public void forEachBlockTickingChunk(final Consumer<LevelChunk> consumer) {
        this.distanceManager.forEachEntityTickingChunk(pos -> {
            ChunkHolder holder = this.visibleChunkMap.get(pos);
            if (holder != null) {
                LevelChunk chunk = holder.getTickingChunk();
                if (chunk != null) consumer.accept(chunk);
            }
        });
    }

    public boolean anyPlayerCloseEnoughForSpawning(final ChunkPos pos) {
        TriState state = this.distanceManager.hasPlayersNearby(pos);
        return state == TriState.DEFAULT ? this.anyPlayerCloseEnoughForSpawningInternal(pos) : state.toBoolean(true);
    }

    public boolean anyPlayerCloseEnoughTo(final BlockPos pos, final int maxDistance) {
        Vec3 target = new Vec3(pos);
        for (ServerPlayer player : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughTo(player, target, maxDistance)) return true;
        }
        return false;
    }

    private boolean anyPlayerCloseEnoughForSpawningInternal(final ChunkPos pos) {
        for (ServerPlayer player : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(player, pos)) return true;
        }
        return false;
    }

    public List<ServerPlayer> getPlayersCloseForSpawning(final ChunkPos pos) {
        if (!this.distanceManager.hasPlayersNearby(pos).toBoolean(true)) return List.of();
        Builder<ServerPlayer> builder = ImmutableList.builder();
        for (ServerPlayer player : this.playerMap.getAllPlayers()) {
            if (this.playerIsCloseEnoughForSpawning(player, pos)) builder.add(player);
        }
        return builder.build();
    }

    private boolean playerIsCloseEnoughForSpawning(final ServerPlayer player, final ChunkPos pos) {
        if (player.isSpectator()) return false;
        double dist = euclideanDistanceSquared(pos, player.position());
        return dist < 16384.0;
    }

    private boolean playerIsCloseEnoughTo(final ServerPlayer player, final Vec3 pos, final int maxDistance) {
        if (player.isSpectator()) return false;
        return player.position().distanceTo(pos) < maxDistance;
    }

    private static double euclideanDistanceSquared(final ChunkPos chunkPos, final Vec3 pos) {
        double x = SectionPos.sectionToBlockCoord(chunkPos.x, 8);
        double z = SectionPos.sectionToBlockCoord(chunkPos.z, 8);
        double dx = x - pos.x, dz = z - pos.z;
        return dx * dx + dz * dz;
    }

    private boolean skipPlayer(final ServerPlayer player) {
        return player.isSpectator() && !this.level.getGameRules().getBoolean(GameRules.SPECTATORS_GENERATE_CHUNKS);
    }

    private void updatePlayerStatus(final ServerPlayer player, final boolean added) {
        boolean ignored = this.skipPlayer(player);
        boolean wasIgnored = this.playerMap.ignoredOrUnknown(player);
        if (added) {
            this.playerMap.addPlayer(player, ignored);
            this.updatePlayerPos(player);
            if (!ignored) this.distanceManager.addPlayer(SectionPos.of(player), player);
            player.setChunkTrackingView(ChunkTrackingView.EMPTY);
            this.updateChunkTracking(player);
        } else {
            SectionPos last = player.getLastSectionPos();
            this.playerMap.removePlayer(player);
            if (!wasIgnored) this.distanceManager.removePlayer(last, player);
            this.applyChunkTrackingView(player, ChunkTrackingView.EMPTY);
        }
    }

    private void updatePlayerPos(final ServerPlayer player) {
        player.setLastSectionPos(SectionPos.of(player));
    }

    public void move(final ServerPlayer player) {
        for (TrackedEntity tracked : this.entityMap.values()) {
            if (tracked.entity == player) tracked.updatePlayers(this.level.players());
            else tracked.updatePlayer(player);
        }
        SectionPos old = player.getLastSectionPos();
        SectionPos now = SectionPos.of(player);
        boolean wasIgnored = this.playerMap.ignored(player);
        boolean ignored = this.skipPlayer(player);
        boolean posChanged = !old.equals(now);
        if (posChanged || wasIgnored != ignored) {
            this.updatePlayerPos(player);
            if (!wasIgnored) this.distanceManager.removePlayer(old, player);
            if (!ignored) this.distanceManager.addPlayer(now, player);
            if (!wasIgnored && ignored) this.playerMap.ignorePlayer(player);
            if (wasIgnored && !ignored) this.playerMap.unIgnorePlayer(player);
            this.updateChunkTracking(player);
        }
    }

    private void updateChunkTracking(final ServerPlayer player) {
        ChunkPos center = player.chunkPosition();
        int dist = this.getPlayerViewDistance(player);
        if (!(player.getChunkTrackingView() instanceof ChunkTrackingView.Positioned view
                && view.center().equals(center) && view.viewDistance() == dist)) {
            this.applyChunkTrackingView(player, ChunkTrackingView.of(center, dist));
        }
    }

    private void applyChunkTrackingView(final ServerPlayer player, final ChunkTrackingView next) {
        if (player.level() != this.level) return;
        ChunkTrackingView prev = player.getChunkTrackingView();
        if (next instanceof ChunkTrackingView.Positioned to
                && !(prev instanceof ChunkTrackingView.Positioned from && from.center().equals(to.center()))) {
            player.connection.send(new ClientboundSetChunkCacheCenterPacket(to.center().x(), to.center().z()));
        }
        ChunkTrackingView.difference(prev, next,
                pos -> this.markChunkPendingToSend(player, pos),
                pos -> dropChunk(player, pos));
        player.setChunkTrackingView(next);
    }

    @Override
    public List<ServerPlayer> getPlayers(final ChunkPos pos, final boolean borderOnly) {
        Set<ServerPlayer> all = this.playerMap.getAllPlayers();
        Builder<ServerPlayer> builder = ImmutableList.builder();
        for (ServerPlayer player : all) {
            if (borderOnly ? this.isChunkOnTrackedBorder(player, pos) : this.isChunkTracked(player, pos)) {
                builder.add(player);
            }
        }
        return builder.build();
    }

    public boolean hasEntityWithId(final int id) { return this.entityMap.containsKey(id); }

    protected void addEntity(final Entity entity) {
        if (entity instanceof EnderDragonPart) return;
        int range = entity.getType().clientTrackingRange() * 16;
        if (range == 0) return;
        int interval = entity.getType().updateInterval();
        if (this.entityMap.containsKey(entity.getId())) {
            throw (IllegalStateException) Util.pauseInIde(new IllegalStateException("Entity is already tracked!"));
        }
        TrackedEntity tracked = new TrackedEntity(entity, range, interval, entity.getType().trackDeltas());
        this.entityMap.put(entity.getId(), tracked);
        tracked.updatePlayers(this.level.players());
        if (entity instanceof ServerPlayer player) {
            this.updatePlayerStatus(player, true);
            for (TrackedEntity e : this.entityMap.values()) {
                if (e.entity != player) e.updatePlayer(player);
            }
        }
    }

    protected void removeEntity(final Entity entity) {
        if (entity instanceof ServerPlayer player) {
            this.updatePlayerStatus(player, false);
            for (TrackedEntity e : this.entityMap.values()) e.removePlayer(player);
        }
        TrackedEntity removed = this.entityMap.remove(entity.getId());
        if (removed != null) removed.broadcastRemoved();
    }

    protected void tick() {
        for (ServerPlayer player : this.playerMap.getAllPlayers()) this.updateChunkTracking(player);
        List<ServerPlayer> moved = Lists.newArrayList();
        List<ServerPlayer> players = this.level.players();
        for (TrackedEntity tracked : this.entityMap.values()) {
            SectionPos old = tracked.lastSectionPos;
            SectionPos now = SectionPos.of(tracked.entity);
            if (!Objects.equals(old, now)) {
                tracked.updatePlayers(players);
                if (tracked.entity instanceof ServerPlayer sp) moved.add(sp);
                tracked.lastSectionPos = now;
            }
            if (!Objects.equals(old, now) || tracked.entity.needsSync
                    || this.distanceManager.inEntityTickingRange(now.chunk())) {
                tracked.serverEntity.sendChanges();
            }
        }
        if (!moved.isEmpty()) {
            for (TrackedEntity e : this.entityMap.values()) e.updatePlayers(moved);
        }
    }

    public void sendToTrackingPlayers(final Entity entity, final Packet<? super ClientGamePacketListener> packet) {
        TrackedEntity tracked = this.entityMap.get(entity.getId());
        if (tracked != null) tracked.sendToTrackingPlayers(packet);
    }

    public void sendToTrackingPlayersFiltered(final Entity entity, final Packet<? super ClientGamePacketListener> packet,
                                              final Predicate<ServerPlayer> predicate) {
        TrackedEntity tracked = this.entityMap.get(entity.getId());
        if (tracked != null) tracked.sendToTrackingPlayersFiltered(packet, predicate);
    }

    protected void sendToTrackingPlayersAndSelf(final Entity entity, final Packet<? super ClientGamePacketListener> packet) {
        TrackedEntity tracked = this.entityMap.get(entity.getId());
        if (tracked != null) tracked.sendToTrackingPlayersAndSelf(packet);
    }

    public boolean isTrackedByAnyPlayer(final Entity entity) {
        TrackedEntity tracked = this.entityMap.get(entity.getId());
        return tracked != null && !tracked.seenBy.isEmpty();
    }

    public void forEachEntityTrackedBy(final ServerPlayer player, final Consumer<Entity> consumer) {
        for (TrackedEntity tracked : this.entityMap.values()) {
            if (tracked.seenBy.contains(player.connection)) consumer.accept(tracked.entity);
        }
    }

    public void resendBiomesForChunks(final List<ChunkAccess> chunks) {
        Map<ServerPlayer, List<LevelChunk>> map = new HashMap<>();
        for (ChunkAccess access : chunks) {
            ChunkPos pos = access.getPos();
            LevelChunk chunk = access instanceof LevelChunk lc ? lc : this.level.getChunk(pos.x, pos.z);
            for (ServerPlayer player : this.getPlayers(pos, false)) {
                map.computeIfAbsent(player, p -> new ArrayList<>()).add(chunk);
            }
        }
        map.forEach((player, list) -> player.connection.send(ClientboundChunksBiomesPacket.forChunks(list)));
    }

    protected PoiManager getPoiManager() { return this.poiManager; }

    public String getStorageName() { return this.storageName; }

    void onFullChunkStatusChange(final ChunkPos pos, final FullChunkStatus status) {
        this.chunkStatusListener.onChunkStatusChange(pos, status);
    }

    public void waitForLightBeforeSending(final ChunkPos center, final int radius) {
        int r = radius + 1;
        ChunkPos.rangeClosed(center, r).forEach(pos -> {
            ChunkHolder holder = this.getVisibleChunkIfPresent(pos);
            if (holder != null) {
                holder.addSendDependency(this.lightEngine.waitForPendingTasks(pos.x, pos.z));
            }
        });
    }

    public void forEachReadyToSendChunk(final Consumer<LevelChunk> consumer) {
        for (ChunkHolder holder : this.visibleChunkMap.values()) {
            LevelChunk chunk = holder.getChunkToSend();
            if (chunk != null) consumer.accept(chunk);
        }
    }

    // ===== 内部类 DistanceManager =====
    private class DistanceManager extends net.minecraft.server.level.DistanceManager {
        protected DistanceManager(TicketStorage ticketStorage, Executor executor, Executor mainThreadExecutor) {
            super(ticketStorage, executor, mainThreadExecutor);
        }

        @Override
        protected boolean isChunkToRemove(final ChunkPos pos) {
            return ChunkMap.this.toDrop.contains(pos);
        }

        @Override
        protected @Nullable ChunkHolder getChunk(final ChunkPos pos) {
            return ChunkMap.this.getUpdatingChunkIfPresent(pos);
        }

        @Override
        protected @Nullable ChunkHolder updateChunkScheduling(final ChunkPos pos, final int level,
                                                              final @Nullable ChunkHolder chunk, final int oldLevel) {
            return ChunkMap.this.updateChunkScheduling(pos, level, chunk, oldLevel);
        }
    }

    // ===== TrackedEntity (内部类，基本不变，但里面用到 ChunkPos 的地方已适配) =====
    private class TrackedEntity implements ServerEntity.Synchronizer {
        private final ServerEntity serverEntity;
        private final Entity entity;
        private final int range;
        private SectionPos lastSectionPos;
        private final Set<ServerPlayerConnection> seenBy = Sets.newIdentityHashSet();

        public TrackedEntity(final Entity entity, final int range, final int updateInterval, final boolean trackDelta) {
            this.serverEntity = new ServerEntity(ChunkMap.this.level, entity, updateInterval, trackDelta, this);
            this.entity = entity;
            this.range = range;
            this.lastSectionPos = SectionPos.of(entity);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof TrackedEntity other && other.entity.getId() == this.entity.getId();
        }

        @Override
        public int hashCode() { return this.entity.getId(); }

        @Override
        public void sendToTrackingPlayers(Packet<? super ClientGamePacketListener> packet) {
            for (ServerPlayerConnection conn : this.seenBy) conn.send(packet);
        }

        @Override
        public void sendToTrackingPlayersAndSelf(Packet<? super ClientGamePacketListener> packet) {
            this.sendToTrackingPlayers(packet);
            if (this.entity instanceof ServerPlayer sp) sp.connection.send(packet);
        }

        @Override
        public void sendToTrackingPlayersFiltered(Packet<? super ClientGamePacketListener> packet, Predicate<ServerPlayer> predicate) {
            for (ServerPlayerConnection conn : this.seenBy) {
                if (predicate.test(conn.getPlayer())) conn.send(packet);
            }
        }

        public void broadcastRemoved() {
            for (ServerPlayerConnection conn : this.seenBy) this.serverEntity.removePairing(conn.getPlayer());
        }

        public void removePlayer(final ServerPlayer player) {
            if (this.seenBy.remove(player.connection)) {
                this.serverEntity.removePairing(player);
                if (this.seenBy.isEmpty()) ChunkMap.this.level.debugSynchronizers().dropEntity(this.entity);
            }
        }

        public void updatePlayer(final ServerPlayer player) {
            if (player == this.entity) return;
            Vec3 delta = player.position().subtract(this.entity.position());
            int viewDist = ChunkMap.this.getPlayerViewDistance(player);
            double maxRange = Math.min(this.getEffectiveRange(), viewDist * 16);
            double distSq = delta.x * delta.x + delta.z * delta.z;
            boolean visible = distSq <= maxRange * maxRange
                    && this.entity.broadcastToPlayer(player)
                    && ChunkMap.this.isChunkTracked(player, this.entity.chunkPosition());
            if (visible) {
                if (this.seenBy.add(player.connection)) {
                    this.serverEntity.addPairing(player);
                    if (this.seenBy.size() == 1) ChunkMap.this.level.debugSynchronizers().registerEntity(this.entity);
                    ChunkMap.this.level.debugSynchronizers().startTrackingEntity(player, this.entity);
                }
            } else {
                this.removePlayer(player);
            }
        }

        private int scaledRange(int range) {
            return ChunkMap.this.level.getServer().getScaledTrackingDistance(range);
        }

        private int getEffectiveRange() {
            int eff = this.range;
            for (Entity passenger : this.entity.getIndirectPassengers()) {
                int pr = passenger.getType().clientTrackingRange() * 16;
                if (pr > eff) eff = pr;
            }
            return this.scaledRange(eff);
        }

        public void updatePlayers(final List<ServerPlayer> players) {
            for (ServerPlayer player : players) this.updatePlayer(player);
        }
    }
}