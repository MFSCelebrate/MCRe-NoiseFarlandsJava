package net.minecraft.world.level.validation;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.nio.file.Path;

public record ForbiddenSymlinkInfo(Path link, Path target) {
}