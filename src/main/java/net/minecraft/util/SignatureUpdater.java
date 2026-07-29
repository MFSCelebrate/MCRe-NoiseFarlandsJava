package net.minecraft.util;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.security.SignatureException;

@FunctionalInterface
public interface SignatureUpdater {
    void update(SignatureUpdater.Output output) throws SignatureException;

    @FunctionalInterface
    interface Output {
        void update(byte[] payload) throws SignatureException;
    }
}