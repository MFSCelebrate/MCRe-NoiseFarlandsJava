package net.minecraft.core;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.Lifecycle;
import java.util.Optional;
import net.minecraft.server.packs.repository.KnownPack;

public record RegistrationInfo(Optional<KnownPack> knownPackInfo, Lifecycle lifecycle) {
    public static final RegistrationInfo BUILT_IN = new RegistrationInfo(Optional.empty(), Lifecycle.stable());
}