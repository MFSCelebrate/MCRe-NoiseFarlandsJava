package net.minecraft.client.renderer.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface RenderLayerParent<S extends EntityRenderState, M extends EntityModel<? super S>> {
    M getModel();
}