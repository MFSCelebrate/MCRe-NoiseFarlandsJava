package net.minecraft.client;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

/**
 * 环形区块存储（64 位无限世界适配）。
 * 使用 SectionPos 对象作为键，彻底摆脱 32 位打包限制。
 *
 * @param <T> 存储值类型，必须实现 Value 接口
 */
@OnlyIn(Dist.CLIENT)
public class RotatingSectionStorage<T extends RotatingSectionStorage.Value> implements Iterable<T> {

    private final RotatingSectionStorage.Node<T>[] nodes;
    private final int radius;
    private final int minY;
    private final int maxY;
    private final int sectionGridSizeY;
    private final int sectionGridSizeXZ;

    // 当前中心位置（SectionPos 对象）
    private SectionPos centerSectionPos = SectionPos.of(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);

    /**
     * 构造函数。
     *
     * @param radius       水平半径（区块数）
     * @param minY         最小 Y 区块坐标
     * @param maxY         最大 Y 区块坐标
     * @param valueCreator 值工厂，用于创建每个网格位置的值
     */
    public RotatingSectionStorage(
            final int radius,
            final int minY,
            final int maxY,
            final RotatingSectionStorage.ValueCreator<T> valueCreator
    ) {
        this.radius = radius;
        this.minY = minY;
        this.maxY = maxY;
        this.sectionGridSizeY = maxY - minY + 1;
        this.sectionGridSizeXZ = radius * 2 + 1;
        int totalSections = this.sectionGridSizeXZ * this.sectionGridSizeXZ * this.sectionGridSizeY;
        this.nodes = new Node[totalSections];

        // 初始化所有网格位置
        for (int x = 0; x < this.sectionGridSizeXZ; x++) {
            for (int y = 0; y < this.sectionGridSizeY; y++) {
                for (int z = 0; z < this.sectionGridSizeXZ; z++) {
                    int index = this.getSectionIndex(x, y, z);
                    // 初始位置：相对中心 (0,0,0) 的偏移
                    int sectionX = x - radius;
                    int sectionY = y + minY;
                    int sectionZ = z - radius;
                    SectionPos initialPos = SectionPos.of(sectionX, sectionY, sectionZ);
                    this.nodes[index] = new Node<>(valueCreator.createValue(index, initialPos));
                }
            }
        }
    }

    /**
     * 重新定位中心。
     *
     * @param newCenterSectionPos 新的中心 SectionPos
     * @return 如果中心发生变化则返回 true
     */
    public boolean repositionCenter(final SectionPos newCenterSectionPos) {
        if (newCenterSectionPos.equals(this.centerSectionPos)) {
            return false;
        }

        long lowestX = newCenterSectionPos.getLongX() - this.radius;
        long lowestZ = newCenterSectionPos.getLongZ() - this.radius;

        for (int gridX = 0; gridX < this.sectionGridSizeXZ; gridX++) {
            // 计算实际世界 X 坐标（环绕）
            long newSectionX = lowestX + Math.floorMod(gridX - lowestX, this.sectionGridSizeXZ);

            for (int gridZ = 0; gridZ < this.sectionGridSizeXZ; gridZ++) {
                long newSectionZ = lowestZ + Math.floorMod(gridZ - lowestZ, this.sectionGridSizeXZ);

                for (int gridY = 0; gridY < this.sectionGridSizeY; gridY++) {
                    long newSectionY = this.minY + gridY;
                    Node<T> node = this.nodes[this.getSectionIndex(gridX, gridY, gridZ)];
                    SectionPos newPos = SectionPos.of(newSectionX, newSectionY, newSectionZ);
                    // 仅当位置变化时才更新（避免不必要的对象赋值）
                    if (!newPos.equals(node.value.getSectionPos())) {
                        node.value.setSectionPos(newPos);
                    }
                }
            }
        }

        this.centerSectionPos = newCenterSectionPos;
        return true;
    }

    // ==================== 查询接口 ====================

    public int radius() {
        return this.radius;
    }

    public int minY() {
        return this.minY;
    }

    public int maxY() {
        return this.maxY;
    }

    public int height() {
        return this.sectionGridSizeY;
    }

    public SectionPos centerSectionPos() {
        return this.centerSectionPos;
    }

    /**
     * 根据 BlockPos 获取值。
     */
    public @Nullable T getValueAt(final BlockPos pos) {
        return this.getValue(SectionPos.of(pos));
    }

    /**
     * 根据 SectionPos 对象获取值。
     */
    public @Nullable T getValue(final SectionPos sectionPos) {
        long sx = sectionPos.getLongX();
        long sy = sectionPos.getLongY();
        long sz = sectionPos.getLongZ();
        return this.getValue(sx, sy, sz);
    }

    /**
     * 根据坐标获取值。
     */
    public @Nullable T getValue(final long sectionX, final long sectionY, final long sectionZ) {
        if (!this.containsSection(sectionX, sectionY, sectionZ)) {
            return null;
        }

        // 计算相对网格坐标（环绕）
        int gridX = Math.floorMod((int) (sectionX - this.centerSectionPos.getLongX() + this.radius), this.sectionGridSizeXZ);
        int gridY = (int) (sectionY - this.minY);
        int gridZ = Math.floorMod((int) (sectionZ - this.centerSectionPos.getLongZ() + this.radius), this.sectionGridSizeXZ);
        return this.nodes[this.getSectionIndex(gridX, gridY, gridZ)].value;
    }

    /**
     * 判断给定 Section 坐标是否在当前环形范围内。
     */
    private boolean containsSection(final long sectionX, final long sectionY, final long sectionZ) {
        if (sectionY < this.minY || sectionY > this.maxY) {
            return false;
        }
        long cx = this.centerSectionPos.getLongX();
        long cz = this.centerSectionPos.getLongZ();
        return sectionX >= cx - this.radius && sectionX <= cx + this.radius &&
               sectionZ >= cz - this.radius && sectionZ <= cz + this.radius;
    }

    /**
     * 计算数组索引（基于网格坐标）。
     */
    private int getSectionIndex(final int gridX, final int gridY, final int gridZ) {
        return (gridZ * this.sectionGridSizeY + gridY) * this.sectionGridSizeXZ + gridX;
    }

    // ==================== 迭代器 ====================

    @Override
    public Iterator<T> iterator() {
        return new Iterator<T>() {
            private int i = 0;

            @Override
            public boolean hasNext() {
                return this.i < RotatingSectionStorage.this.nodes.length;
            }

            @Override
            public T next() {
                if (this.i >= RotatingSectionStorage.this.nodes.length) {
                    throw new NoSuchElementException();
                }
                return RotatingSectionStorage.this.nodes[this.i++].value;
            }
        };
    }

    @Override
    public void forEach(final Consumer<? super T> action) {
        for (Node<T> node : this.nodes) {
            action.accept(node.value);
        }
    }

    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(this.iterator(), this.nodes.length, 0);
    }

    public int size() {
        return this.nodes.length;
    }

    // ==================== 内部嵌套类 ====================

    @OnlyIn(Dist.CLIENT)
    private record Node<T>(T value) {
    }

    /**
     * 值接口：使用 SectionPos 对象替代 long 打包键。
     */
    @OnlyIn(Dist.CLIENT)
    public interface Value {
        void setSectionPos(SectionPos pos);
        SectionPos getSectionPos();
    }

    /**
     * 值工厂接口。
     */
    @OnlyIn(Dist.CLIENT)
    public interface ValueCreator<T extends RotatingSectionStorage.Value> {
        T createValue(int index, SectionPos initialPos);
    }
}