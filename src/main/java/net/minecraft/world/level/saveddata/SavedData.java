package net.minecraft.world.level.saveddata;
import it.unimi.dsi.fastutil.longs.LongSet;

public abstract class SavedData {
    private boolean dirty;

    public void setDirty() {
        this.setDirty(true);
    }

    public void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

    public boolean isDirty() {
        return this.dirty;
    }
}