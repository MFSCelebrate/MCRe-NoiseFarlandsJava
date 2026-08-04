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
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔥 MCRe NoiseFarlands —— 世界主要设置界面
 *
 * 使用原版 ObjectSelectionList 实现稳定滚动，带详细介绍文本
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
        this.list = new SettingList(this.minecraft, this.width, listBottom - listTop, listTop, 24);
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

            // 边境之地设置
            this.addSection("§6§l边境之地设置");

            this.addSliderWithDesc("边境之地距离",
                () -> (double)(configData.farLandsDistance - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN),
                val -> { configData.farLandsDistance = SLIDER_MIN + (int)(val * (SLIDER_MAX - SLIDER_MIN)); },
                () -> "§e" + String.format("%,d", configData.farLandsDistance),
                "边境之地生成位置与世界原点的距离，该设置用于各类机制的判定，不影响地形生成");

            this.addSliderWithDesc("条纹之地距离",
                () -> configData.stripeLandsDistance == -1 ? 0.0 : (double)configData.stripeLandsDistance / STRIPE_MAX,
                val -> { configData.stripeLandsDistance = val < 0.01 ? -1 : (int)(val * STRIPE_MAX); },
                () -> configData.stripeLandsDistance == -1 ? "§c禁用" : "§e" + String.format("%,d", configData.stripeLandsDistance),
                "条纹之地开始的位置与世界原点的距离，设为-1以禁用条纹之地");

            this.addToggleWithDesc("启用天空网格", () -> configData.enableSkyGrid, val -> configData.enableSkyGrid = val,
                "使边缘之地生成天空网格而不是天空之桥");
            this.addToggleWithDesc("强制生成天空网格", () -> configData.forceSkyGrid, val -> configData.forceSkyGrid = val,
                "即使插值前的密度值被限制，也强制生成天空网格");

            // 世界边界设置
            this.addSection("§6§l世界边界设置");
            this.addToggleWithDesc("移除世界边界", () -> configData.removeWorldBorder, val -> configData.removeWorldBorder = val,
                "移除世界边界的蓝色力场");
            this.addToggleWithDesc("移除世界界限", () -> configData.removeWorldBoundary, val -> configData.removeWorldBoundary = val,
                "移除x,z坐标30000000处的空气墙，§c[警告]离开世界界限太远时将造成崩溃！！！");
            this.addToggleWithDesc("移除坐标限制", () -> configData.removeCoordinateLimits, val -> configData.removeCoordinateLimits = val,
                "移除一切与坐标有关的限制，例如tp指令的坐标限制");

            // 精度系统
            this.addSection("§b§l精度系统");
            this.addCycleWithDesc("精度模式", new String[]{"32bit", "64bit", "256bit"},
                () -> configData.precisionMode, val -> configData.precisionMode = val,
                mode -> switch (mode) {
                    case "32bit" -> "32 位";
                    case "64bit" -> "64 位";
                    case "256bit" -> "256 位";
                    default -> mode;
                }, "选择坐标精度等级：32位（经典边境之地）、64位（中期改造）或256位（终极形态）");

            // 假区块设置
            this.addSection("§d§l假区块设置");
            this.addToggleWithDesc("禁用方块碰撞", () -> configData.fcDisableBlockCollision, val -> configData.fcDisableBlockCollision = val,
                "禁用假区块中的方块碰撞，除非实体免疫假区块");
            this.addToggleWithDesc("禁用方块效果", () -> configData.fcDisableBlockEffect, val -> configData.fcDisableBlockEffect = val,
                "禁用假区块中的方块对实体的效果（如火焰、仙人掌的伤害），除非实体免疫假区块");
            this.addToggleWithDesc("禁用流体碰撞", () -> configData.fcDisableFluidCollision, val -> configData.fcDisableFluidCollision = val,
                "禁用假区块中的流体碰撞，除非实体免疫假区块");
            this.addToggleWithDesc("禁止流体流过边界", () -> configData.fcDisableFluidFlowing, val -> configData.fcDisableFluidFlowing = val,
                "使流体无法流过假区块边界");
            this.addToggleWithDesc("禁止方块交互", () -> configData.fcDisableBlockInteraction, val -> configData.fcDisableBlockInteraction = val,
                "使实体无法与假区块中的方块交互，除非有合适的工具");
            this.addToggleWithDesc("禁止爆炸影响", () -> configData.fcDisableExplosionEffect, val -> configData.fcDisableExplosionEffect = val,
                "使假区块中的方块免疫爆炸，且无法阻挡爆炸射线");
            this.addToggleWithDesc("禁止梯子攀爬", () -> configData.fcDisableLadderBehavior, val -> configData.fcDisableLadderBehavior = val,
                "使假区块中的梯子类方块无法攀爬");
            this.addToggleWithDesc("禁止活塞工作（实验性）", () -> configData.fcDisablePistonBehavior, val -> configData.fcDisablePistonBehavior = val,
                "使假区块中的活塞无法正常工作");

            // 结构生成
            this.addSection("§a§l结构生成设置");
            this.addToggleWithDesc("生成岩石之令实验室", () -> configData.generateOotsLaboratory, val -> configData.generateOotsLaboratory = val,
                "启用岩石之令实验室的生成");
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
            this.addEntry(new SectionEntry(Component.literal(text)), 20);
        }

        private void addSliderWithDesc(String label, java.util.function.DoubleSupplier getter,
                                        java.util.function.DoubleConsumer setter,
                                        Supplier<String> displayText, String desc) {
            this.addEntry(new SliderWithDescEntry(
                Component.literal(label), Component.literal("§7" + desc),
                getter, setter, displayText
            ), 44);
        }

        private void addToggleWithDesc(String label, java.util.function.BooleanSupplier getter,
                                        java.util.function.Consumer<Boolean> setter, String desc) {
            this.addEntry(new ToggleWithDescEntry(
                Component.literal(label), Component.literal("§7" + desc),
                getter, setter
            ), 40);
        }

        private void addCycleWithDesc(String label, String[] values,
                                       Supplier<String> getter, Consumer<String> setter,
                                       Function<String, String> displayMapper, String desc) {
            this.addEntry(new CycleWithDescEntry(
                Component.literal(label), Component.literal("§7" + desc),
                values, getter, setter, displayMapper
            ), 44);
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

        // 滑块 + 介绍文本
        private class SliderWithDescEntry extends SettingEntry {
            private final Component label;
            private final Component desc;
            private final AbstractSliderButton slider;

            SliderWithDescEntry(Component label, Component desc,
                                java.util.function.DoubleSupplier getter,
                                java.util.function.DoubleConsumer setter,
                                Supplier<String> displayText) {
                this.label = label;
                this.desc = desc;
                this.slider = new AbstractSliderButton(0, 0, CONTENT_WIDTH - 20, 20, label, getter.getAsDouble()) {
                    @Override protected void updateMessage() {
                        this.setMessage(Component.literal("§f" + label.getString() + ": " + displayText.get()));
                    }
                    @Override protected void applyValue() {
                        setter.accept(this.value);
                        this.updateMessage();
                    }
                };
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                // 介绍文本在上方
                List<FormattedCharSequence> descLines = WorldMainSettingScreen.this.font.split(desc, CONTENT_WIDTH - 20);
                int textY = getContentY();
                for (FormattedCharSequence line : descLines) {
                    graphics.text(WorldMainSettingScreen.this.font, line, getContentX() + 10, textY, -6250336);
                    textY += 9;
                }
                // 滑块在下方
                slider.setX(getContentX() + 10);
                slider.setY(textY);
                slider.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public Component getNarration() { return slider.getMessage(); }
        }

        // 开关 + 介绍文本
        private class ToggleWithDescEntry extends SettingEntry {
            private final Component label;
            private final Component desc;
            private final CycleButton<Boolean> toggle;

            ToggleWithDescEntry(Component label, Component desc,
                                java.util.function.BooleanSupplier getter,
                                java.util.function.Consumer<Boolean> setter) {
                this.label = label;
                this.desc = desc;
                this.toggle = CycleButton.onOffBuilder(getter.getAsBoolean())
                    .displayOnlyValue()
                    .create(0, 0, 44, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                // 开关在右侧
                toggle.setX(getContentRight() - 45);
                toggle.setY(getContentY());
                toggle.extractRenderState(graphics, mouseX, mouseY, a);

                // 标签在左侧（紧挨着开关左边）
                int labelMaxWidth = getContentRight() - 45 - getContentX() - 10;
                if (labelMaxWidth > 20) {
                    graphics.text(WorldMainSettingScreen.this.font, label, getContentX() + 10, getContentY() + 5, -1);
                }

                // 介绍文本在下一行
                List<FormattedCharSequence> descLines = WorldMainSettingScreen.this.font.split(desc, CONTENT_WIDTH - 20);
                int descY = getContentY() + 22;
                for (FormattedCharSequence line : descLines) {
                    graphics.text(WorldMainSettingScreen.this.font, line, getContentX() + 10, descY, -6250336);
                    descY += 9;
                }
            }

            @Override
            public Component getNarration() { return label; }
        }

        // 循环选择 + 介绍文本
        private class CycleWithDescEntry extends SettingEntry {
            private final Component desc;
            private final CycleButton<String> cycle;

            CycleWithDescEntry(Component label, Component desc,
                               String[] values, Supplier<String> getter,
                               Consumer<String> setter,
                               Function<String, String> displayMapper) {
                this.desc = desc;
                this.cycle = CycleButton.builder(v -> Component.literal(displayMapper.apply(v)), getter.get())
                    .withValues(values)
                    .create(0, 0, 200, 20, label, (b, val) -> setter.accept(val));
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
                // 介绍文本在上方
                List<FormattedCharSequence> descLines = WorldMainSettingScreen.this.font.split(desc, CONTENT_WIDTH - 20);
                int textY = getContentY();
                for (FormattedCharSequence line : descLines) {
                    graphics.text(WorldMainSettingScreen.this.font, line, getContentX() + 10, textY, -6250336);
                    textY += 9;
                }
                // 按钮在下方
                cycle.setX(getContentX() + 10);
                cycle.setY(textY);
                cycle.extractRenderState(graphics, mouseX, mouseY, a);
            }

            @Override
            public Component getNarration() { return cycle.getMessage(); }
        }
    }
}