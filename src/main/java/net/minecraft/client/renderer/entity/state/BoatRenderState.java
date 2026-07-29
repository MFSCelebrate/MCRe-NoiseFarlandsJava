package net.minecraft.client.renderer.entity.state;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BoatRenderState extends EntityRenderState {
    public float yRot;
    public int hurtDir;
    public float hurtTime;
    public float damageTime;
    public float bubbleAngle;
    public boolean isUnderWater;
    public float rowingTimeLeft;
    public float rowingTimeRight;
}