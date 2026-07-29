package net.minecraft.util.filefix.virtualfilesystem.exception;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.nio.file.DirectoryNotEmptyException;

public class CowFSDirectoryNotEmptyException extends DirectoryNotEmptyException {
    public CowFSDirectoryNotEmptyException(final String message) {
        super(message);
    }
}