package net.minecraft.client.gui.screens.worldselection;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface WorldCreationContextMapper {
    WorldCreationContext apply(
        final ReloadableServerResources managers, final LayeredRegistryAccess<RegistryLayer> registries, final DataPackReloadCookie cookie
    );
}