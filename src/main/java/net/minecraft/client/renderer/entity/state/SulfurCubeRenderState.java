package net.minecraft.client.renderer.entity.state;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SulfurCubeRenderState extends SlimeRenderState {
    public BlockModelRenderState containedBlock = new BlockModelRenderState();
    public float fuseRemainingTicks;
}