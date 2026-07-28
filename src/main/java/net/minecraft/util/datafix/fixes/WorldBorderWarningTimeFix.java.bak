package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class WorldBorderWarningTimeFix extends DataFix {
    public WorldBorderWarningTimeFix(final Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        // 空操作，直接返回原数据
        return this.fixTypeEverywhereTyped(
            "WorldBorderWarningTimeFix",
            this.getInputSchema().getType(DSL.remainderType()),
            typed -> typed
        );
    }
}