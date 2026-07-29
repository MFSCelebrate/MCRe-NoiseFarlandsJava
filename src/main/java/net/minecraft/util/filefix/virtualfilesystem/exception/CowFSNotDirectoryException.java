package net.minecraft.util.filefix.virtualfilesystem.exception;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.nio.file.NotDirectoryException;

public class CowFSNotDirectoryException extends NotDirectoryException {
    public CowFSNotDirectoryException(final String message) {
        super(message);
    }
}