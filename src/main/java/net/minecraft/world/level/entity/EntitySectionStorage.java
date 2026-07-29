package net.minecraft.world.level.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import it.unimi.dsi.fastutil.objects.Object2ObjectFunction;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectAVLTreeSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSortedSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.core.SectionPos;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;

public class EntitySectionStorage<T extends EntityAccess> {
    public static final int CHONKY_ENTITY_SEARCH_GRACE = 2;
    public static final int MAX_NON_CHONKY_ENTITY_SIZE = 4;
    private final Class<T> entityClass;
    // ===== 修改：使用 SectionPos 作为键 =====
    private final Object2ObjectFunction<ChunkPos, Visibility> initialSectionVisibility;
    private final Object2ObjectMap<SectionPos, EntitySection<T>> sections = new Object2ObjectOpenHashMap<>();
    // ===== 使用 SectionPos 的排序集合，按 x -> y -> z 顺序 =====
    private final ObjectSortedSet<SectionPos> sectionIds = new ObjectAVLTreeSet<>(
        Comparator.comparingLong((SectionPos a) -> a.getLongX())
            .thenComparingLong(a -> a.getLongY())
            .thenComparingLong(a -> a.getLongZ())
    );

    public EntitySectionStorage(final Class<T> entityClass, final Object2ObjectFunction<ChunkPos, Visibility> initialSectionVisibility) {
        this.entityClass = entityClass;
        this.initialSectionVisibility = initialSectionVisibility;
    }

    public void forEachAccessibleNonEmptySection(final AABB bb, final AbortableIterationConsumer<EntitySection<T>> output) {
        int xMin = SectionPos.posToSectionCoord(bb.minX - 2.0);
        int yMin = SectionPos.posToSectionCoord(bb.minY - 4.0);
        int zMin = SectionPos.posToSectionCoord(bb.minZ - 2.0);
        int xMax = SectionPos.posToSectionCoord(bb.maxX + 2.0);
        int yMax = SectionPos.posToSectionCoord(bb.maxY + 0.0);
        int zMax = SectionPos.posToSectionCoord(bb.maxZ + 2.0);

        // ===== 遍历 sectionIds，使用 SectionPos 比较 =====
        for (SectionPos sectionPos : this.sectionIds) {
            int x = sectionPos.getX();
            int y = sectionPos.getY();
            int z = sectionPos.getZ();
            if (x >= xMin && x <= xMax && y >= yMin && y <= yMax && z >= zMin && z <= zMax) {
                EntitySection<T> entitySection = this.sections.get(sectionPos);
                if (entitySection != null
                    && !entitySection.isEmpty()
                    && entitySection.getStatus().isAccessible()
                    && output.accept(entitySection).shouldAbort()) {
                    return;
                }
            }
        }
    }

    // ===== 返回 Stream<SectionPos> =====
    public Stream<SectionPos> getExistingSectionPositionsInChunk(final ChunkPos chunkPos) {
        ObjectSortedSet<SectionPos> chunkSections = this.getChunkSections(chunkPos);
        if (chunkSections.isEmpty()) {
            return Stream.empty();
        }
        return chunkSections.stream();
    }

    // ===== 返回 ObjectSortedSet<SectionPos> =====
    private ObjectSortedSet<SectionPos> getChunkSections(final ChunkPos chunkPos) {
        int x = chunkPos.x;
        int z = chunkPos.z;
        // 构造边界 SectionPos
        SectionPos lowerBound = SectionPos.of(x, Integer.MIN_VALUE, z);
        SectionPos upperBound = SectionPos.of(x, Integer.MAX_VALUE, z);
        return this.sectionIds.subSet(lowerBound, upperBound);
    }

    // ===== 返回 Stream<EntitySection<T>> =====
    public Stream<EntitySection<T>> getExistingSectionsInChunk(final ChunkPos chunkPos) {
        return this.getExistingSectionPositionsInChunk(chunkPos).map(this.sections::get).filter(Objects::nonNull);
    }

    // ===== 从 SectionPos 获取 ChunkPos =====
    private static ChunkPos getChunkPosFromSectionPos(final SectionPos sectionPos) {
        return new ChunkPos(sectionPos.getX(), sectionPos.getZ());
    }

    // ===== 参数改为 SectionPos =====
    public EntitySection<T> getOrCreateSection(final SectionPos key) {
        return this.sections.computeIfAbsent(key, this::createSection);
    }

    public @Nullable EntitySection<T> getSection(final SectionPos key) {
        return this.sections.get(key);
    }

    private EntitySection<T> createSection(final SectionPos sectionPos) {
        ChunkPos chunkPos = getChunkPosFromSectionPos(sectionPos);
        Visibility chunkStatus = this.initialSectionVisibility.get(chunkPos);
        this.sectionIds.add(sectionPos);
        return new EntitySection<>(this.entityClass, chunkStatus);
    }

    // ===== 返回 Set<ChunkPos> =====
    public Set<ChunkPos> getAllChunksWithExistingSections() {
        Set<ChunkPos> chunks = new ObjectOpenHashSet<>();
        for (SectionPos sectionPos : this.sections.keySet()) {
            chunks.add(getChunkPosFromSectionPos(sectionPos));
        }
        return chunks;
    }

    public void getEntities(final AABB bb, final AbortableIterationConsumer<T> output) {
        this.forEachAccessibleNonEmptySection(bb, section -> section.getEntities(bb, output));
    }

    public <U extends T> void getEntities(final EntityTypeTest<T, U> type, final AABB bb, final AbortableIterationConsumer<U> consumer) {
        this.forEachAccessibleNonEmptySection(bb, section -> section.getEntities(type, bb, consumer));
    }

    // ===== 参数改为 SectionPos =====
    public void remove(final SectionPos sectionKey) {
        this.sections.remove(sectionKey);
        this.sectionIds.remove(sectionKey);
    }

    @VisibleForDebug
    public int count() {
        return this.sectionIds.size();
    }
}