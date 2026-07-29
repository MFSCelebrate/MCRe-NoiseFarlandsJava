package net.minecraft.client;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.realmsclient.client.RealmsClient;
import net.minecraft.client.main.GameConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record GameLoadCookie(RealmsClient realmsClient, GameConfig.QuickPlayData quickPlayData) {
}