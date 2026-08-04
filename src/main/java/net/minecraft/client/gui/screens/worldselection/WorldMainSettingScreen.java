package net.minecraft.client.gui.screens.worldselection;

import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 🔥 MCRe NoiseFarlands —— 世界主要设置界面
 *
 * <p>将 FarLandsTraveler 模组的所有配置项移植到世界创建流程中，
 * 允许玩家在创建世界时直接配置边境之地相关参数。</p>
 *
 * @author MCRe Ultimate Scaler
 * @since 2026-08-04
 */
@OnlyIn(Dist.CLIENT)
public class WorldMainSettingScreen extends Screen {

    // ==================== 界面布局常量 ====================
    private static final int CONTENT_WIDTH = 320;
    private static final int SLIDER_MIN = 10000000;
    private static final int SLIDER_MAX = 33554432;
    private static final int STRIPE_MAX = 33554432;
    private static final int PADDING = 5;
    private static final int SEPARATOR_HEIGHT = 2;
    private static final int TITLE_Y = 20;
    private static final int HINT_Y = 32;
    private static final int HEADER_SEPARATOR_Y = 42;
    private static final int FOOTER_SEPARATOR_HEIGHT_FROM_BOTTOM = 36;
    private static final int BUTTONS_Y_OFFSET_FROM_BOTTOM = 10;

    private final Screen parent;
    private final WorldCreationContext settings;

    // ==================== 配置数据 ====================
    private final FarLandsConfigData configData;

    // ==================== 布局组件 ====================
    private final LinearLayout scrollContent = LinearLayout.vertical().spacing(6);
    private ScrollableLayout scrollArea;
    private Button doneButton;
    private Button cancelButton;

    // ==================== 构造函数 ====================
    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        super(Component.literal("边境旅者 配置"));
        this.parent = parent;
        this.settings = settings;
        this.configData = new FarLandsConfigData();
    }

    // ==================== 初始化 ====================
    @Override
    protected void init() {
        this.buildScrollContent();
        this.scrollArea = new ScrollableLayout(this.minecraft, this.scrollContent, 200);
        this.scrollArea.setMinWidth(CONTENT_WIDTH);

        // 通过 getContainer() 获取内部容器并注册（Container 已改为 public）
        this.addRenderableWidget(this.scrollArea.getContainer());

        // 完成按钮
        this.doneButton = Button.builder(
            Component.literal("完成"),
            button -> this.onDone()
        ).build();
        this.addRenderableWidget(this.doneButton);

        // 取消按钮
        this.cancelButton = Button.builder(
            Component.literal("取消"),
            button -> this.onClose()
        ).build();
        this.addRenderableWidget(this.cancelButton);

        this.repositionElements();
    }

    // ==================== 构建滚动内容 ====================

    private void buildScrollContent() {
        this.scrollContent.defaultCellSetting().alignHorizontallyCenter();

        // ========== 第一组：边境之地核心设置 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§6§l边境之地设置")
        ));

        // 1. 边境之地距离（滑块）
        this.scrollContent.addChild(new FarLandsDistanceSlider(CONTENT_WIDTH - 20), s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7边境之地生成位置与世界原点的距离\n§7该设置用于各类机制的判定，不影响地形生成"),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20));

        // 2. 条纹之地距离（滑块）
        this.scrollContent.addChild(new StripeLandsSlider(CONTENT_WIDTH - 20), s -> s.paddingHorizontal(10));

        // 3. 启用天空网格（开关）
        SwitchGrid.Builder skyGridBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(4)
            .withInfoUnderneath(2, true);
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

        // ========== 第二组：世界边界设置 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§6§l世界边界设置")
        ));

        SwitchGrid.Builder borderBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20).withRowSpacing(4);
        borderBuilder.addSwitch(
            Component.literal("移除世界边界"),
            () -> this.configData.removeWorldBorder,
            val -> this.configData.removeWorldBorder = val
        ).withInfo(Component.literal("移除世界边界的蓝色力场"));
        borderBuilder.addSwitch(
            Component.literal("移除世界界限"),
            () -> this.configData.removeWorldBoundary,
            val -> this.configData.removeWorldBoundary = val
        ).withInfo(Component.literal("移除x,z坐标30000000处的空气墙\n§c[警告]离开世界界限太远时将造成崩溃！！！"));
        borderBuilder.addSwitch(
            Component.literal("移除坐标限制"),
            () -> this.configData.removeCoordinateLimits,
            val -> this.configData.removeCoordinateLimits = val
        ).withInfo(Component.literal("移除一切与坐标有关的限制，例如tp指令的坐标限制"));
        this.scrollContent.addChild(borderBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 第三组：精度系统 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§b§l精度系统")
        ));

        CycleButton<String> precisionModeButton = CycleButton.builder(
                mode -> switch (mode) {
                    case "32bit" -> Component.literal("32 位");
                    case "64bit" -> Component.literal("64 位");
                    case "256bit" -> Component.literal("256 位");
                    default -> Component.literal(mode);
                },
                "32bit"
            )
            .withValues("32bit", "64bit", "256bit")
            .create(0, 0, CONTENT_WIDTH - 20, 20,
                Component.literal("精度模式"),
                (button, val) -> this.configData.precisionMode = val
            );
        this.scrollContent.addChild(precisionModeButton, s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7选择坐标精度等级：32位（经典边境之地）、64位（中期改造）或256位（终极形态）"),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20).paddingBottom(4));

        // ========== 第四组：假区块设置 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§d§l假区块设置")
        ));

        SwitchGrid.Builder fcBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(3)
            .withInfoUnderneath(2, false);
        fcBuilder.addSwitch(
            Component.literal("禁用假区块中的方块碰撞"),
            () -> this.configData.fcDisableBlockCollision,
            val -> this.configData.fcDisableBlockCollision = val
        ).withInfo(Component.literal("禁用假区块中的方块碰撞\n除非实体免疫假区块"));
        fcBuilder.addSwitch(
            Component.literal("禁用假区块中的方块效果"),
            () -> this.configData.fcDisableBlockEffect,
            val -> this.configData.fcDisableBlockEffect = val
        ).withInfo(Component.literal("禁用假区块中的方块对实体的效果（如火焰、仙人掌的伤害）\n除非实体免疫假区块"));
        fcBuilder.addSwitch(
            Component.literal("禁用假区块中的流体碰撞"),
            () -> this.configData.fcDisableFluidCollision,
            val -> this.configData.fcDisableFluidCollision = val
        ).withInfo(Component.literal("禁用假区块中的流体碰撞\n除非实体免疫假区块"));
        fcBuilder.addSwitch(
            Component.literal("使流体无法流过假区块边界"),
            () -> this.configData.fcDisableFluidFlowing,
            val -> this.configData.fcDisableFluidFlowing = val
        ).withInfo(Component.literal("使流体无法流过假区块边界"));
        fcBuilder.addSwitch(
            Component.literal("使实体无法与假区块中的方块交互"),
            () -> this.configData.fcDisableBlockInteraction,
            val -> this.configData.fcDisableBlockInteraction = val
        ).withInfo(Component.literal("使实体（玩家、末影人等）无法与假区块中的方块交互\n除非他们有合适的工具"));
        fcBuilder.addSwitch(
            Component.literal("使假区块中的方块不受爆炸影响"),
            () -> this.configData.fcDisableExplosionEffect,
            val -> this.configData.fcDisableExplosionEffect = val
        ).withInfo(Component.literal("使假区块中的方块免疫爆炸，且无法阻挡爆炸射线"));
        fcBuilder.addSwitch(
            Component.literal("使假区块中的梯子无法攀爬"),
            () -> this.configData.fcDisableLadderBehavior,
            val -> this.configData.fcDisableLadderBehavior = val
        ).withInfo(Component.literal("使假区块中的梯子类方块无法攀爬"));
        fcBuilder.addSwitch(
            Component.literal("使假区块中的活塞无法正常工作（实验性）"),
            () -> this.configData.fcDisablePistonBehavior,
            val -> this.configData.fcDisablePistonBehavior = val
        ).withInfo(Component.literal("使假区块中的活塞无法正常工作\n§e该功能目前无法正常工作"));
        this.scrollContent.addChild(fcBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 第五组：结构生成 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§a§l结构生成设置")
        ));

        SwitchGrid.Builder structBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20).withRowSpacing(4);
        structBuilder.addSwitch(
            Component.literal("生成岩石之令实验室"),
            () -> this.configData.generateOotsLaboratory,
            val -> this.configData.generateOotsLaboratory = val
        ).withInfo(Component.literal("启用岩石之令实验室的生成"));
        this.scrollContent.addChild(structBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 底部总结 ==========
        this.scrollContent.addChild(new MultiLineTextWidget(
            Component.literal(
                "§7当前配置：边境之地距离 §e" + String.format("%,d", this.configData.farLandsDistance)
                + " §r§7| 精度模式 §b" + this.configData.precisionMode
            ),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 20).setCentered(true), s -> s.padding(10));
    }

    // ==================== 布局调整 ====================

    @Override
    public void repositionElements() {
        // 滚动面板：在顶部和底部分隔线之间
        int scrollAreaTop = HEADER_SEPARATOR_Y + SEPARATOR_HEIGHT + PADDING;
        int buttonsY = this.height - FOOTER_SEPARATOR_HEIGHT_FROM_BOTTOM;
        int scrollAreaBottom = buttonsY - SEPARATOR_HEIGHT - PADDING;
        int scrollAreaHeight = Math.max(150, scrollAreaBottom - scrollAreaTop);

        this.scrollArea.getContainer().setMaxHeight(scrollAreaHeight);
        this.scrollArea.getContainer().setPosition(
            (this.width - CONTENT_WIDTH) / 2,
            scrollAreaTop
        );

        // 确认/取消按钮：底部分隔线下方居中
        int buttonY = buttonsY + SEPARATOR_HEIGHT + BUTTONS_Y_OFFSET_FROM_BOTTOM;
        int buttonSpacing = 8;
        int totalButtonsWidth = 150 + buttonSpacing + 150;
        int buttonsStartX = (this.width - totalButtonsWidth) / 2;

        this.doneButton.setPosition(buttonsStartX, buttonY);
        this.doneButton.setWidth(150);
        this.cancelButton.setPosition(buttonsStartX + 150 + buttonSpacing, buttonY);
        this.cancelButton.setWidth(150);
    }

    // ==================== 渲染 ====================

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 黑色背景
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // 标题：居中，顶部分隔线之上
        int titleWidth = this.font.width(this.title);
        graphics.text(this.font, this.title.copy().withStyle(ChatFormatting.BOLD).getVisualOrderText(),
            (this.width - titleWidth) / 2, TITLE_Y, -1);

        // 提示文本：标题下方，居中
        Component hint = Component.literal("§7配置边境之地相关参数");
        int hintWidth = this.font.width(hint);
        graphics.text(this.font, hint.getVisualOrderText(),
            (this.width - hintWidth) / 2, HINT_Y, -6250336);

        // 顶部分割线
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Screen.HEADER_SEPARATOR,
            0,
            HEADER_SEPARATOR_Y,
            0.0F,
            0.0F,
            this.width,
            SEPARATOR_HEIGHT,
            32,
            SEPARATOR_HEIGHT
        );

        // 渲染所有已注册的 renderable（滚动面板容器 + 按钮）
        super.extractRenderState(graphics, mouseX, mouseY, a);

        // 底部分割线：按钮上方
        int buttonsY = this.height - FOOTER_SEPARATOR_HEIGHT_FROM_BOTTOM;
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            Screen.FOOTER_SEPARATOR,
            0,
            buttonsY,
            0.0F,
            0.0F,
            this.width,
            SEPARATOR_HEIGHT,
            32,
            SEPARATOR_HEIGHT
        );
    }

    // ==================== 辅助方法 ====================

    private MultiLineTextWidget createSectionHeader(final Component text) {
        MultiLineTextWidget widget = new MultiLineTextWidget(text, this.font);
        widget.setMaxWidth(CONTENT_WIDTH - 20);
        widget.setCentered(true);
        return widget;
    }

    // ==================== 回调 ====================

    private void onDone() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 黑色背景已在前面的方法中处理
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
            this.setMessage(Component.literal(
                "边境之地距离: §e" + String.format("%,d", configData.farLandsDistance)));
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
                  configData.stripeLandsDistance == -1
                      ? 0.0
                      : (double) configData.stripeLandsDistance / STRIPE_MAX);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            String text;
            if (configData.stripeLandsDistance == -1) {
                text = "条纹之地距离: §c禁用";
            } else {
                text = "条纹之地距离: §e" + String.format("%,d", configData.stripeLandsDistance);
            }
            this.setMessage(Component.literal(text));
        }

        @Override
        protected void applyValue() {
            if (this.value < 0.01) {
                configData.stripeLandsDistance = -1;
            } else {
                configData.stripeLandsDistance = (int)(this.value * STRIPE_MAX);
            }
            this.updateMessage();
        }
    }
}