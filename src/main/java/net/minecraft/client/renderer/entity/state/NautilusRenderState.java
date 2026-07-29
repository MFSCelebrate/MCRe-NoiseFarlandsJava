package net.minecraft.client.renderer.entity.state;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.animal.nautilus.ZombieNautilusVariant;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class NautilusRenderState extends LivingEntityRenderState {
    public ItemStack saddle = ItemStack.EMPTY;
    public ItemStack bodyArmorItem = ItemStack.EMPTY;
    public @Nullable ZombieNautilusVariant variant;
}