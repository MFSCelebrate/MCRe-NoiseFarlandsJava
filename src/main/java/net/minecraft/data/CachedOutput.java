package net.minecraft.data;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.google.common.hash.HashCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.FileUtil;

public interface CachedOutput {
    CachedOutput NO_CACHE = (path, input, hash) -> {
        FileUtil.createDirectoriesSafe(path.getParent());
        Files.write(path, input);
    };

    void writeIfNeeded(Path path, byte[] input, HashCode hash) throws IOException;
}