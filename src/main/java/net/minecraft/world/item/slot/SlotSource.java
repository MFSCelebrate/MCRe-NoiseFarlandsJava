package net.minecraft.world.item.slot;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootContextUser;

public interface SlotSource extends LootContextUser {
    MapCodec<? extends SlotSource> codec();

    SlotCollection provide(LootContext context);
}