package test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import test.DebugAssertions.OwnerThreadAssertion;

/**
 * 🔧 MCRe 功能测试样板：<b>依赖 Minecraft 类</b>（客户端配置 + 自研断言工具）的集成回归。
 *
 * <p>归属：{@code src-functest/test}（功能测试根）——类名以 {@code Tests} 结尾。
 * <p>与 {@code src-unittest/test}（单元测试根，类名以 {@code UnitTests} 结尾）同包 {@code test}，
 * 靠类名后缀区分，不会撞类。
 * <p>本类需要 Minecraft 类在 classpath 上（但不需要完整 Bootstrap / 资源）→ 放在<b>独立源集</b>，
 * 由独立的 {@code functest} task 执行，<b>不挂进 {@code build}</b>，避免功能测试失败拖垮产物构建。
 */
class OwnerThreadAssertionTests {

    private WorldMainSettingScreen.FarLandsConfigData config;

    @BeforeEach
    void setUp() {
        this.config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        this.config.debugAssertionsMultiThread = false;
    }

    @AfterEach
    void tearDown() {
        this.config.debugAssertionsMultiThread = false;
    }

    @Test
    @DisplayName("开关关闭：错误 owner 也不抛（零开销直通）")
    void disabledDoesNotThrow() {
        assertDoesNotThrow(() -> OwnerThreadAssertion.assertOwnerThread(new Thread("fake-owner"), "单元测试-关闭态"));
        assertDoesNotThrow(() -> OwnerThreadAssertion.assertExclusiveOwner("K-关闭态", "单元测试-关闭态"));
    }

    @Test
    @DisplayName("开关开启：owner 就是当前线程 → 不抛")
    void enabledWithCorrectOwnerDoesNotThrow() {
        this.config.debugAssertionsMultiThread = true;
        assertDoesNotThrow(() -> OwnerThreadAssertion.assertOwnerThread(Thread.currentThread(), "单元测试-正确 owner"));
    }

    @Test
    @DisplayName("开关开启：owner 不是当前线程 → 抛 IllegalStateException")
    void enabledWithWrongOwnerThrows() {
        this.config.debugAssertionsMultiThread = true;
        final Thread foreign = new Thread("foreign-owner");
        assertThrows(
                IllegalStateException.class,
                () -> OwnerThreadAssertion.assertOwnerThread(foreign, "单元测试-错误 owner")
        );
    }

    @Test
    @DisplayName("互斥归属：同一 key 被别的线程持有时抛异常，释放后可重新获取")
    void exclusiveOwnership() {
        this.config.debugAssertionsMultiThread = true;
        final String key = "K-互斥";
        OwnerThreadAssertion.assertExclusiveOwner(key, "单元测试-互斥占用");
        // 同线程重入允许
        assertDoesNotThrow(() -> OwnerThreadAssertion.assertExclusiveOwner(key, "单元测试-互斥重入"));
        OwnerThreadAssertion.releaseExclusiveOwner(key);
        // 释放后可重新获取
        assertDoesNotThrow(() -> OwnerThreadAssertion.assertExclusiveOwner(key, "单元测试-互斥重取"));
        OwnerThreadAssertion.releaseExclusiveOwner(key);
    }
}
