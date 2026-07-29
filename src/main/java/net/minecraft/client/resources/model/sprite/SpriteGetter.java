package net.minecraft.client.resources.model.sprite;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SpriteGetter {
    TextureAtlasSprite get(SpriteId id);
}