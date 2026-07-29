package net.minecraft.client.renderer.block.model.properties.conditional;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ConditionalBlockModelProperty {
    boolean get(BlockState state);
}