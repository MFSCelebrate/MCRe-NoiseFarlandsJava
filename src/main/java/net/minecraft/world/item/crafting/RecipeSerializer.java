package net.minecraft.world.item.crafting;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record RecipeSerializer<T extends Recipe<?>>(MapCodec<T> codec, @Deprecated StreamCodec<RegistryFriendlyByteBuf, T> streamCodec) {
}