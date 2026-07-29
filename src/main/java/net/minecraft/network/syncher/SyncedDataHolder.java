package net.minecraft.network.syncher;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

public interface SyncedDataHolder {
    void onSyncedDataUpdated(EntityDataAccessor<?> accessor);

    void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> updatedItems);
}