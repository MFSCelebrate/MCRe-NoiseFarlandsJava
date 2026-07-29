package net.minecraft.client.renderer.block.model.properties.select;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface SelectBlockModelProperty<T> {
    @Nullable T get(BlockState blockState, BlockDisplayContext displayContext);
}