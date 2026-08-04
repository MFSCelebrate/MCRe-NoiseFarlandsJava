package net.minecraft.client.gui.screens.worldselection;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔥 MCRe NoiseFarlands —— 世界主要设置界面
 */
@OnlyIn(Dist.CLIENT)
public class WorldMainSettingScreen extends Screen {

    private static final int CONTENT_WIDTH = 320;
    private static final int SLIDER_MIN = 10000000;
    private static final int SLIDER_MAX = 33554432;
    private static final int STRIPE_MAX = 33554432;

    private final Screen parent;
    private final WorldCreationContext settings;
    private final FarLandsConfigData configData = new FarLandsConfigData();

    // 手动管理的子组件列表（因为 Screen.renderables 是 private 的）
    private final List<GuiEventListener> allChildren = new ArrayList<>();

    // 布局变量
    private ScrollableLayout scrollArea;
    private final LinearLayout scrollContent = LinearLayout.vertical().spacing(6);
    private Button doneButton;
    private Button cancelButton;
    private StringWidget titleWidget;
    private StringWidget hintWidget;

    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        super(Component.literal("边境旅者 配置"));
        this.parent = parent;
        this.settings = settings;
    }

    private <T extends GuiEventListener & Renderable & NarratableEntry> T addChild(T widget) {
        this.allChildren.add(widget);
        return this.addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        this.allChildren.clear();

        // --- 标题（固定顶部居中） ---
        int centerX = this.width / 2;
        this.titleWidget = new StringWidget(
            this.title.copy().withStyle(ChatFormatting.BOLD), this.font
        );
        this.titleWidget.setPosition(centerX - this.titleWidget.getWidth() / 2, 8);
        this.addChild(this.titleWidget);

        // --- 提示文本 ---
        this.hintWidget = new StringWidget(
            Component.literal("§7配置边境之地相关参数"), this.font
        );
        this.hintWidget.setPosition(centerX - this.hintWidget.getWidth() / 2, 8 + 9 + 4);
        this.addChild(this.hintWidget);

        // --- 滚动内容区域 ---
        this.buildScrollContent();
        int scrollTop = 8 + 9 + 4 + 9 + 8; // 标题 + 提示 + 间距
        int scrollHeight = Math.max(150, this.height - scrollTop - 20 - 24); // 留出按钮空间
        this.scrollArea = new ScrollableLayout(this.minecraft, this.scrollContent, scrollHeight);
        this.scrollArea.setMinWidth(CONTENT_WIDTH);
        this.scrollArea.setPosition((this.width - CONTENT_WIDTH) / 2, scrollTop);
        this.scrollArea.visitWidgets(w -> this.addChild(w));

        // --- 底部按钮 ---
        this.doneButton = Button.builder(Component.literal("完成"), b -> this.onDone())
            .pos(centerX - 110, this.height - 28).width(100).build();
        this.cancelButton = Button.builder(Component.literal("取消"), b -> this.onClose())
            .pos(centerX + 10, this.height - 28).width(100).build();
        this.addChild(this.doneButton);
        this.addChild(this.cancelButton);
    }

    private void buildScrollContent() {
        this.scrollContent.defaultCellSetting().alignHorizontallyCenter();

        // ===== 第一组：边境之地核心设置 =====
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§6§l边境之地设置")
        ));

        this.scrollContent.addChild(new FarLandsDistanceSlider(CONTENT_WIDTH - 20), s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7边境之地生成位置与世界原点的距离\n§7该设置用于各类机制的判定，不影响地形生成"),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20));

        this.scrollContent.addChild(new StripeLandsSlider(CONTENT_WIDTH - 20), s -> s.paddingHorizontal(10));

        // 天空网格开关
        SwitchGrid.Builder skyGridBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(4).withInfoUnderneath(2, true);
        skyGridBuilder.addSwitch(
            Component.literal("启用天空网格"),
            () -> this.configData.enableSkyGrid,
            val -> this.configData.enableSkyGrid = val
        ).withInfo(Component.literal("使边缘之地生成天空网格而不是天空之桥"));
        skyGridBuilder.addSwitch(
            Component.literal("强制生成天空网格"),
            () -> this.configData.forceSkyGrid,
            val -> this.configData.forceSkyGrid = val
        ).withInfo(Component.literal("即使插值前的密度值被限制，也强制生成天空网格"));
        this.scrollContent.addChild(skyGridBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ===== 第二组：世界边界设置 =====
        this.scrollContent.addChild(this.createSectionHeader(Component.literal("§6§l世界边界设置")));
        
        SwitchGrid.Builder borderBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20).withRowSpacing(4);
        borderBuilder.addSwitch(Component.literal("移除世界边界"), () -> this.configData.removeWorldBorder, val -> this.configData.removeWorldBorder = val)
            .withInfo(Component.literal("移除世界边界的蓝色力场"));
        borderBuilder.addSwitch(Component.literal("移除世界界限"), () -> this.configData.removeWorldBoundary, val -> this.configData.removeWorldBoundary = val)
            .withInfo(Component.literal("移除x,z坐标30000000处的空气墙\n§c[警告]离开世界界限太远时将造成崩溃！！！"));
        borderBuilder.addSwitch(Component.literal("移除坐标限制"), () -> this.configData.removeCoordinateLimits, val -> this.configData.removeCoordinateLimits = val)
            .withInfo(Component.literal("移除一切与坐标有关的限制，例如tp指令的坐标限制"));
        this.scrollContent.addChild(borderBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ===== 第三组：精度系统 =====
        this.scrollContent.addChild(this.createSectionHeader(Component.literal("§b§l精度系统")));

        CycleButton<String> precisionModeButton = CycleButton.builder(
                mode -> switch (mode) {
                    case "32bit" -> Component.literal("32 位");
                    case "64bit" -> Component.literal("64 位");
                    case "256bit" -> Component.literal("256 位");
                    default -> Component.literal(mode);
                }, "32bit"
            ).withValues("32bit", "64bit", "256bit")
            .create(0, 0, CONTENT_WIDTH - 20, 20,
                Component.literal("精度模式"),
                (button, val) -> this.configData.precisionMode = val
            );
        this.scrollContent.addChild(precisionModeButton, s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7选择坐标精度等级：32位（经典边境之地）、64位（中期改造）或256位（终极形态）"),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20).paddingBottom(4));

        // ===== 第四组：假区块设置 =====
        this.scrollContent.addChild(this.createSectionHeader(Component.literal("§d§l假区块设置")));

        SwitchGrid.Builder fcBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(3).withInfoUnderneath(2, false);
        fcBuilder.addSwitch(Component.literal("禁用假区块中的方块碰撞"), () -> this.configData.fcDisableBlockCollision, val -> this.configData.fcDisableBlockCollision = val)
            .withInfo(Component.literal("禁用假区块中的方块碰撞\n除非实体免疫假区块"));
        fcBuilder.addSwitch(Component.literal("禁用假区块中的方块效果"), () -> this.configData.fcDisableBlockEffect, val -> this.configData.fcDisableBlockEffect = val)
            .withInfo(Component.literal("禁用假区块中的方块对实体的效果（如火焰、仙人掌的伤害）\n除非实体免疫假区块"));
        fcBuilder.addSwitch(Component.literal("禁用假区块中的流体碰撞"), () -> this.configData.fcDisableFluidCollision, val -> this.configData.fcDisableFluidCollision = val)
            .withInfo(Component.literal("禁用假区块中的流体碰撞\n除非实体免疫假区块"));
        fcBuilder.addSwitch(Component.literal("使流体无法流过假区块边界"), () -> this.configData.fcDisableFluidFlowing, val -> this.configData.fcDisableFluidFlowing = val)
            .withInfo(Component.literal("使流体无法流过假区块边界"));
        fcBuilder.addSwitch(Component.literal("使实体无法与假区块中的方块交互"), () -> this.configData.fcDisableBlockInteraction, val -> this.configData.fcDisableBlockInteraction = val)
            .withInfo(Component.literal("使实体（玩家、末影人等）无法与假区块中的方块交互\n除非他们有合适的工具"));
        fcBuilder.addSwitch(Component.literal("使假区块中的方块不受爆炸影响"), () -> this.configData.fcDisableExplosionEffect, val -> this.configData.fcDisableExplosionEffect = val)
            .withInfo(Component.literal("使假区块中的方块免疫爆炸，且无法阻挡爆炸射线"));
        fcBuilder.addSwitch(Component.literal("使假区块中的梯子无法攀爬"), () -> this.configData.fcDisableLadderBehavior, val -> this.configData.fcDisableLadderBehavior = val)
            .withInfo(Component.literal("使假区块中的梯子类方块无法攀爬"));
        fcBuilder.addSwitch(Component.literal("使假区块中的活塞无法正常工作（实验性）"), () -> this.configData.fcDisablePistonBehavior, val -> this.configData.fcDisablePistonBehavior = val)
            .withInfo(Component.literal("使假区块中的活塞无法正常工作\n§e该功能目前无法正常工作"));
        this.scrollContent.addChild(fcBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ===== 第五组：结构生成 =====
        this.scrollContent.addChild(this.createSectionHeader(Component.literal("§a§l结构生成设置")));

        SwitchGrid.Builder structBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20).withRowSpacing(4);
        structBuilder.addSwitch(Component.literal("生成岩石之令实验室"), () -> this.configData.generateOotsLaboratory, val -> this.configData.generateOotsLaboratory = val)
            .withInfo(Component.literal("启用岩石之令实验室的生成"));
        this.scrollContent.addChild(structBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // 底部总结
        this.scrollContent.addChild(new MultiLineTextWidget(
            Component.literal("§7当前配置：边境之地距离 §e" + String.format("%,d", this.configData.farLandsDistance)
                + " §r§7| 精度模式 §b" + this.configData.precisionMode),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 20).setCentered(true), s -> s.padding(10));
    }

    @Override
    public void repositionElements() {
        // init 里已经用绝对坐标设置好了，不需要额外布局
        // 但如果窗口大小改变，可能需要重新计算
        if (this.scrollArea != null) {
            int centerX = this.width / 2;
            int scrollTop = 8 + 9 + 4 + 9 + 8;
            int scrollHeight = Math.max(150, this.height - scrollTop - 20 - 24);
            this.scrollArea.setMaxHeight(scrollHeight);
            this.scrollArea.setPosition((this.width - CONTENT_WIDTH) / 2, scrollTop);
        }
        if (this.doneButton != null && this.cancelButton != null) {
            int centerX = this.width / 2;
            this.doneButton.setPosition(centerX - 110, this.height - 28);
            this.cancelButton.setPosition(centerX + 10, this.height - 28);
        }
        if (this.titleWidget != null) {
            this.titleWidget.setPosition(this.width / 2 - this.titleWidget.getWidth() / 2, 8);
        }
        if (this.hintWidget != null) {
            this.hintWidget.setPosition(this.width / 2 - this.hintWidget.getWidth() / 2, 8 + 9 + 4);
        }
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 黑色背景
        this.extractBackground(graphics, mouseX, mouseY, a);

        // 手动渲染所有子组件
        for (GuiEventListener child : this.allChildren) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    private MultiLineTextWidget createSectionHeader(final Component text) {
        MultiLineTextWidget widget = new MultiLineTextWidget(text, this.font);
        widget.setMaxWidth(CONTENT_WIDTH - 20);
        widget.setCentered(true);
        return widget;
    }

    private void onDone() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
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

    // ==================== 边境之地距离滑块 ====================
    @OnlyIn(Dist.CLIENT)
    private class FarLandsDistanceSlider extends AbstractSliderButton {
        public FarLandsDistanceSlider(final int width) {
            super(0, 0, width, 20, Component.empty(),
                  (double)(configData.farLandsDistance - SLIDER_MIN) / (SLIDER_MAX - SLIDER_MIN));
            this.updateMessage();
        }
        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal("边境之地距离: §e" + String.format("%,d", configData.farLandsDistance)));
        }
        @Override
        protected void applyValue() {
            configData.farLandsDistance = SLIDER_MIN + (int)(this.value * (SLIDER_MAX - SLIDER_MIN));
            this.updateMessage();
        }
    }

    // ==================== 条纹之地距离滑块 ====================
    @OnlyIn(Dist.CLIENT)
    private class StripeLandsSlider extends AbstractSliderButton {
        public StripeLandsSlider(final int width) {
            super(0, 0, width, 20, Component.empty(),
                  configData.stripeLandsDistance == -1 ? 0.0 : (double) configData.stripeLandsDistance / STRIPE_MAX);
            this.updateMessage();
        }
        @Override
        protected void updateMessage() {
            String text = configData.stripeLandsDistance == -1
                ? "条纹之地距离: §c禁用"
                : "条纹之地距离: §e" + String.format("%,d", configData.stripeLandsDistance);
            this.setMessage(Component.literal(text));
        }
        @Override
        protected void applyValue() {
            configData.stripeLandsDistance = this.value < 0.01 ? -1 : (int)(this.value * STRIPE_MAX);
            this.updateMessage();
        }
    }
}