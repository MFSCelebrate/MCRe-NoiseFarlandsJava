package net.minecraft.util.filefix.virtualfilesystem.exception;
import it.unimi.dsi.fastutil.longs.LongSet;

public class CowFSSymlinkException extends CowFSCreationException {
    public CowFSSymlinkException(final String message) {
        super(message);
    }
}