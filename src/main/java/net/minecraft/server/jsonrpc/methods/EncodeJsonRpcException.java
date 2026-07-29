package net.minecraft.server.jsonrpc.methods;
import it.unimi.dsi.fastutil.longs.LongSet;

public class EncodeJsonRpcException extends RuntimeException {
    public EncodeJsonRpcException(final String message) {
        super(message);
    }
}