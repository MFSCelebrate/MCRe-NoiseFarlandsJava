package net.MinecraftTools.logging;

import com.mojang.logging.LogListeners;
import com.mojang.logging.LogQueues;
import com.mojang.logging.plugins.ListenerLogAppender;
import com.mojang.logging.plugins.QueueLogAppender;
import java.io.InputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * MCRe 异步日志引导。
 *
 * <p>为什么需要：Minecraft 26.2 的日志是<b>全同步</b>的 —— 每条日志的格式化、控制台写、文件写
 * 都发生在调用线程上。开启 DEBUG 级别后，日志本身就变成帧率杀手（实测：调用方线程
 * 46~54 µs/行，最坏单次 14 ms；带 Throwable 的错误日志 88~106 µs/行）。
 *
 * <p>本类做三件事：
 * <ol>
 *   <li>{@link #configureEarly(Path)}：把 log4j2 的配置切到<b>游戏目录里可编辑的一份</b>
 *       {@code mcre-log4j2.xml}（首次启动时从 jar 内导出；之后以用户改过的为准，删掉即恢复默认）。
 *       官方配置在 {@code com.mojang:logging} 库里（classpath 上的 log4j2.xml）；游戏目录里那份
 *       不在 classpath，默认不生效（除非启动器显式传 {@code -Dlog4j.configurationFile}）。</li>
 *   <li>{@link #attachTracyAppender()} / {@link #attachServerGuiAppender()}：把 Mojang 自带的两个
 *       插件 appender 按需挂上。它们默认每行都要在调用线程做一次格式化（Queue）或一次栈回溯取
 *       位置（Listener 的 {@code %F:%L}，实测约 40 µs/行），客户端根本用不到，所以不静态挂载。</li>
 *   <li>{@link #flushForExit()}：崩溃/退出前等异步队列排空，避免尾部日志丢失。</li>
 * </ol>
 *
 * <p>日志级别：默认 {@code info}（与原版一致）；需要调试时用 {@code -Dmcre.log.level=debug} 覆盖，
 * 或直接编辑游戏目录里的 {@code mcre-log4j2.xml}。
 */
public final class MCReLoggingBootstrap {
    /** classpath 上的 MCRe 配置模板资源名（首次启动时导出到游戏目录）。 */
    public static final String CONFIG_RESOURCE = "mcre-log4j2.xml";
    /** 游戏目录里的配置文件名（用户可编辑）。 */
    public static final String CONFIG_FILE_NAME = "mcre-log4j2.xml";
    /** 覆盖 root 日志级别的系统属性名（值取 log4j2 的 Level 名，如 debug/info/warn/error）。 */
    public static final String LEVEL_PROPERTY = "mcre.log.level";
    /** Mojang 插件 appender：Tracy 性能分析（target 名与 TracyBootstrap 一致）。 */
    private static final String TRACY_APPENDER = "Tracy";
    /** Mojang 插件 appender：专用服务器 GUI 控制台（target 名与 MinecraftServerGui 一致）。 */
    private static final String SERVER_GUI_APPENDER = "ServerGuiConsole";
    /** 退出/崩溃时等待异步队列排空的上限。 */
    private static final long FLUSH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(2);

    private static boolean configured;

    private MCReLoggingBootstrap() {
    }

    /**
     * 切换 log4j2 配置，配置文件放在<b>当前工作目录</b>（专用服务器用；客户端用
     * {@link #configureEarly(Path)} 传游戏目录）。
     *
     * <p><b>必须在任何 Logger 创建之前调用</b>（log4j2 在第一次取 Logger 时读取配置，之后再改
     * 属性不生效）。
     */
    public static void configureEarly() {
        configureEarly(Path.of(System.getProperty("user.dir", ".")));
    }

    /**
     * 切换 log4j2 配置，配置文件放在指定目录（客户端传游戏目录）。
     *
     * <p>行为：
     * <ul>
     *   <li>启动器已显式指定 {@code -Dlog4j2.configurationFile}（或老名字
     *       {@code -Dlog4j.configurationFile}）→ <b>完全尊重它</b>，不导出也不覆盖；</li>
     *   <li>目录里没有 {@code mcre-log4j2.xml} → 从 jar 内导出一份默认模板；</li>
     *   <li>目录里已有 → <b>沿用用户那份</b>（用户改过的设置不丢）；</li>
     *   <li>用户那份 XML 解析失败 → 回退 jar 内默认配置，并在 stderr 打印提示（游戏照常跑）。</li>
     * </ul>
     */
    public static void configureEarly(Path directory) {
        if (configured) {
            return;
        }

        configured = true;
        // log4j2 的正式属性名是 log4j2.configurationFile；老 log4j1 名字 log4j.configurationFile 也能被 log4j2 识别，一并尊重
        String existing = System.getProperty("log4j2.configurationFile");
        if (existing == null || existing.isEmpty()) {
            existing = System.getProperty("log4j.configurationFile");
        }

        if (existing != null && !existing.isEmpty()) {
            // 启动器/用户显式指定 → 优先，不动它
            return;
        }

        Path target = directory.resolve(CONFIG_FILE_NAME);
        if (exportDefaultConfig(target) && isValidXml(target)) {
            System.setProperty("log4j2.configurationFile", target.toUri().toString());
        } else {
            System.setProperty("log4j2.configurationFile", "classpath:" + CONFIG_RESOURCE);
        }
    }

    /**
     * 挂载 Tracy appender（{@code --tracy} 时调用）。
     *
     * <p>Tracy 的 pattern 含 {@code (%F:%L)}，需要调用点位置信息 —— 这会让 log4j2 在调用线程
     * 为每一行做栈回溯（实测约 40 µs/行）。因此只在真正开启 Tracy 时才挂，避免平时白交这份税。
     */
    public static void attachTracyAppender() {
        Layout<? extends Serializable> layout = PatternLayout.newBuilder()
            .withPattern("(%F:%L): %msg{nolookups}%n")
            .build();
        LogListeners.Target target = LogListeners.getOrCreateTarget(TRACY_APPENDER);
        attach(TRACY_APPENDER, new ListenerLogAppender(TRACY_APPENDER, null, layout, true, target));
    }

    /**
     * 挂载专用服务器 GUI 控制台 appender（{@code MinecraftServerGui} 创建时调用）。
     *
     * <p>该 appender 每行都要做一次完整格式化，客户端用不到，所以只在真的要开 GUI 时挂。
     */
    public static void attachServerGuiAppender() {
        Layout<? extends Serializable> layout = PatternLayout.newBuilder()
            .withPattern("[%d{HH:mm:ss} %level]: %msg{nolookups}%n")
            .build();
        BlockingQueue<String> queue = LogQueues.getOrCreateQueue(SERVER_GUI_APPENDER);
        attach(SERVER_GUI_APPENDER, new QueueLogAppender(SERVER_GUI_APPENDER, null, layout, true, queue));
    }

    /**
     * 校验当前生效的日志配置是不是 MCRe 那份（用户把游戏目录里的 XML 改坏时，log4j2 会静默回退
     * 默认配置 = 退回同步，日志又变卡）。启动后调一次，发现不对就打印提示。
     *
     * @return 配置是否看起来是 MCRe 的（false = 已被改坏或被外部配置接管）
     */
    public static boolean verifyActive() {
        try {
            LoggerContext context = (LoggerContext)LogManager.getContext(false);
            Set<String> names = new TreeSet<>(context.getConfiguration().getRootLogger().getAppenders().keySet());
            boolean ok = names.contains("AsyncFile") || names.contains("File");
            if (!ok) {
                System.err.println("[MCRe] 当前生效的日志配置不是 MCRe 的（appenders=" + names
                    + "）—— 游戏目录里的 " + CONFIG_FILE_NAME + " 可能被改坏了，日志已回退 log4j2 默认配置（同步）。"
                    + "删掉该文件即可恢复 MCRe 默认。");
            }
            return ok;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 等待异步队列排空（崩溃/退出前调用），避免尾部日志丢失。
     *
     * <p>只等队列清空，<b>不</b>停掉 appender —— 之后的日志仍能正常写入。
     */
    public static void flushForExit() {
        try {
            LoggerContext context = (LoggerContext)LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            long deadline = System.nanoTime() + FLUSH_TIMEOUT_NANOS;
            for (Appender appender : configuration.getAppenders().values()) {
                if (appender instanceof AsyncAppender async) {
                    while (async.getQueueSize() > 0 && System.nanoTime() < deadline) {
                        try {
                            Thread.sleep(5L);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            // 退出路径上不允许再抛异常
        }
    }

    /** 首次运行时把 jar 内的配置模板导出到目标文件；已存在则不覆盖（保留用户改动）。 */
    private static boolean exportDefaultConfig(Path target) {
        try {
            if (!Files.exists(target)) {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                try (InputStream in = MCReLoggingBootstrap.class.getResourceAsStream("/" + CONFIG_RESOURCE)) {
                    if (in == null) {
                        return false;
                    }

                    Files.copy(in, target);
                }
            }

            return true;
        } catch (Throwable t) {
            System.err.println("[MCRe] 无法导出日志配置到 " + target + "：" + t + "（改用 jar 内默认配置）");
            return false;
        }
    }

    /** 粗校验 XML 是否可解析（解析失败就回退 jar 内默认，避免用户改坏配置后日志悄悄变回同步）。 */
    private static boolean isValidXml(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (Throwable ignored) {
                // 某些解析器不支持该特性，不影响基本校验
            }

            try {
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            } catch (Throwable ignored) {
                // 同上
            }

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.parse(in);
            return true;
        } catch (Throwable t) {
            System.err.println("[MCRe] 日志配置 " + file + " 解析失败（" + t + "），改用 jar 内默认配置。");
            return false;
        }
    }

    private static void attach(String name, Appender appender) {
        try {
            LoggerContext context = (LoggerContext)LogManager.getContext(false);
            Configuration configuration = context.getConfiguration();
            LoggerConfig root = configuration.getRootLogger();
            if (root.getAppenders().containsKey(name)) {
                return;
            }

            appender.start();
            root.addAppender(appender, Level.ALL, null);
            context.updateLoggers();
        } catch (Throwable t) {
            System.err.println("[MCRe] failed to attach logger appender '" + name + "': " + t);
        }
    }
}
