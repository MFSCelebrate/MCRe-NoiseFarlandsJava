package net.minecraft.client.gui.screens.worldselection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.WorldOptions;
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

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 40, 50);
    private final Screen parent;
    private final WorldCreationContext settings;

    // ==================== 配置数据 ====================
    private final FarLandsConfigData configData;

    // ==================== 构造函数 ====================
    public WorldMainSettingScreen(final Screen parent, final WorldCreationContext settings) {
        // 翻译键：边境旅者 配置
        super(Component.literal("边境旅者 配置"));
        this.parent = parent;
        this.settings = settings;
        this.configData = new FarLandsConfigData();
    }

    // ==================== 初始化 ====================
    @Override
    protected void init() {
        // --- 头部标题 ---
        this.layout.addTitleHeader(this.title, this.font);

        // --- 主体内容 ---
        LinearLayout content = this.layout.addToContents(LinearLayout.vertical().spacing(6));
        content.defaultCellSetting().alignHorizontallyCenter();

        // ========== 第一组：边境之地核心设置 ==========
        content.addChild(this.createSectionHeader(
            Component.literal("§6§l边境之地设置")
        ));

        // 1. 边境之地距离（滑块）
        content.addChild(new ConfigSlider(
            CONTENT_WIDTH - 20,
            () -> "边境之地距离: §e" + String.format("%,d", this.configData.farLandsDistance),
            val -> {
                this.configData.farLandsDistance = 10000000 + (int)(val * 23554432);
                return (double)(this.configData.farLandsDistance - 10000000) / 23554432;
            },
            0.108 // 12550824 的归一化值
        ), s -> s.paddingHorizontal(10));
        content.addChild(new StringWidget(
            Component.literal("§7边境之地生成位置与世界原点的距离\n§7该设置用于各类机制的判定，不影响地形生成").withStyle(s -> s.withColor(-6250336)),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20));

        // 2. 条纹之地距离（滑块）
        content.addChild(new StripeLandsSlider(
            CONTENT_WIDTH - 20
        ), s -> s.paddingHorizontal(10));

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
        content.addChild(skyGridBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 第二组：世界边界设置 ==========
        content.addChild(this.createSectionHeader(
            Component.literal("§6§l世界边界设置")
        ));

        SwitchGrid.Builder borderBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(4);
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
        content.addChild(borderBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 第三组：精度系统 ==========
        content.addChild(this.createSectionHeader(
            Component.literal("§b§l精度系统")
        ));

        // 精度模式选择
        CycleButton<String> precisionModeButton = CycleButton.builder(
                mode -> {
                    return switch (mode) {
                        case "32bit" -> Component.literal("32 位");
                        case "64bit" -> Component.literal("64 位");
                        case "256bit" -> Component.literal("256 位");
                        default -> Component.literal(mode);
                    };
                },
                "32bit"
            )
            .withValues("32bit", "64bit", "256bit")
            .create(
                0, 0, CONTENT_WIDTH - 20, 20,
                Component.literal("精度模式"),
                (button, val) -> this.configData.precisionMode = val
            );
        content.addChild(precisionModeButton, s -> s.paddingHorizontal(10));
        content.addChild(new StringWidget(
            Component.literal("§7选择坐标精度等级：32位（经典边境之地）、64位（中期改造）或256位（终极形态）")
                .withStyle(s -> s.withColor(-6250336)),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 40), s -> s.paddingHorizontal(20).paddingBottom(4));

        // ========== 第四组：假区块设置 ==========
        content.addChild(this.createSectionHeader(
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
        content.addChild(fcBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 第五组：结构生成 ==========
        content.addChild(this.createSectionHeader(
            Component.literal("§a§l结构生成设置")
        ));

        SwitchGrid.Builder structBuilder = SwitchGrid.builder(CONTENT_WIDTH - 20)
            .withRowSpacing(4);
        structBuilder.addSwitch(
            Component.literal("生成岩石之令实验室"),
            () -> this.configData.generateOotsLaboratory,
            val -> this.configData.generateOotsLaboratory = val
        ).withInfo(Component.literal("启用岩石之令实验室的生成"));
        content.addChild(structBuilder.build().layout(), s -> s.paddingHorizontal(10));

        // ========== 底部总结 ==========
        content.addChild(new MultiLineTextWidget(
            Component.literal(
                "§7当前配置：边境之地距离 §e" + String.format("%,d", this.configData.farLandsDistance)
                + "§r§7 | 精度模式 §b" + this.configData.precisionMode
            ),
            this.font
        ).setMaxWidth(CONTENT_WIDTH - 20).setCentered(true), s -> s.padding(10));

        // --- 底部按钮 ---
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(
            Component.literal("完成"),
            button -> this.onDone()
        ).build());
        footer.addChild(Button.builder(
            Component.literal("取消"),
            button -> this.onClose()
        ).build());

        this.layout.visitWidgets(x$0 -> this.addRenderableWidget(x$0));
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
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
        // TODO: 将 configData 应用到世界生成设置中
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float a) {
        this.extractMenuBackground(graphics);
    }

    // ==================== 配置数据类 ====================

    /**
     * 存储所有 FarLands 配置的数据类
     */
    public static class FarLandsConfigData {
        // 边境之地核心
        public int farLandsDistance = 12550824;
        public int stripeLandsDistance = 16777216;
        public boolean enableSkyGrid = false;
        public boolean forceSkyGrid = false;

        // 世界边界
        public boolean removeWorldBorder = true;
        public boolean removeWorldBoundary = true;
        public boolean removeCoordinateLimits = true;

        // 精度系统
        public String precisionMode = "32bit";

        // 假区块
        public boolean fcDisableBlockCollision = true;
        public boolean fcDisableBlockEffect = true;
        public boolean fcDisableFluidCollision = true;
        public boolean fcDisableFluidFlowing = true;
        public boolean fcDisableBlockInteraction = true;
        public boolean fcDisableExplosionEffect = true;
        public boolean fcDisableLadderBehavior = true;
        public boolean fcDisablePistonBehavior = true;

        // 结构
        public boolean generateOotsLaboratory = true;
    }

    // ==================== 条纹之地距离滑块 ====================

    @OnlyIn(Dist.CLIENT)
    private class StripeLandsSlider extends AbstractSliderButton {
        private static final int MAX_DISTANCE = 33554432;

        public StripeLandsSlider(final int width) {
            super(0, 0, width, 20, Component.empty(),
                  WorldMainSettingScreen.this.configData.stripeLandsDistance == -1 
                      ? 0.0 
                      : (double) WorldMainSettingScreen.this.configData.stripeLandsDistance / MAX_DISTANCE);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            String text;
            if (WorldMainSettingScreen.this.configData.stripeLandsDistance == -1) {
                text = "条纹之地距离: §c禁用";
            } else {
                text = "条纹之地距离: §e" + String.format("%,d", WorldMainSettingScreen.this.configData.stripeLandsDistance);
            }
            this.setMessage(Component.literal(text));
        }

        @Override
        protected void applyValue() {
            if (this.value < 0.01) {
                WorldMainSettingScreen.this.configData.stripeLandsDistance = -1;
            } else {
                WorldMainSettingScreen.this.configData.stripeLandsDistance = (int)(this.value * MAX_DISTANCE);
            }
            this.updateMessage();
        }
    }
}