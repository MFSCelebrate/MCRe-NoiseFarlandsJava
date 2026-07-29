package net.minecraft.world.level.block;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class PlayerWallHeadBlock extends WallSkullBlock {
    public static final MapCodec<PlayerWallHeadBlock> CODEC = simpleCodec(PlayerWallHeadBlock::new);

    @Override
    public MapCodec<PlayerWallHeadBlock> codec() {
        return CODEC;
    }

    protected PlayerWallHeadBlock(final BlockBehaviour.Properties properties) {
        super(SkullBlock.Types.PLAYER, properties);
    }
}