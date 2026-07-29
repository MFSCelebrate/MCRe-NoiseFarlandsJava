package net.minecraft.world.level.chunk.storage;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Util;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

public class SectionStorage<R, P> implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String SECTIONS_TAG = "Sections";
    private final SimpleRegionStorage simpleRegionStorage;
    // ===== 修改：使用 SectionPos 作为键 =====
    private final Object2ObjectMap<SectionPos, Optional<R>> storage = new Object2ObjectOpenHashMap<>();
    // ===== 修改：使用 ChunkPos 作为键 =====
    private final ObjectLinkedOpenHashSet<ChunkPos> dirtyChunks = new ObjectLinkedOpenHashSet<>();
    private final Codec<P> codec;
    private final Function<R, P> packer;
    private final BiFunction<P, Runnable, R> unpacker;
    private final Function<Runnable, R> factory;
    private final RegistryAccess registryAccess;
    private final ChunkIOErrorReporter errorReporter;
    protected final LevelHeightAccessor levelHeightAccessor;
    // ===== 修改：使用 ChunkPos 作为键 =====
    private final ObjectOpenHashSet<ChunkPos> loadedChunks = new ObjectOpenHashSet<>();
    private final Object2ObjectMap<ChunkPos, CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>> pendingLoads = new Object2ObjectOpenHashMap<>();
    private final Object loadLock = new Object();

    public SectionStorage(
        final SimpleRegionStorage simpleRegionStorage,
        final Codec<P> codec,
        final Function<R, P> packer,
        final BiFunction<P, Runnable, R> unpacker,
        final Function<Runnable, R> factory,
        final RegistryAccess registryAccess,
        final ChunkIOErrorReporter errorReporter,
        final LevelHeightAccessor levelHeightAccessor
    ) {
        this.simpleRegionStorage = simpleRegionStorage;
        this.codec = codec;
        this.packer = packer;
        this.unpacker = unpacker;
        this.factory = factory;
        this.registryAccess = registryAccess;
        this.errorReporter = errorReporter;
        this.levelHeightAccessor = levelHeightAccessor;
    }

    protected void tick(final BooleanSupplier haveTime) {
        // ===== 使用对象迭代 =====
        Iterator<ChunkPos> iterator = this.dirtyChunks.iterator();

        while (iterator.hasNext() && haveTime.getAsBoolean()) {
            ChunkPos chunkPos = iterator.next();
            iterator.remove();
            this.writeChunk(chunkPos);
        }

        this.unpackPendingLoads();
    }

    private void unpackPendingLoads() {
        synchronized (this.loadLock) {
            // ===== 使用 ObjectIterator =====
            ObjectIterator<Object2ObjectMap.Entry<ChunkPos, CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>>> iterator =
                Object2ObjectMaps.fastIterator(this.pendingLoads);

            while (iterator.hasNext()) {
                Object2ObjectMap.Entry<ChunkPos, CompletableFuture<Optional<SectionStorage.PackedChunk<P>>>> entry = iterator.next();
                Optional<SectionStorage.PackedChunk<P>> chunk = entry.getValue().getNow(null);
                if (chunk != null) {
                    ChunkPos chunkPos = entry.getKey();
                    this.unpackChunk(chunkPos, chunk.orElse(null));
                    iterator.remove();
                    this.loadedChunks.add(chunkPos);
                }
            }
        }
    }

    public void flushAll() {
        if (!this.dirtyChunks.isEmpty()) {
            this.dirtyChunks.forEach(this::writeChunk);
            this.dirtyChunks.clear();
        }
    }

    public boolean hasWork() {
        return !this.dirtyChunks.isEmpty();
    }

    // ===== 参数改为 SectionPos =====
    protected @Nullable Optional<R> get(final SectionPos sectionPos) {
        return this.storage.get(sectionPos);
    }

    protected Optional<R> getOrLoad(final SectionPos sectionPos) {
        if (this.outsideStoredRange(sectionPos)) {
            return Optional.empty();
        } else {
            Optional<R> r = this.get(sectionPos);
            if (r != null) {
                return r;
            } else {
                this.unpackChunk(sectionPos.chunk());
                r = this.get(sectionPos);
                if (r == null) {
                    throw (IllegalStateException) Util.pauseInIde(new IllegalStateException());
                } else {
                    return r;
                }
            }
        }
    }

    // ===== 参数改为 SectionPos =====
    protected boolean outsideStoredRange(final SectionPos sectionPos) {
        int y = SectionPos.sectionToBlockCoord(sectionPos.getY());
        return this.levelHeightAccessor.isOutsideBuildHeight(y);
    }

    // ===== 参数改为 SectionPos =====
    protected R getOrCreate(final SectionPos sectionPos) {
        if (this.outsideStoredRange(sectionPos)) {
            throw (IllegalArgumentException) Util.pauseInIde(new IllegalArgumentException("sectionPos out of bounds"));
        }

        Optional<R> r = this.getOrLoad(sectionPos);
        if (r.isPresent()) {
            return r.get();
        }

        R newR = this.factory.apply(() -> this.setDirty(sectionPos));
        this.storage.put(sectionPos, Optional.of(newR));
        return newR;
    }

    public CompletableFuture<?> prefetch(final ChunkPos chunkPos) {
        synchronized (this.loadLock) {
            return this.loadedChunks.contains(chunkPos)
                ? CompletableFuture.completedFuture(null)
                : this.pendingLoads.computeIfAbsent(chunkPos, k -> this.tryRead(chunkPos));
        }
    }

    // ===== 参数改为 ChunkPos =====
    private void unpackChunk(final ChunkPos chunkPos) {
        CompletableFuture<Optional<SectionStorage.PackedChunk<P>>> future;
        synchronized (this.loadLock) {
            if (!this.loadedChunks.add(chunkPos)) {
                return;
            }

            future = this.pendingLoads.computeIfAbsent(chunkPos, k -> this.tryRead(chunkPos));
        }

        this.unpackChunk(chunkPos, future.join().orElse(null));
        synchronized (this.loadLock) {
            this.pendingLoads.remove(chunkPos);
        }
    }

    private CompletableFuture<Optional<SectionStorage.PackedChunk<P>>> tryRead(final ChunkPos chunkPos) {
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        return this.simpleRegionStorage
            .read(chunkPos)
            .thenApplyAsync(
                result -> result.map(tag -> SectionStorage.PackedChunk.parse(this.codec, registryOps, tag, this.simpleRegionStorage, this.levelHeightAccessor)),
                Util.backgroundExecutor().forName("parseSection")
            )
            .exceptionally(throwable -> {
                if (throwable instanceof CompletionException) {
                    throwable = throwable.getCause();
                }

                if (throwable instanceof IOException e) {
                    LOGGER.error("Error reading chunk {} data from disk", chunkPos, e);
                    this.errorReporter.reportChunkLoadFailure(e, this.simpleRegionStorage.storageInfo(), chunkPos);
                    return Optional.empty();
                } else {
                    throw new CompletionException(throwable);
                }
            });
    }

    // ===== 参数改为 ChunkPos，内部使用 SectionPos =====
    private void unpackChunk(final ChunkPos pos, final SectionStorage.@Nullable PackedChunk<P> packedChunk) {
        if (packedChunk == null) {
            for (int sectionY = this.levelHeightAccessor.getMinSectionY(); sectionY <= this.levelHeightAccessor.getMaxSectionY(); sectionY++) {
                SectionPos sectionPos = SectionPos.of(pos.x(), sectionY, pos.z());
                this.storage.put(sectionPos, Optional.empty());
            }
        } else {
            boolean versionChanged = packedChunk.versionChanged();

            for (int sectionY = this.levelHeightAccessor.getMinSectionY(); sectionY <= this.levelHeightAccessor.getMaxSectionY(); sectionY++) {
                SectionPos sectionPos = SectionPos.of(pos.x(), sectionY, pos.z());
                Optional<R> section = Optional.ofNullable(packedChunk.sectionsByY.get(sectionY))
                    .map(packed -> this.unpacker.apply((P) packed, () -> this.setDirty(sectionPos)));
                this.storage.put(sectionPos, section);
                section.ifPresent(s -> {
                    this.onSectionLoad(sectionPos);
                    if (versionChanged) {
                        this.setDirty(sectionPos);
                    }
                });
            }
        }
    }

    // ===== 参数改为 ChunkPos =====
    private void writeChunk(final ChunkPos chunkPos) {
        RegistryOps<Tag> registryOps = this.registryAccess.createSerializationContext(NbtOps.INSTANCE);
        Dynamic<Tag> tag = this.writeChunk(chunkPos, registryOps);
        Tag value = tag.getValue();
        if (value instanceof CompoundTag compoundTag) {
            this.simpleRegionStorage.write(chunkPos, compoundTag).exceptionally(throwable -> {
                this.errorReporter.reportChunkSaveFailure(throwable, this.simpleRegionStorage.storageInfo(), chunkPos);
                return null;
            });
        } else {
            LOGGER.error("Expected compound tag, got {}", value);
        }
    }

    private <T> Dynamic<T> writeChunk(final ChunkPos chunkPos, final DynamicOps<T> ops) {
        Map<T, T> sections = Maps.newHashMap();

        for (int sectionY = this.levelHeightAccessor.getMinSectionY(); sectionY <= this.levelHeightAccessor.getMaxSectionY(); sectionY++) {
            SectionPos sectionPos = SectionPos.of(chunkPos.x(), sectionY, chunkPos.z());
            Optional<R> r = this.storage.get(sectionPos);
            if (r != null && !r.isEmpty()) {
                DataResult<T> serializedSection = this.codec.encodeStart(ops, this.packer.apply(r.get()));
                String yName = Integer.toString(sectionY);
                serializedSection.resultOrPartial(LOGGER::error).ifPresent(s -> sections.put(ops.createString(yName), (T) s));
            }
        }

        return new Dynamic<>(
            ops,
            ops.createMap(
                ImmutableMap.of(
                    ops.createString("Sections"),
                    ops.createMap(sections),
                    ops.createString("DataVersion"),
                    ops.createInt(SharedConstants.getCurrentVersion().dataVersion().version())
                )
            )
        );
    }

    // ===== 移除 getKey 方法，直接使用 SectionPos =====

    // ===== 参数改为 SectionPos =====
    protected void onSectionLoad(final SectionPos sectionPos) {
    }

    // ===== 参数改为 SectionPos =====
    protected void setDirty(final SectionPos sectionPos) {
        Optional<R> r = this.storage.get(sectionPos);
        if (r != null && !r.isEmpty()) {
            this.dirtyChunks.add(sectionPos.chunk());
        } else {
            LOGGER.warn("No data for position: {}", sectionPos);
        }
    }

    public void flush(final ChunkPos chunkPos) {
        if (this.dirtyChunks.remove(chunkPos)) {
            this.writeChunk(chunkPos);
        }
    }

    @Override
    public void close() throws IOException {
        this.simpleRegionStorage.close();
    }

    private record PackedChunk<T>(Int2ObjectMap<T> sectionsByY, boolean versionChanged) {
        public static <T> SectionStorage.PackedChunk<T> parse(
            final Codec<T> codec,
            final DynamicOps<Tag> ops,
            final Tag tag,
            final SimpleRegionStorage simpleRegionStorage,
            final LevelHeightAccessor levelHeightAccessor
        ) {
            Dynamic<Tag> originalTag = new Dynamic<>(ops, tag);
            Dynamic<Tag> fixedTag = simpleRegionStorage.upgradeChunkTag(originalTag, 1945);
            boolean versionChanged = originalTag != fixedTag;
            OptionalDynamic<Tag> sections = fixedTag.get("Sections");
            Int2ObjectMap<T> sectionsByY = new Int2ObjectOpenHashMap<>();

            for (int sectionY = levelHeightAccessor.getMinSectionY(); sectionY <= levelHeightAccessor.getMaxSectionY(); sectionY++) {
                Optional<T> section = sections.get(Integer.toString(sectionY))
                    .result()
                    .flatMap(sectionData -> codec.parse((Dynamic<Tag>) sectionData).resultOrPartial(SectionStorage.LOGGER::error));
                if (section.isPresent()) {
                    sectionsByY.put(sectionY, section.get());
                }
            }

            return new SectionStorage.PackedChunk<>(sectionsByY, versionChanged);
        }
    }
}