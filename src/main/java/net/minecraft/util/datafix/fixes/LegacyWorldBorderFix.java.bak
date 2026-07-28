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
        // 世界边界已被移除，此修复不再需要执行任何操作
        // 返回一个空规则，直接传递数据而不做修改
        return this.fixTypeEverywhereTyped(
            "LegacyWorldBorderFix",
            this.getInputSchema().getType(DSL.remainderType()),
            typed -> typed
        );
    }
}