package net.minecraft.client.sounds;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface Weighted<T> {
    int getWeight();

    T getSound(RandomSource random);

    void preloadIfRequired(SoundEngine soundEngine);
}