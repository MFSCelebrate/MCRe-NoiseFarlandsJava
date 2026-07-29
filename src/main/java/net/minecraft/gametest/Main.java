package net.minecraft.gametest;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.SharedConstants;
import net.minecraft.gametest.framework.GameTestMainUtil;

public class Main {
    public static void main(final String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        GameTestMainUtil.runGameTestServer(args, path -> {});
    }
}