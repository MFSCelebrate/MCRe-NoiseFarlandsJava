package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.network.chat.Component;

public interface ProgressListener {
    void progressStartNoAbort(Component string);

    void progressStart(Component string);

    void progressStage(Component string);

    void progressStagePercentage(int i);

    void stop();
}