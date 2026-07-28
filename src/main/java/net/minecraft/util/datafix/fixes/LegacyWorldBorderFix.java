package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;

public class LegacyWorldBorderFix extends DataFix {
    public LegacyWorldBorderFix(final Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        // 空操作
        return this.fixTypeEverywhere(
            "LegacyWorldBorderFix",
            DSL.remainderType(),
            dynamic -> dynamic
        );
    }
}