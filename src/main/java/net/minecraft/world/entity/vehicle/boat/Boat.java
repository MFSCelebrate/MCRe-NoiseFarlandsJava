package net.minecraft.world.entity.vehicle.boat;
import it.unimi.dsi.fastutil.longs.LongSet;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public class Boat extends AbstractBoat {
    public Boat(final EntityType<? extends Boat> type, final Level level, final Supplier<Item> dropItem) {
        super(type, level, dropItem);
    }

    @Override
    protected double rideHeight(final EntityDimensions dimensions) {
        return dimensions.height() / 3.0F;
    }
}