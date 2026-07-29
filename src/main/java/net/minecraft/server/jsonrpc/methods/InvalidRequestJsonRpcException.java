package net.minecraft.server.jsonrpc.methods;
import it.unimi.dsi.fastutil.longs.LongSet;

public class InvalidRequestJsonRpcException extends RuntimeException {
    public InvalidRequestJsonRpcException(final String message) {
        super(message);
    }
}