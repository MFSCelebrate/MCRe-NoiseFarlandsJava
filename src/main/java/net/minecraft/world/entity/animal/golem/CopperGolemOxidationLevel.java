package net.minecraft.world.entity.animal.golem;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public record CopperGolemOxidationLevel(
    SoundEvent spinHeadSound, SoundEvent hurtSound, SoundEvent deathSound, SoundEvent stepSound, Identifier texture, Identifier eyeTexture
) {
}