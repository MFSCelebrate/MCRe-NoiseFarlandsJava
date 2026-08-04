package net.minecraft.client.gui.screens.worldselection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔥 MCRe NoiseFarlands —— 世界主要设置界面
 * 
 * 使用原版 ObjectSelectionList 实现滚动，确保稳定可靠
 */
@OnlyIn(Dist.CLIENT)
public class WorldMainSettingScreen extends Screen {

    private static final int CONTENT_WIDTH = 310;
    private static final int SLIDER_MIN = 10000000;
    private static final int SLIDER_MAX = 33554432;
    private static final int STRIPE_MAX = 33554432;

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
        // 标题（居中）
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
        int listTop = 8 + 9 + 4 + 9 + 8;
        int listBottom = this.height - 40;
        int listHeight = listBottom - listTop;
        this.list = new SettingList(this.minecraft, this.width, listHeight, listTop, 0);
        this.addRenderableWidget(this.list);

        // 底部按钮
        this.addRenderableWidget(
            Button.builder(Component.literal("完成"), b -> this.onDone())
                .pos(this.width / 2 - 110, this.height - 28)
                .width(100)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.literal("取消"), b -> this.onClose())
                .pos(this.width / 2 + 10, this.height - 28)
                .width(100)
                .build()
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

    // ==================== 可滚动的设置列表 ====================
    private class SettingList extends ContainerObjectSelectionList<SettingList.SettingEntry> {

        public SettingList(final Minecraft minecraft, final int width, final int height, final int y, final int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;
            this.setRenderBackground(false);
            this.setRenderTopAndBottom(false);

            // 添加所有设置项
            this.addSection("§6§l边境之地设置");

            this.addSlider("边境之地距离", () -> (double)(configData.farLandsDistance - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN),
                val -> configData.farLandsDistance = SLIDER_MIN + (int)(val * (SLIDER_MAX - SLIDER_MIN)),
                () -> "边境之地距离: §e" + String.format("%,d", configData.farLandsDistance));
            this.addLabel("§7边境之地生成位置与世界原点的距离，该设置用于各类机制的判定，不影响地形生成");

            this.addSlider("条纹之地距离", 
                () -> configData.stripeLandsDistance == -1 ? 0.0 : (double)configData.stripeLandsDistance / STRIPE_MAX,
                val -> configData.stripeLandsDistance = val < 0.01 ? -1 : (int)(val * STRIPE_MAX),
                () -> configData.stripeLandsDistance == -1 ? "条纹之地距离: §c禁用" : "条纹之地距离: §e" + String.format("%,d", configData.stripeLandsDistance));

            this.addToggle("启用天空网格", () -> configData.enableSkyGrid, val -> configData.enableSkyGrid = val,
                "使边缘之地生成天空网格而不是天空之桥");
            this.addToggle("强制生成天空网格", () -> configData.forceSkyGrid, val -> configData.forceSkyGrid = val,
                "即使插值前的密度值被限制，也强制生成天空网格");

            this.addSection("§6§l世界边界设置");
            this.addToggle("移除世界边界", () -> configData.removeWorldBorder, val -> configData.removeWorldBorder = val,
                "移除世界边界的蓝色力场");
            this.addToggle("移除世界界限", () -> configData.removeWorldBoundary, val -> configData.removeWorldBoundary = val,
                "移除x,z坐标30000000处的空气墙\n§c[警告]离开世界界限太远时将造成崩溃！！！");
            this.addToggle("移除坐标限制", () -> configData.removeCoordinateLimits, val -> configData.removeCoordinateLimits = val,
                "移除一切与坐标有关的限制，例如tp指令的坐标限制");

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
            this.addToggle("禁用假区块中的方块碰撞", () -> configData.fcDisableBlockCollision, val -> configData.fcDisableBlockCollision = val,
                "禁用假区块中的方块碰撞，除非实体免疫假区块");
            this.addToggle("禁用假区块中的方块效果", () -> configData.fcDisableBlockEffect, val -> configData.fcDisableBlockEffect = val,
                "禁用假区块中的方块对实体的效果（如火焰、仙人掌的伤害），除非实体免疫假区块");
            this.addToggle("禁用假区块中的流体碰撞", () -> configData.fcDisableFluidCollision, val -> configData.fcDisableFluidCollision = val,
                "禁用假区块中的流体碰撞，除非实体免疫假区块");
            this.addToggle("使流体无法流过假区块边界", () -> configData.fcDisableFluidFlowing, val -> configData.fcDisableFluidFlowing = val,
                "使流体无法流过假区块边界");
            this.addToggle("使实体无法与假区块中的方块交互", () -> configData.fcDisableBlockInteraction, val -> configData.fcDisableBlockInteraction = val,
                "使实体（玩家、末影人等）无法与假区块中的方块交互，除非他们有合适的工具");
            this.addToggle("使假区块中的方块不受爆炸影响", () -> configData.fcDisableExplosionEffect, val -> configData.fcDisableExplosionEffect = val,
                "使假区块中的方块免疫爆炸，且无法阻挡爆炸射线");
            this.addToggle("使假区块中的梯子无法攀爬", () -> configData.fcDisableLadderBehavior, val -> configData.fcDisableLadderBehavior = val,
                "使假区块中的梯子类方块无法攀爬");
            this.addToggle("使假区块中的活塞无法正常工作（实验性）", () -> configData.fcDisablePistonBehavior, val -> configData.fcDisablePistonBehavior = val,
                "使假区块中的活塞无法正常工作");

            this.addSection("§a§l结构生成设置");
            this.addToggle("生成岩石之令实验室", () -> configData.generateOotsLaboratory, val -> configData.generateOotsLaboratory = val,
                "启用岩石之令实验室的生成");
        }

        @Override
        public int getRowWidth() {
            return CONTENT_WIDTH;
        }

        // ===== 辅助方法 =====

        private void addSection(String text) {
            this.addEntry(new SectionEntry(Component.literal(text)));
        }

        private void addLabel(String text) {
            this.addEntry(new LabelEntry(Component.literal(text), this.minecraft.font));
        }

        private void addSlider(String label, java.util.function.DoubleSupplier getter,
                               java.util.function.DoubleConsumer setter, java.util.function.Supplier<String> display) {
            this.addEntry(new SliderEntry(Component.literal(label), getter, setter, display));
        }

        private void addToggle(String label, java.util.function.BooleanSupplier getter,
                               java.util.function.Consumer<Boolean> setter, String info) {
            this.addEntry(new ToggleEntry(Component.literal(label), getter, setter, Component.literal(info)));
        }

        private void addCycle(String label, String[] values,
                              java.util.function.Supplier<String> getter,
                              java.util.function.Consumer<String> setter,
                              java.util.function.Function<String, String> displayMapper) {
            this.addEntry(new CycleEntry(Component.literal(label), values, getter, setter, displayMapper));
        }

        // ===== 设置项条目（基类） =====

        abstract class SettingEntry extends ContainerObjectSelectionList.Entry<SettingEntry> {
        }

        // 区块标题
        private class SectionEntry extends SettingEntry {
            private final Component text;
            SectionEntry(Component text) { this.text = text; }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int x = getContentXMiddle();
                graphics.centeredText(WorldMainSettingScreen.this.font, text, x, getContentY() + 5, -1);
            }

            @Override
            public List<? extends GuiEventListener> children() { return List.of(); }

            @Override
            public List<? extends NarratableEntry> narratables() { return List.of(); }
        }

        // 静态文本
        private class LabelEntry extends SettingEntry {
            private final Component text;
            LabelEntry(Component text, net.minecraft.client.gui.Font font) { this.text = text; }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                int x = getContentX();
                graphics.text(WorldMainSettingScreen.this.font, text, x, getContentY(), -6250336);
            }

            @Override
            public List<? extends GuiEventListener> children() { return List.of(); }

            @Override
            public List<? extends NarratableEntry> narratables() { return List.of(); }
        }

        // 滑块
        private class SliderEntry extends SettingEntry {
            private final AbstractSliderButton slider;
            SliderEntry(Component label, java.util.function.DoubleSupplier getter,
                        java.util.function.DoubleConsumer setter, java.util.function.Supplier<String> display) {
                slider = new AbstractSliderButton(0, 0, 150, 20, Component.empty(), getter.getAsDouble()) {
                    @Override protected void updateMessage() { this.setMessage(Component.literal(display.get())); }
                    @Override protected void applyValue() { setter.accept(this.value); this.updateMessage(); }
                };
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                slider.setX(getContentX() + 100);
                slider.setY(getContentY());
                slider.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override public List<? extends GuiEventListener> children() { return List.of(slider); }
            @Override public List<? extends NarratableEntry> narratables() { return List.of(slider); }
        }

        // 开关
        private class ToggleEntry extends SettingEntry {
            private final CycleButton<Boolean> toggle;
            ToggleEntry(Component label, java.util.function.BooleanSupplier getter,
                        java.util.function.Consumer<Boolean> setter, Component info) {
                toggle = CycleButton.onOffBuilder(getter.getAsBoolean())
                    .displayOnlyValue()
                    .create(0, 0, 44, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                toggle.setX(getContentRight() - 45);
                toggle.setY(getContentY());
                toggle.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override public List<? extends GuiEventListener> children() { return List.of(toggle); }
            @Override public List<? extends NarratableEntry> narratables() { return List.of(toggle); }
        }

        // 循环选择
        private class CycleEntry extends SettingEntry {
            private final CycleButton<String> cycle;
            CycleEntry(Component label, String[] values, java.util.function.Supplier<String> getter,
                       java.util.function.Consumer<String> setter, java.util.function.Function<String, String> displayMapper) {
                cycle = CycleButton.builder(
                        v -> Component.literal(displayMapper.apply(v)), getter.get()
                    ).withValues(values)
                    .create(0, 0, 150, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                cycle.setX(getContentX() + 100);
                cycle.setY(getContentY());
                cycle.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override public List<? extends GuiEventListener> children() { return List.of(cycle); }
            @Override public List<? extends NarratableEntry> narratables() { return List.of(cycle); }
        }
    }
}