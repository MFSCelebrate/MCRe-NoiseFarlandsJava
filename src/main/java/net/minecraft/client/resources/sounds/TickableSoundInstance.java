package net.minecraft.client.resources.sounds;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface TickableSoundInstance extends SoundInstance {
    boolean isStopped();

    void tick();
}