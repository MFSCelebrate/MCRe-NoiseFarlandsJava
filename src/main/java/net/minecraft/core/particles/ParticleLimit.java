package net.minecraft.core.particles;
import it.unimi.dsi.fastutil.longs.LongSet;

public record ParticleLimit(int limit) {
    public static final ParticleLimit SPORE_BLOSSOM = new ParticleLimit(1000);
}