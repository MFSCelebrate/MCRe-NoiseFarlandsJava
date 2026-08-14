package net.minecraft.client.gui.screens.worldselection;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

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
    private static final int SCROLL_AREA_MIN_HEIGHT = 130;

    private final Screen parent;
    private final WorldCreationContext settings;

    // ==================== 配置数据 ====================
    private final FarLandsConfigData configData;

    // ==================== 布局组件 ====================
    private final LinearLayout scrollContent = LinearLayout.vertical().spacing(6);
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private @Nullable ScrollableLayout scrollArea;

    // ==================== 构造函数 ====================
    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        super(Component.literal("世界自定义设置 │ World custom settings"));
        this.parent = parent;
        this.settings = settings;
        this.configData = new FarLandsConfigData();
    }

    // ==================== 初始化 ====================
    @Override
    protected void init() {
        // 标题放在 header
        LinearLayout header = LinearLayout.vertical().spacing(4);
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title.copy().withStyle(ChatFormatting.BOLD), this.font));
        header.addChild(new StringWidget(
            Component.literal("§7进行对世界生成器的自定义 │ Customize the world generator"),
            this.font
        ));
        this.layout.addToHeader(header);

        // 主体内容：滚动面板
        this.buildScrollContent();
        this.scrollArea = new ScrollableLayout(this.minecraft, this.scrollContent, SCROLL_AREA_MIN_HEIGHT);
        this.scrollArea.setMinWidth(CONTENT_WIDTH);
        this.layout.addToContents(this.scrollArea);

        // 底部按钮
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(
            Component.literal("完成"),
            button -> this.onDone()
        ).build());
        footer.addChild(Button.builder(
            Component.literal("取消"),
            button -> this.onClose()
        ).build());

        // 统一注册所有组件
        this.layout.visitWidgets(x$0 -> this.addRenderableWidget(x$0));
        this.repositionElements();
    }

    // ==================== 构建滚动内容 ====================

    private void buildScrollContent() {
        this.scrollContent.defaultCellSetting().alignHorizontallyCenter();

        // ========== 第一组：边境之地核心设置 ==========
        this.scrollContent.addChild(this.createSectionHeader(
            Component.literal("§6§l边境之地设置")
        ));

        // 1. 边境之地距离（输入框）
        LinearLayout distanceRow = LinearLayout.horizontal().spacing(4);
        distanceRow.defaultCellSetting().alignVerticallyMiddle();
        distanceRow.addChild(new StringWidget(Component.literal("边境之地距离:"), this.font));
        EditBox distanceInput = new EditBox(this.font, 120, 20, Component.literal("边境之地距离"));
        distanceInput.setValue(String.valueOf(this.configData.farLandsDistance));
        distanceInput.setResponder(val -> {
            try {
                int parsed = Integer.parseInt(val.trim());
                this.configData.farLandsDistance = Mth.clamp(parsed, 10000000, 33554432);
            } catch (NumberFormatException ignored) {}
        });
        distanceRow.addChild(distanceInput);
        this.scrollContent.addChild(distanceRow, s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7边境之地生成位置与世界原点的距离\n§7该设置用于各类机制的判定，不影响地形生成"),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20));

        // 2. 条纹之地距离（输入框）
        LinearLayout stripeRow = LinearLayout.horizontal().spacing(4);
        stripeRow.defaultCellSetting().alignVerticallyMiddle();
        stripeRow.addChild(new StringWidget(Component.literal("条纹之地距离:"), this.font));
        EditBox stripeInput = new EditBox(this.font, 120, 20, Component.literal("条纹之地距离"));
        stripeInput.setValue(this.configData.stripeLandsDistance == -1 ? "-1" : String.valueOf(this.configData.stripeLandsDistance));
        stripeInput.setResponder(val -> {
            try {
                int parsed = Integer.parseInt(val.trim());
                this.configData.stripeLandsDistance = parsed < 0 ? -1 : Mth.clamp(parsed, 0, 33554432);
            } catch (NumberFormatException ignored) {}
        });
        stripeRow.addChild(stripeInput);
        this.scrollContent.addChild(stripeRow, s -> s.paddingHorizontal(10));

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
        skyGridBuilder.addSwitch(
            Component.literal("渐进式边境之地"),
            () -> this.configData.progressiveFarlands,
            val -> this.configData.progressiveFarlands = val
        ).withInfo(Component.literal("强制让自实现的 lerp 方法返回 start，不再进行原计算"));
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
                    case "Bedrock" -> Component.literal("Bedrock");
                    case "64bit-Bedrock" -> Component.literal("64bit-Bedrock");
                    default -> Component.literal(mode);
                },
                "32bit"
            )
            .withValues("32bit", "64bit", "Bedrock", "64bit-Bedrock")
            .create(0, 0, CONTENT_WIDTH - 20, 20,
                Component.literal("精度模式"),
                (button, val) -> this.configData.precisionMode = val
            );
        this.scrollContent.addChild(precisionModeButton, s -> s.paddingHorizontal(10));
        this.scrollContent.addChild(new StringWidget(
            Component.literal("§7选择坐标精度等级：32位（经典边境之地）、64位（中期改造）、基岩版边境之地 (Float计算)以及基岩版边境之地的64位变种"),
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
    }

    // ==================== 布局调整 ====================

    @Override
    protected void repositionElements() {
        if (this.scrollArea != null) {
            this.scrollArea.setMaxHeight(SCROLL_AREA_MIN_HEIGHT);
            this.layout.arrangeElements();
            int availableExtraHeight = this.height - this.layout.getFooterHeight() - this.scrollArea.getRectangle().bottom();
            this.scrollArea.setMaxHeight(this.scrollArea.getHeight() + availableExtraHeight);
        }
    }

    // ==================== 渲染 ====================

    @Override
    public void extractRenderState(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 黑色背景
        graphics.fill(0, 0, this.width, this.height, 0xFF000000);

        // 渲染所有已注册的 renderable
        super.extractRenderState(graphics, mouseX, mouseY, a);

        // ===== 动态总结栏（每次渲染都读取最新值） =====
        Component summary = Component.literal(
            "§7当前配置：边境之地距离 §e" + String.format("%,d", this.configData.farLandsDistance)
            + " §r§7| 精度模式 §b" + this.configData.precisionMode
        );
        int summaryWidth = this.font.width(summary);
        int summaryY = (this.scrollArea != null ? this.scrollArea.getRectangle().bottom() : this.height / 2) + 5;
        graphics.text(this.font, summary.getVisualOrderText(),
            (this.width - summaryWidth) / 2, summaryY, -6250336);

        // 顶部分割线
        if (this.scrollArea != null) {
            int separatorY = this.layout.getHeaderHeight();
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Screen.HEADER_SEPARATOR,
                0,
                separatorY,
                0.0F,
                0.0F,
                this.width,
                2,
                32,
                2
            );

            // 底部分割线
            int footerTop = this.height - this.layout.getFooterHeight();
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Screen.FOOTER_SEPARATOR,
                0,
                footerTop - 2,
                0.0F,
                0.0F,
                this.width,
                2,
                32,
                2
            );
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
        // 保存当前配置到静态字段
        FarLandsConfigData.activeConfig = this.configData;
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        // 空实现
    }

    // ==================== 配置数据类 ====================

    public static class FarLandsConfigData {
        public int farLandsDistance = 12550824;
        public int stripeLandsDistance = 16777216;
        public boolean enableSkyGrid = false;
        public boolean forceSkyGrid = false;
        public boolean progressiveFarlands = false;
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

        /** 当前活动的 FarLands 配置，由 WorldMainSettingScreen.onDone() 写入 */
        public static FarLandsConfigData activeConfig = new FarLandsConfigData();
    }
}