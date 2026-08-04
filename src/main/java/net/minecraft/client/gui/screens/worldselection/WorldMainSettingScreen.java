package net.minecraft.client.gui.screens.worldselection;

import java.util.Locale;
import java.util.function.DoubleSupplier;
import java.util.function.DoubleUnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
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
    private static final int MARGIN_TOP = 16;
    private static final int MARGIN_BOTTOM = 16;

    private final Screen parent;
    private final WorldCreationContext settings;

    // ==================== 配置数据 ====================
    private final FarLandsConfigData configData;

    // ==================== 子组件列表 ====================
    private final java.util.ArrayList<GuiEventListener> allChildren = new java.util.ArrayList<>();

    // ==================== 独立组件引用 ====================
    private StringWidget titleWidget;
    private StringWidget hintWidget;
    private WorldMainSettingScreen.ConfigScrollPanel scrollPanel;
    private final LinearLayout scrollContent = LinearLayout.vertical().spacing(6);
    private Button doneButton;
    private Button cancelButton;

    // ==================== 构造函数 ====================
    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        super(Component.literal("边境旅者 配置"));
        this.parent = parent;
        this.settings = settings;
        this.configData = new FarLandsConfigData();
    }

    // ==================== 添加子组件 ====================

    private <T extends GuiEventListener & Renderable & NarratableEntry> T addChild(T widget) {
        this.allChildren.add(widget);
        return this.addRenderableWidget(widget);
    }

    // ==================== 初始化 ====================
    @Override
    protected void init() {
        this.allChildren.clear();

        // --- 1. 标题（顶部居中） ---
        this.titleWidget = new StringWidget(
            this.title.copy().withStyle(ChatFormatting.BOLD),
            this.font
        );
        this.addChild(this.titleWidget);

        // --- 2. 提示文本（标题下方居中） ---
        this.hintWidget = new StringWidget(
            Component.literal("§7配置边境之地相关参数"),
            this.font
        );
        this.addChild(this.hintWidget);

        // --- 3. 构建滚动内容 ---
        this.buildScrollContent();

        // --- 4. 滚动面板（占据中间区域） ---
        this.scrollPanel = new WorldMainSettingScreen.ConfigScrollPanel(
            0, 0, CONTENT_WIDTH, 200, this.scrollContent
        );
        this.addChild(this.scrollPanel);

        // --- 5. 按钮 ---
        this.doneButton = Button.builder(
            Component.literal("完成"),
            button -> this.onDone()
        ).width(100).build();
        this.cancelButton = Button.builder(
            Component.literal("取消"),
            button -> this.onClose()
        ).width(100).build();
        this.addChild(this.doneButton);
        this.addChild(this.cancelButton);

        // --- 6. 布局定位 ---
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

    // ==================== 布局定位 ====================

    @Override
    public void repositionElements() {
        int centerX = this.width / 2;
        int contentLeft = centerX - CONTENT_WIDTH / 2;

        // 1. 标题
        int titleY = MARGIN_TOP;
        this.titleWidget.setPosition(centerX - this.titleWidget.getWidth() / 2, titleY);

        // 2. 提示
        int hintY = titleY + 14;
        this.hintWidget.setPosition(centerX - this.hintWidget.getWidth() / 2, hintY);

        // 3. 滚动面板
        int scrollTop = hintY + 14 + 5;
        int scrollBottom = this.height - MARGIN_BOTTOM - 28; // 28 = 按钮高度 + 边距
        int scrollHeight = Math.max(150, scrollBottom - scrollTop);

        this.scrollPanel.setPosition(contentLeft, scrollTop);
        this.scrollPanel.setSize(CONTENT_WIDTH, scrollHeight);

        // 4. 按钮
        int buttonsY = this.height - MARGIN_BOTTOM - 20;
        this.doneButton.setPosition(centerX - 100 - 4, buttonsY);
        this.cancelButton.setPosition(centerX + 4, buttonsY);
    }

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 黑色背景
        this.extractBackground(graphics, mouseX, mouseY, a);

        // 渲染所有子组件
        for (GuiEventListener child : this.allChildren) {
            if (child instanceof Renderable renderable) {
                renderable.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }
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
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);
    }

    // ==================== 滚动面板（核心修复！） ====================

    /**
     * 自定义滚动面板——继承 AbstractScrollArea，包装内部 LayoutElement 集合
     */
    @OnlyIn(Dist.CLIENT)
    private class ConfigScrollPanel extends AbstractScrollArea {
        private final LinearLayout content;
        private final java.util.List<LayoutElement> contentChildren = new java.util.ArrayList<>();

        public ConfigScrollPanel(int x, int y, int width, int height, LinearLayout content) {
            super(x, y, width, height, Component.empty(), AbstractScrollArea.defaultSettings(10));
            this.content = content;
            this.content.arrangeElements();
            this.collectChildren();
        }

        private void collectChildren() {
            this.contentChildren.clear();
            this.content.visitChildren(child -> this.contentChildren.add(child));
        }

        public void setSize(int width, int height) {
            this.setWidth(width);
            this.setHeight(height);
            this.refreshScrollAmount();
        }

        @Override
        protected int contentHeight() {
            return this.content.getHeight() + 10;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            // 先渲染滚动条背景
            this.extractScrollbar(graphics, mouseX, mouseY);

            // 启用裁剪：只显示面板区域内的内容
            graphics.enableScissor(this.getX(), this.getY(), this.getRight(), this.getBottom());

            // 平移坐标系到滚动偏移量
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0F, (float)(-this.scrollAmount()));

            // 渲染内容子组件
            for (LayoutElement child : this.contentChildren) {
                if (child instanceof Renderable renderable) {
                    // 只有在可见区域内的才渲染
                    int childY = child.getY();
                    int childBottom = childY + child.getHeight();
                    if (childBottom - this.scrollAmount() >= this.getY() 
                        && childY - this.scrollAmount() <= this.getBottom()) {
                        renderable.extractRenderState(graphics, mouseX, mouseY, a);
                    }
                }
            }

            graphics.pose().popMatrix();
            graphics.disableScissor();

            // 再渲染滚动条（覆盖裁剪层之上）
            this.extractScrollbar(graphics, mouseX, mouseY);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
            if (this.isMouseOver(mx, my)) {
                this.setScrollAmount(this.scrollAmount() - scrollY * 10.0);
                return true;
            }
            return false;
        }

        @Override
        public boolean isMouseOver(double mx, double my) {
            return mx >= this.getX() && mx < this.getRight() 
                && my >= this.getY() && my < this.getBottom();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
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