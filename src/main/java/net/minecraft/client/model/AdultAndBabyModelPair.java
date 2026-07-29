package net.minecraft.client.model;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record AdultAndBabyModelPair<T extends Model<?>>(T adultModel, T babyModel) {
    public T getModel(final boolean isBaby) {
        return isBaby ? this.babyModel : this.adultModel;
    }
}