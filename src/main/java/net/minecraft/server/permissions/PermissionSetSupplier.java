package net.minecraft.server.permissions;
import it.unimi.dsi.fastutil.longs.LongSet;

public interface PermissionSetSupplier {
    PermissionSet permissions();
}