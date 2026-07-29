package net.minecraft.util.filefix;
import it.unimi.dsi.fastutil.longs.LongSet;

public class CanceledFileFixException extends FileFixException {
    public CanceledFileFixException() {
        super(null, null);
    }
}