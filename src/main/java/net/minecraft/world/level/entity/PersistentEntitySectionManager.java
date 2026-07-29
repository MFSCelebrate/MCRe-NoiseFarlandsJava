package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Queues;
import com.google.common.collect.Sets;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.util.CsvOutput;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import org.slf4j.Logger;

public class PersistentEntitySectionManager<T extends EntityAccess> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Set<UUID> knownUuids = Sets.newHashSet();
    private final LevelCallback<T> callbacks;
    private final EntityPersistentStorage<T> permanentStorage;
    private final EntityLookup<T> visibleEntityStorage;
    private final EntitySectionStorage<T> sectionStorage;
    private final LevelEntityGetter<T> entityGetter;
    // ===== 修改：使用 ChunkPos 作为键 =====
    private final Object2ObjectMap<ChunkPos, Visibility> chunkVisibility = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<ChunkPos, PersistentEntitySectionManager.ChunkLoadStatus> chunkLoadStatuses = new Object2ObjectOpenHashMap<>();
    private final Set<ChunkPos> chunksToUnload = new ObjectOpenHashSet<>();
    private final Queue<ChunkEntities<T>> loadingInbox = Queues.newConcurrentLinkedQueue();

    public PersistentEntitySectionManager(final Class<T> entityClass, final LevelCallback<T> callbacks, final EntityPersistentStorage<T> permanentStorage) {
        this.visibleEntityStorage = new EntityLookup<>();
        this.sectionStorage = new EntitySectionStorage<>(entityClass, this.chunkVisibility);
        this.chunkVisibility.defaultReturnValue(Visibility.HIDDEN);
        this.chunkLoadStatuses.defaultReturnValue(PersistentEntitySectionManager.ChunkLoadStatus.FRESH);
        this.callbacks = callbacks;
        this.permanentStorage = permanentStorage;
        this.entityGetter = new LevelEntityGetterAdapter<>(this.visibleEntityStorage, this.sectionStorage);
    }

    // ===== 参数改为 SectionPos =====
    private void removeSectionIfEmpty(final SectionPos sectionPos, final EntitySection<T> section) {
        if (section.isEmpty()) {
            this.sectionStorage.remove(sectionPos);
        }
    }

    private boolean addEntityUuid(final T entity) {
        if (!this.knownUuids.add(entity.getUUID())) {
            LOGGER.warn("UUID of added entity already exists: {}", entity);
            return false;
        } else {
            return true;
        }
    }

    public boolean addNewEntity(final T entity) {
        return this.addEntity(entity, false);
    }

    private boolean addEntity(final T entity, final boolean loaded) {
        if (!this.addEntityUuid(entity)) {
            return false;
        }

        // ===== 使用 SectionPos.of 替代 asLong =====
        SectionPos sectionKey = SectionPos.of(entity.blockPosition());
        EntitySection<T> entitySection = this.sectionStorage.getOrCreateSection(sectionKey);
        entitySection.add(entity);
        entity.setLevelCallback(new PersistentEntitySectionManager.Callback(entity, sectionKey, entitySection));
        if (!loaded) {
            this.callbacks.onCreated(entity);
        }

        Visibility status = getEffectiveStatus(entity, entitySection.getStatus());
        if (status.isAccessible()) {
            this.startTracking(entity);
        }

        if (status.isTicking()) {
            this.startTicking(entity);
        }

        return true;
    }

    private static <T extends EntityAccess> Visibility getEffectiveStatus(final T entity, final Visibility status) {
        return entity.isAlwaysTicking() ? Visibility.TICKING : status;
    }

    public boolean isTicking(final ChunkPos pos) {
        return this.chunkVisibility.get(pos).isTicking();
    }

    public void addLegacyChunkEntities(final Stream<T> entities) {
        entities.forEach(e -> this.addEntity((T) e, true));
    }

    public void addWorldGenChunkEntities(final Stream<T> entities) {
        entities.forEach(e -> this.addEntity((T) e, false));
    }

    private void startTicking(final T entity) {
        this.callbacks.onTickingStart(entity);
    }

    private void stopTicking(final T entity) {
        this.callbacks.onTickingEnd(entity);
    }

    private void startTracking(final T entity) {
        this.visibleEntityStorage.add(entity);
        this.callbacks.onTrackingStart(entity);
    }

    private void stopTracking(final T entity) {
        this.callbacks.onTrackingEnd(entity);
        this.visibleEntityStorage.remove(entity);
    }

    public void updateChunkStatus(final ChunkPos pos, final FullChunkStatus fullChunkStatus) {
        Visibility chunkStatus = Visibility.fromFullChunkStatus(fullChunkStatus);
        this.updateChunkStatus(pos, chunkStatus);
    }

    public void updateChunkStatus(final ChunkPos pos, final Visibility chunkStatus) {
        if (chunkStatus == Visibility.HIDDEN) {
            this.chunkVisibility.remove(pos);
            this.chunksToUnload.add(pos);
        } else {
            this.chunkVisibility.put(pos, chunkStatus);
            this.chunksToUnload.remove(pos);
            this.ensureChunkQueuedForLoad(pos);
        }

        this.sectionStorage.getExistingSectionsInChunk(pos).forEach(section -> {
            Visibility previousStatus = section.updateChunkStatus(chunkStatus);
            boolean wasAccessible = previousStatus.isAccessible();
            boolean isAccessible = chunkStatus.isAccessible();
            boolean wasTicking = previousStatus.isTicking();
            boolean isTicking = chunkStatus.isTicking();
            if (wasTicking && !isTicking) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTicking);
            }

            if (wasAccessible && !isAccessible) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::stopTracking);
            } else if (!wasAccessible && isAccessible) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTracking);
            }

            if (!wasTicking && isTicking) {
                section.getEntities().filter(e -> !e.isAlwaysTicking()).forEach(this::startTicking);
            }
        });
    }

    private void ensureChunkQueuedForLoad(final ChunkPos chunkPos) {
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPos);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
        }
    }

    private boolean storeChunkSections(final ChunkPos chunkPos, final Consumer<T> savedEntityVisitor) {
        PersistentEntitySectionManager.ChunkLoadStatus chunkLoadStatus = this.chunkLoadStatuses.get(chunkPos);
        if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.PENDING) {
            return false;
        }

        List<T> rootEntitiesToSave = this.sectionStorage
            .getExistingSectionsInChunk(chunkPos)
            .flatMap(section -> section.getEntities().filter(EntityAccess::shouldBeSaved))
            .collect(Collectors.toList());
        if (rootEntitiesToSave.isEmpty()) {
            if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                this.permanentStorage.storeEntities(new ChunkEntities<>(chunkPos, ImmutableList.of()));
            }
            return true;
        } else if (chunkLoadStatus == PersistentEntitySectionManager.ChunkLoadStatus.FRESH) {
            this.requestChunkLoad(chunkPos);
            return false;
        } else {
            this.permanentStorage.storeEntities(new ChunkEntities<>(chunkPos, rootEntitiesToSave));
            rootEntitiesToSave.forEach(savedEntityVisitor);
            return true;
        }
    }

    private void requestChunkLoad(final ChunkPos chunkPos) {
        this.chunkLoadStatuses.put(chunkPos, PersistentEntitySectionManager.ChunkLoadStatus.PENDING);
        this.permanentStorage.loadEntities(chunkPos).thenAccept(this.loadingInbox::add).exceptionally(t -> {
            LOGGER.error("Failed to read chunk {}", chunkPos, t);
            return null;
        });
    }

    private boolean processChunkUnload(final ChunkPos chunkPos) {
        boolean storeSuccessful = this.storeChunkSections(chunkPos, entity -> entity.getPassengersAndSelf().forEach(this::unloadEntity));
        if (!storeSuccessful) {
            return false;
        }
        this.chunkLoadStatuses.remove(chunkPos);
        return true;
    }

    private void unloadEntity(final EntityAccess e) {
        e.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        e.setLevelCallback(EntityInLevelCallback.NULL);
    }

    private void processUnloads() {
        this.chunksToUnload.removeIf(chunkPos -> this.chunkVisibility.get(chunkPos) != Visibility.HIDDEN ? true : this.processChunkUnload(chunkPos));
    }

    public void processPendingLoads() {
        ChunkEntities<T> loadedChunk;
        while ((loadedChunk = this.loadingInbox.poll()) != null) {
            loadedChunk.getEntities().forEach(e -> this.addEntity((T) e, true));
            this.chunkLoadStatuses.put(loadedChunk.getPos(), PersistentEntitySectionManager.ChunkLoadStatus.LOADED);
        }
    }

    public void tick() {
        this.processPendingLoads();
        this.processUnloads();
    }

    private Set<ChunkPos> getAllChunksToSave() {
        Set<ChunkPos> result = this.sectionStorage.getAllChunksWithExistingSections();
        for (Object2ObjectMap.Entry<ChunkPos, PersistentEntitySectionManager.ChunkLoadStatus> entry : Object2ObjectMaps.fastIterable(this.chunkLoadStatuses)) {
            if (entry.getValue() == PersistentEntitySectionManager.ChunkLoadStatus.LOADED) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    public void autoSave() {
        this.getAllChunksToSave().forEach(chunkPos -> {
            boolean shouldUnload = this.chunkVisibility.get(chunkPos) == Visibility.HIDDEN;
            if (shouldUnload) {
                this.processChunkUnload(chunkPos);
            } else {
                this.storeChunkSections(chunkPos, e -> {});
            }
        });
    }

    public void saveAll() {
        Set<ChunkPos> chunksToSave = this.getAllChunksToSave();

        while (!chunksToSave.isEmpty()) {
            this.permanentStorage.flush(false);
            this.processPendingLoads();
            chunksToSave.removeIf(chunkPos -> {
                boolean shouldUnload = this.chunkVisibility.get(chunkPos) == Visibility.HIDDEN;
                return shouldUnload ? this.processChunkUnload(chunkPos) : this.storeChunkSections(chunkPos, e -> {});
            });
        }

        this.permanentStorage.flush(true);
    }

    @Override
    public void close() throws IOException {
        this.saveAll();
        this.permanentStorage.close();
    }

    public boolean isLoaded(final UUID uuid) {
        return this.knownUuids.contains(uuid);
    }

    public LevelEntityGetter<T> getEntityGetter() {
        return this.entityGetter;
    }

    public boolean canPositionTick(final BlockPos pos) {
        return this.chunkVisibility.get(new ChunkPos(pos)).isTicking();
    }

    public boolean canPositionTick(final ChunkPos pos) {
        return this.chunkVisibility.get(pos).isTicking();
    }

    public boolean areEntitiesLoaded(final ChunkPos chunkPos) {
        return this.chunkLoadStatuses.get(chunkPos) == PersistentEntitySectionManager.ChunkLoadStatus.LOADED;
    }

    public void dumpSections(final Writer output) throws IOException {
        CsvOutput csvOutput = CsvOutput.builder()
            .addColumn("x")
            .addColumn("y")
            .addColumn("z")
            .addColumn("visibility")
            .addColumn("load_status")
            .addColumn("entity_count")
            .build(output);

        for (ChunkPos chunkPos : this.sectionStorage.getAllChunksWithExistingSections()) {
            PersistentEntitySectionManager.ChunkLoadStatus loadStatus = this.chunkLoadStatuses.get(chunkPos);
            this.sectionStorage.getExistingSectionPositionsInChunk(chunkPos).forEach(sectionPos -> {
                EntitySection<T> section = this.sectionStorage.getSection(sectionPos);
                if (section != null) {
                    try {
                        csvOutput.writeRow(
                            sectionPos.getX(),
                            sectionPos.getY(),
                            sectionPos.getZ(),
                            section.getStatus(),
                            loadStatus,
                            section.size()
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    @VisibleForDebug
    public String gatherStats() {
        return this.knownUuids.size()
            + ","
            + this.visibleEntityStorage.count()
            + ","
            + this.sectionStorage.count()
            + ","
            + this.chunkLoadStatuses.size()
            + ","
            + this.chunkVisibility.size()
            + ","
            + this.loadingInbox.size()
            + ","
            + this.chunksToUnload.size();
    }

    @VisibleForDebug
    public int count() {
        return this.visibleEntityStorage.count();
    }

    // ===== 内部类 Callback =====
    private class Callback implements EntityInLevelCallback {
        private final T entity;
        // ===== 改为 SectionPos =====
        private SectionPos currentSectionKey;
        private EntitySection<T> currentSection;

        private Callback(final T entity, final SectionPos currentSectionKey, final EntitySection<T> currentSection) {
            this.entity = entity;
            this.currentSectionKey = currentSectionKey;
            this.currentSection = currentSection;
        }

        @Override
        public void onMove() {
            BlockPos pos = this.entity.blockPosition();
            // ===== 使用 SectionPos.of 替代 asLong =====
            SectionPos newSectionPos = SectionPos.of(pos);
            if (!newSectionPos.equals(this.currentSectionKey)) {
                Visibility previousStatus = this.currentSection.getStatus();
                if (!this.currentSection.remove(this.entity)) {
                    PersistentEntitySectionManager.LOGGER
                        .warn("Entity {} wasn't found in section {} (moving to {})", this.entity, this.currentSectionKey, newSectionPos);
                }

                PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
                EntitySection<T> newSection = PersistentEntitySectionManager.this.sectionStorage.getOrCreateSection(newSectionPos);
                newSection.add(this.entity);
                this.currentSection = newSection;
                this.currentSectionKey = newSectionPos;
                this.updateStatus(previousStatus, newSection.getStatus());
            }
        }

        private void updateStatus(final Visibility previousStatus, final Visibility newStatus) {
            Visibility effectivePreviousStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, previousStatus);
            Visibility effectiveNewStatus = PersistentEntitySectionManager.getEffectiveStatus(this.entity, newStatus);
            if (effectivePreviousStatus == effectiveNewStatus) {
                if (effectiveNewStatus.isAccessible()) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            } else {
                boolean wasAccessible = effectivePreviousStatus.isAccessible();
                boolean isAccessible = effectiveNewStatus.isAccessible();
                if (wasAccessible && !isAccessible) {
                    PersistentEntitySectionManager.this.stopTracking(this.entity);
                } else if (!wasAccessible && isAccessible) {
                    PersistentEntitySectionManager.this.startTracking(this.entity);
                }

                boolean wasTicking = effectivePreviousStatus.isTicking();
                boolean isTicking = effectiveNewStatus.isTicking();
                if (wasTicking && !isTicking) {
                    PersistentEntitySectionManager.this.stopTicking(this.entity);
                } else if (!wasTicking && isTicking) {
                    PersistentEntitySectionManager.this.startTicking(this.entity);
                }

                if (isAccessible) {
                    PersistentEntitySectionManager.this.callbacks.onSectionChange(this.entity);
                }
            }
        }

        @Override
        public void onRemove(final Entity.RemovalReason reason) {
            if (!this.currentSection.remove(this.entity)) {
                PersistentEntitySectionManager.LOGGER
                    .warn("Entity {} wasn't found in section {} (destroying due to {})", this.entity, this.currentSectionKey, reason);
            }

            Visibility status = PersistentEntitySectionManager.getEffectiveStatus(this.entity, this.currentSection.getStatus());
            if (status.isTicking()) {
                PersistentEntitySectionManager.this.stopTicking(this.entity);
            }

            if (status.isAccessible()) {
                PersistentEntitySectionManager.this.stopTracking(this.entity);
            }

            if (reason.shouldDestroy()) {
                PersistentEntitySectionManager.this.callbacks.onDestroyed(this.entity);
            }

            PersistentEntitySectionManager.this.knownUuids.remove(this.entity.getUUID());
            this.entity.setLevelCallback(NULL);
            PersistentEntitySectionManager.this.removeSectionIfEmpty(this.currentSectionKey, this.currentSection);
        }
    }

    private enum ChunkLoadStatus {
        FRESH,
        PENDING,
        LOADED;
    }
}