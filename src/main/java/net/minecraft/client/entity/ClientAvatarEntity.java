package net.minecraft.client.entity;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public interface ClientAvatarEntity {
    ClientAvatarState avatarState();

    PlayerSkin getSkin();

    Parrot.@Nullable Variant getParrotVariantOnShoulder(boolean left);

    boolean showExtraEars();
}