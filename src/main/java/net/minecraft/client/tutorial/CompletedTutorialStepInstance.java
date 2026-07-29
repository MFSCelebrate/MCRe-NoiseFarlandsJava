package net.minecraft.client.tutorial;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CompletedTutorialStepInstance implements TutorialStepInstance {
    public CompletedTutorialStepInstance(final Tutorial tutorial) {
    }
}