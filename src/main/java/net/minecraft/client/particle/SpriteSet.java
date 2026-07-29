package net.minecraft.client.particle;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SpriteSet {
    TextureAtlasSprite get(final int index, final int max);

    TextureAtlasSprite get(RandomSource random);

    TextureAtlasSprite first();
}