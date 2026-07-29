package net.minecraft.client.renderer.blockentity.state;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.level.block.HangingSignBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HangingSignRenderState extends SignRenderState {
    public HangingSignBlock.Attachment attachmentType = HangingSignBlock.Attachment.CEILING;
}