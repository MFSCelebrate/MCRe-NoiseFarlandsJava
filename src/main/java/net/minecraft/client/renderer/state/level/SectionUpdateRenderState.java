package net.minecraft.client.renderer.state.level;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record SectionUpdateRenderState(long sectionNode, boolean playerChanged, RenderSectionRegion region) {
}