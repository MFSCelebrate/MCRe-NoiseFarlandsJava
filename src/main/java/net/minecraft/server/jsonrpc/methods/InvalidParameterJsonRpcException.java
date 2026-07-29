package net.minecraft.server.jsonrpc.methods;
import it.unimi.dsi.fastutil.longs.LongSet;

public class InvalidParameterJsonRpcException extends RuntimeException {
    public InvalidParameterJsonRpcException(final String message) {
        super(message);
    }
}