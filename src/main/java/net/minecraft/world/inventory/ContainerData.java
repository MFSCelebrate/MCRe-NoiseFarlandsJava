package net.minecraft.world.inventory;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface ContainerData {
    int get(final int dataId);

    void set(final int dataId, final int value);

    int getCount();
}