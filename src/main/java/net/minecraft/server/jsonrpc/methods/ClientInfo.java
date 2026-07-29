package net.minecraft.server.jsonrpc.methods;

public record ClientInfo(Integer connectionId) {
    public static ClientInfo of(final Integer connectionId) {
        return new ClientInfo(connectionId);
    }

    public Integer connectionId() {
        // $VF: Couldn't be decompiled
        // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
        //
        // Bytecode:
        // 0: aload 0
        // 1: getfield net/minecraft/server/jsonrpc/methods/ClientInfo.connectionId Ljava/lang/Integer;
        // 4: areturn
    }
}