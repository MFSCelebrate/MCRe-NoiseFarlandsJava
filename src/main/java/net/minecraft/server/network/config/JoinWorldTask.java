package net.minecraft.server.network.config;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.Consumer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.configuration.ClientboundFinishConfigurationPacket;
import net.minecraft.server.network.ConfigurationTask;

public class JoinWorldTask implements ConfigurationTask {
    public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type("join_world");

    @Override
    public void start(final Consumer<Packet<?>> connection) {
        connection.accept(ClientboundFinishConfigurationPacket.INSTANCE);
    }

    @Override
    public ConfigurationTask.Type type() {
        return TYPE;
    }
}