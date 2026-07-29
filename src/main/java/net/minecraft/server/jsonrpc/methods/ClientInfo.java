package net.minecraft.server.jsonrpc.methods;
import it.unimi.dsi.fastutil.longs.LongSet;

public record ClientInfo(Integer connectionId) {
   public static ClientInfo of(final Integer connectionId) {
      return new ClientInfo(connectionId);
   }
}
