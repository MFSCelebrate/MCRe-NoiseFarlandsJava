package net.minecraft.util.filefix;
import it.unimi.dsi.fastutil.longs.LongSet;

public class AtomicMoveNotSupportedFileFixException extends FileFixException {
    public AtomicMoveNotSupportedFileFixException(final FileSystemCapabilities fileSystemCapabilities) {
        super(null, fileSystemCapabilities);
    }
}