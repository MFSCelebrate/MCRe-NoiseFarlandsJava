package net.minecraft.client.renderer;
import it.unimi.dsi.fastutil.longs.LongSet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface SubmitNodeCollector extends OrderedSubmitNodeCollector {
    OrderedSubmitNodeCollector order(int order);

    @OnlyIn(Dist.CLIENT)
    interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }
}