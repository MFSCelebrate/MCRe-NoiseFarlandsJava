package net.minecraft.client.renderer.item.properties.numeric;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface RangeSelectItemModelProperty {
    float get(ItemStack itemStack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed);

    MapCodec<? extends RangeSelectItemModelProperty> type();
}