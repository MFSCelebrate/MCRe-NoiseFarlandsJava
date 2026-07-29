package net.minecraft.world.entity.ai.goal;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.EnumSet;

public abstract class JumpGoal extends Goal {
    public JumpGoal() {
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.JUMP));
    }
}