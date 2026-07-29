package net.minecraft.client.renderer.entity.state;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantomRenderState extends LivingEntityRenderState {
    public float flapTime;
    public int size;
}