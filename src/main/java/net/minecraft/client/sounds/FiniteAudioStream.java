package net.minecraft.client.sounds;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.io.IOException;
import java.nio.ByteBuffer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface FiniteAudioStream extends AudioStream {
    ByteBuffer readAll() throws IOException;
}