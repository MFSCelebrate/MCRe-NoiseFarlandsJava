package net.minecraft.client.resources.server;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackDownloader {
    void download(Map<UUID, DownloadQueue.DownloadRequest> requests, Consumer<DownloadQueue.BatchResult> output);
}