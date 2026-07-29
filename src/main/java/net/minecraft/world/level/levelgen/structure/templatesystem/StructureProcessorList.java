package net.minecraft.world.level.levelgen.structure.templatesystem;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.List;

public class StructureProcessorList {
    private final List<StructureProcessor> list;

    public StructureProcessorList(final List<StructureProcessor> list) {
        this.list = list;
    }

    public List<StructureProcessor> list() {
        return this.list;
    }

    @Override
    public String toString() {
        return "ProcessorList[" + this.list + "]";
    }
}