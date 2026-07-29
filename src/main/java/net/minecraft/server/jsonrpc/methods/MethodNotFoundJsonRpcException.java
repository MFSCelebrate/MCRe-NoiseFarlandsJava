package net.minecraft.server.jsonrpc.methods;
import it.unimi.dsi.fastutil.longs.LongSet;

public class MethodNotFoundJsonRpcException extends RuntimeException {
    public MethodNotFoundJsonRpcException(final String message) {
        super(message);
    }
}