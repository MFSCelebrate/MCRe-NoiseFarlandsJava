package net.minecraft.client.gui.screens.worldselection;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔥 MCRe NoiseFarlands —— 世界主要设置界面
 * 
 * 使用原版 ObjectSelectionList 实现稳定滚动
 */
@OnlyIn(Dist.CLIENT)
public class WorldMainSettingScreen extends Screen {

    private static final int CONTENT_WIDTH = 310;
    private static final int SLIDER_MIN = 10000000;
    private static final int SLIDER_MAX = 33554432;
    private static final int STRIPE_MAX = 33554432;
    private static final int ITEM_HEIGHT = 28;

    private final Screen parent;
    private final WorldCreationContext settings;
    private final FarLandsConfigData configData = new FarLandsConfigData();
    private SettingList list;

    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        super(Component.literal("边境旅者 配置"));
        this.parent = parent;
        this.settings = settings;
    }

    @Override
    protected void init() {
        // 标题
        StringWidget titleWidget = new StringWidget(
            this.title.copy().withStyle(ChatFormatting.BOLD), this.font
        );
        titleWidget.setPosition(this.width / 2 - titleWidget.getWidth() / 2, 8);
        this.addRenderableWidget(titleWidget);

        // 提示
        StringWidget hintWidget = new StringWidget(
            Component.literal("§7配置边境之地相关参数"), this.font
        );
        hintWidget.setPosition(this.width / 2 - hintWidget.getWidth() / 2, 8 + 9 + 4);
        this.addRenderableWidget(hintWidget);

        // 可滚动的设置列表
        int listTop = 8 + 9 + 4 + 9 + 12;
        int listBottom = this.height - 40;
        this.list = new SettingList(this.minecraft, this.width, listBottom - listTop, listTop, ITEM_HEIGHT);
        this.addRenderableWidget(this.list);

        // 底部按钮
        this.addRenderableWidget(
            Button.builder(Component.literal("完成"), b -> this.onDone())
                .pos(this.width / 2 - 110, this.height - 32)
                .width(100).build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("取消"), b -> this.onClose())
                .pos(this.width / 2 + 10, this.height - 32)
                .width(100).build()
        );
    }

    private void onDone() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    @Override
    public void repositionElements() {
        if (this.list != null) {
            int listTop = 8 + 9 + 4 + 9 + 12;
            int listBottom = this.height - 40;
            this.list.updateSizeAndPosition(this.width, listBottom - listTop, 0, listTop);
        }
    }

    // ==================== 配置数据类 ====================
    public static class FarLandsConfigData {
        public int farLandsDistance = 12550824;
        public int stripeLandsDistance = 16777216;
        public boolean enableSkyGrid = false;
        public boolean forceSkyGrid = false;
        public boolean removeWorldBorder = true;
        public boolean removeWorldBoundary = true;
        public boolean removeCoordinateLimits = true;
        public String precisionMode = "32bit";
        public boolean fcDisableBlockCollision = true;
        public boolean fcDisableBlockEffect = true;
        public boolean fcDisableFluidCollision = true;
        public boolean fcDisableFluidFlowing = true;
        public boolean fcDisableBlockInteraction = true;
        public boolean fcDisableExplosionEffect = true;
        public boolean fcDisableLadderBehavior = true;
        public boolean fcDisablePistonBehavior = true;
        public boolean generateOotsLaboratory = true;
    }

    // ==================== 设置列表 ====================
    private class SettingList extends ObjectSelectionList<SettingList.SettingEntry> {

        public SettingList(final Minecraft minecraft, final int width, final int height, final int y, final int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;

            // 填充设置项
            this.addSection("§6§l边境之地设置");

            this.addSlider("边境之地距离",
                () -> (double)(configData.farLandsDistance - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN),
                val -> { configData.farLandsDistance = SLIDER_MIN + (int)(val * (SLIDER_MAX - SLIDER_MIN)); },
                () -> "边境之地距离: §e" + String.format("%,d", configData.farLandsDistance));

            this.addLabel("§7边境之地生成位置与世界原点的距离，该设置用于各类机制的判定，不影响地形生成");

            this.addSlider("条纹之地距离",
                () -> configData.stripeLandsDistance == -1 ? 0.0 : (double)configData.stripeLandsDistance / STRIPE_MAX,
                val -> { configData.stripeLandsDistance = val < 0.01 ? -1 : (int)(val * STRIPE_MAX); },
                () -> configData.stripeLandsDistance == -1 ? "条纹之地距离: §c禁用" : "条纹之地距离: §e" + String.format("%,d", configData.stripeLandsDistance));

            this.addToggle("启用天空网格", () -> configData.enableSkyGrid, val -> configData.enableSkyGrid = val);
            this.addToggle("强制生成天空网格", () -> configData.forceSkyGrid, val -> configData.forceSkyGrid = val);

            this.addSection("§6§l世界边界设置");
            this.addToggle("移除世界边界", () -> configData.removeWorldBorder, val -> configData.removeWorldBorder = val);
            this.addToggle("移除世界界限", () -> configData.removeWorldBoundary, val -> configData.removeWorldBoundary = val);
            this.addToggle("移除坐标限制", () -> configData.removeCoordinateLimits, val -> configData.removeCoordinateLimits = val);

            this.addSection("§b§l精度系统");
            this.addCycle("精度模式", new String[]{"32bit", "64bit", "256bit"},
                () -> configData.precisionMode,
                val -> configData.precisionMode = val,
                mode -> switch (mode) {
                    case "32bit" -> "32 位";
                    case "64bit" -> "64 位";
                    case "256bit" -> "256 位";
                    default -> mode;
                }
            );

            this.addSection("§d§l假区块设置");
            this.addToggle("禁用假区块中的方块碰撞", () -> configData.fcDisableBlockCollision, val -> configData.fcDisableBlockCollision = val);
            this.addToggle("禁用假区块中的方块效果", () -> configData.fcDisableBlockEffect, val -> configData.fcDisableBlockEffect = val);
            this.addToggle("禁用假区块中的流体碰撞", () -> configData.fcDisableFluidCollision, val -> configData.fcDisableFluidCollision = val);
            this.addToggle("使流体无法流过假区块边界", () -> configData.fcDisableFluidFlowing, val -> configData.fcDisableFluidFlowing = val);
            this.addToggle("使实体无法与假区块中的方块交互", () -> configData.fcDisableBlockInteraction, val -> configData.fcDisableBlockInteraction = val);
            this.addToggle("使假区块中的方块不受爆炸影响", () -> configData.fcDisableExplosionEffect, val -> configData.fcDisableExplosionEffect = val);
            this.addToggle("使假区块中的梯子无法攀爬", () -> configData.fcDisableLadderBehavior, val -> configData.fcDisableLadderBehavior = val);
            this.addToggle("使假区块中的活塞无法正常工作（实验性）", () -> configData.fcDisablePistonBehavior, val -> configData.fcDisablePistonBehavior = val);

            this.addSection("§a§l结构生成设置");
            this.addToggle("生成岩石之令实验室", () -> configData.generateOotsLaboratory, val -> configData.generateOotsLaboratory = val);
        }

        @Override
        public int getRowWidth() {
            return CONTENT_WIDTH;
        }

        @Override
        protected int scrollBarX() {
            return this.getRowRight() + 6;
        }

        // ===== 辅助方法 =====

        private void addSection(String text) {
            this.addEntry(new SectionEntry(Component.literal(text)), 24);
        }

        private void addLabel(String text) {
            this.addEntry(new LabelEntry(Component.literal(text)), 28);
        }

        private void addSlider(String label, java.util.function.DoubleSupplier getter,
                               java.util.function.DoubleConsumer setter, Supplier<String> display) {
            this.addEntry(new SliderEntry(Component.literal(label), getter, setter, display), 28);
        }

        private void addToggle(String label, java.util.function.BooleanSupplier getter,
                               java.util.function.Consumer<Boolean> setter) {
            this.addEntry(new ToggleEntry(Component.literal(label), getter, setter), 24);
        }

        private void addCycle(String label, String[] values,
                              Supplier<String> getter, Consumer<String> setter,
                              Function<String, String> displayMapper) {
            this.addEntry(new CycleEntry(Component.literal(label), values, getter, setter, displayMapper), 28);
        }

        // ===== 条目类型 =====

        abstract class SettingEntry extends ObjectSelectionList.Entry<SettingEntry> {
        }

        // 区块标题
        private class SectionEntry extends SettingEntry {
            private final Component text;
            SectionEntry(Component text) { this.text = text; }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                graphics.centeredText(WorldMainSettingScreen.this.font, text,
                    getContentXMiddle(), getContentY() + 5, -1);
            }

            @Override
            public Component getNarration() { return text; }
        }

        // 静态文本
        private class LabelEntry extends SettingEntry {
            private final Component text;
            LabelEntry(Component text) { this.text = text; }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                graphics.text(WorldMainSettingScreen.this.font, text,
                    getContentX(), getContentY(), -6250336);
            }

            @Override
            public Component getNarration() { return text; }
        }

        // 滑块
        private class SliderEntry extends SettingEntry {
            private final AbstractSliderButton slider;
            SliderEntry(Component label, java.util.function.DoubleSupplier getter,
                        java.util.function.DoubleConsumer setter, Supplier<String> display) {
                slider = new AbstractSliderButton(0, 0, CONTENT_WIDTH - 20, 20, label, getter.getAsDouble()) {
                    @Override
                    protected void updateMessage() {
                        this.setMessage(Component.literal("§f" + label.getString() + "§r: " + display.get()));
                    }
                    @Override
                    protected void applyValue() {
                        setter.accept(this.value);
                        this.updateMessage();
                    }
                };
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                slider.setX(getContentX() + 10);
                slider.setY(getContentY());
                slider.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public Component getNarration() { return slider.getMessage(); }
        }

        // 开关
        private class ToggleEntry extends SettingEntry {
            private final CycleButton<Boolean> toggle;
            private final Component label;

            ToggleEntry(Component label, java.util.function.BooleanSupplier getter,
                        java.util.function.Consumer<Boolean> setter) {
                this.label = label;
                toggle = CycleButton.onOffBuilder(getter.getAsBoolean())
                    .displayOnlyValue()
                    .create(0, 0, 44, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int textRight = getContentX() + WorldMainSettingScreen.this.font.width(label);
                int toggleX = getContentRight() - 45;
                if (textRight + 10 < toggleX) {
                    // 文本不重叠，显示文本
                    graphics.text(WorldMainSettingScreen.this.font, label, getContentX(), getContentY() + 6, -1);
                }
                toggle.setX(toggleX);
                toggle.setY(getContentY() + 2);
                toggle.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public Component getNarration() { return label; }
        }

        // 循环选择
        private class CycleEntry extends SettingEntry {
            private final CycleButton<String> cycle;
            CycleEntry(Component label, String[] values, Supplier<String> getter,
                       Consumer<String> setter, Function<String, String> displayMapper) {
                cycle = CycleButton.builder(v -> Component.literal(displayMapper.apply(v)), getter.get())
                    .withValues(values)
                    .create(0, 0, CONTENT_WIDTH - 20, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                cycle.setX(getContentX() + 10);
                cycle.setY(getContentY());
                cycle.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public Component getNarration() { return cycle.getMessage(); }
        }
    }
}