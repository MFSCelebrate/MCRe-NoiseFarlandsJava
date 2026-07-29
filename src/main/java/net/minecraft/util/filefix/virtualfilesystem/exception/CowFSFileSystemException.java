package net.minecraft.util.filefix.virtualfilesystem.exception;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.nio.file.FileSystemException;

public class CowFSFileSystemException extends FileSystemException {
    public CowFSFileSystemException(final String message) {
        super(message);
    }
}