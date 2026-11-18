package test.DebugAssertions;

import com.mojang.logging.LogUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.slf4j.Logger;

/**
 * 🔧 MCRe「断言调试」：<b>线程归属（owner thread）断言</b>。
 *
 * <p>原型来自 Minecraft PE 1.0.0（携带版）源码 {@code world/level/BlockSource.cpp}：
 * <pre>
 * #ifndef PUBLISH
 * #define DEBUG_ASSERT_OWNER_THREAD DEBUG_ASSERT(isOwnerThread(), "ERROR, this is running on the wrong thread");
 * #else
 * #define DEBUG_ASSERT_OWNER_THREAD {}
 * #endif
 * </pre>
 * 即：<b>断言「当前线程是不是这块数据的 owner」</b>，不是就直接崩。
 *
 * <p>MCRe 版与 PE 的差异：
 * <ul>
 *     <li>PE 是<b>编译期</b>开关（{@code PUBLISH} 宏，发布版整个断言被抹掉）；
 *     MCRe 是<b>运行期</b>开关（世界内「全局配置 → 调试」页的「断言调试」，默认关闭）。</li>
 *     <li>PE 断言失败走 {@code DEBUG_ASSERT} → 打印日志后 abort；
 *     MCRe 断言失败先 {@link Logger#error} 输出详细信息（当前线程 / owner 线程 / 上下文），
 *     再抛 {@link IllegalStateException} → 游戏崩溃报告里能直接看到是谁越权。</li>
 * </ul>
 *
 * <p><b>开关关闭时零开销</b>：每个断言点先读一个 static boolean（配置对象在启动时构造一次），
 * 不满足即直接返回，不会进入任何线程名/日志格式化。
 *
 * @see WorldMainSettingScreen.FarLandsConfigData#debugAssertionsMultiThread
 */
public final class OwnerThreadAssertion {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 互斥归属登记表：key → 当前持有它的线程。
     * <p>用于「同一份数据同一时刻只能有一个 owner」这类<b>串行化</b>不变量
     * （并行 worldgen 的半径锁单元、光照引擎的任务 drain 等）。
     */
    private static final ConcurrentMap<Object, Thread> EXCLUSIVE_OWNERS = new ConcurrentHashMap<>();

    private OwnerThreadAssertion() {
    }

    /**
     * 断言总开关（读世界内「全局配置 → 调试 → 断言调试」）。
     *
     * @return true = 开启，所有断言点生效；false = 关闭（零开销直接返回）
     */
    public static boolean enabled() {
        final WorldMainSettingScreen.FarLandsConfigData config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;

        return config != null && config.debugAssertionsMultiThread;
    }

    /**
     * 核心断言：当前线程必须是 {@code owner}。
     *
     * @param owner   该数据的归属线程；{@code null} 视为无归属（断言失败）
     * @param context 上下文描述（会写进日志与崩溃信息，便于定位是哪条路径越权）
     */
    public static void assertOwnerThread(final Thread owner, final String context) {
        if (!enabled()) {
            return;
        }

        final Thread current = Thread.currentThread();

        if (owner != null && owner == current) {
            return;
        }

        fail(owner, current, context);
    }

    /**
     * 反向断言：当前线程<b>不能</b>是 {@code owner}（用于抓「同一线程同时扮演两种角色」的协议违规）。
     *
     * @param owner   该数据的归属线程；{@code null} 视为无归属（断言通过）
     * @param context 上下文描述
     */
    public static void assertNotOwnerThread(final Thread owner, final String context) {
        if (!enabled()) {
            return;
        }

        final Thread current = Thread.currentThread();

        if (owner == null || owner != current) {
            return;
        }

        fail(owner, current, context + "（当前线程不应是该数据的 owner）");
    }

    /**
     * 断言当前线程是某个 <b>Level</b> 的归属线程（Level 构造时所在线程 = 世界主线程）。
     *
     * @param level   目标世界
     * @param context 上下文描述
     */
    public static void assertLevelOwnerThread(final Level level, final String context) {
        if (!enabled()) {
            return;
        }

        if (level == null) {
            fail(null, Thread.currentThread(), context + "（Level 为 null）");
            return;
        }

        assertOwnerThread(level.worldThread(), context);
    }

    /**
     * 断言当前线程是某个 <b>MinecraftServer</b> 的世界主线程。
     *
     * @param server  目标服务端
     * @param context 上下文描述
     */
    public static void assertServerThread(final MinecraftServer server, final String context) {
        if (!enabled()) {
            return;
        }

        if (server != null && server.isSameThread()) {
            return;
        }

        fail(null, Thread.currentThread(), context + "（服务端世界主线程）");
    }

    /**
     * 断言当前线程是某个区块的<b>「生成式写入」归属线程</b>（MCRe 分带生成机制）。
     *
     * <p>分带填充在异步线程改写活区块，{@link ChunkAccess#beginGenerationWrite()} 登记归属线程、
     * {@link ChunkAccess#endGenerationWrite()} 释放；本断言用于抓「别的线程来收尾」这类越权。
     *
     * @param chunk   目标区块
     * @param context 上下文描述
     */
    public static void assertGenerationWriteOwner(final ChunkAccess chunk, final String context) {
        if (!enabled()) {
            return;
        }

        if (chunk == null) {
            fail(null, Thread.currentThread(), context + "（Chunk 为 null）");
            return;
        }

        assertOwnerThread(chunk.generationWriteOwner(), context);
    }

    /**
     * <b>互斥归属</b>断言：同一 {@code key} 同一时刻只能属于一个线程。
     *
     * <p>与 {@link #assertOwnerThread} 的区别：owner 断言是「这块数据<b>永远</b>归某个固定线程」，
     * 互斥断言是「这块数据<b>当下</b>只能有一个持有者」——适用于本来就允许多线程轮流进入、
     * 但绝不允许两人同时在临界区的场景（并行生成的锁单元、光照任务 drain）。
     *
     * @param key     互斥单元标识
     * @param context 上下文描述
     */
    public static void assertExclusiveOwner(final Object key, final String context) {
        if (!enabled()) {
            return;
        }

        final Thread current = Thread.currentThread();
        final Thread previous = EXCLUSIVE_OWNERS.putIfAbsent(key, current);

        if (previous != null && previous != current) {
            fail(previous, current, context + "（互斥归属冲突：" + key + " 同时被两个线程持有）");
        }
    }

    /**
     * 释放互斥归属（<b>只释放自己持有的</b>，避免误删他人登记）。
     *
     * @param key 互斥单元标识
     */
    public static void releaseExclusiveOwner(final Object key) {
        if (EXCLUSIVE_OWNERS.isEmpty()) {
            return;
        }

        EXCLUSIVE_OWNERS.remove(key, Thread.currentThread());
    }

    /**
     * 互斥归属断言（坐标单元版）：用「chunkX,chunkZ」字符串做 key，
     * <b>刻意不复用</b>锁的打包 long key —— 这样「锁 key 算错导致同一逻辑单元用了两把锁」的
     * bug 也会被本断言抓出来（两个线程会同时认为自己独占同一逻辑单元）。
     *
     * @param chunkX  单元 X 坐标
     * @param chunkZ  单元 Z 坐标
     * @param context 上下文描述
     */
    public static void assertExclusiveCell(final long chunkX, final long chunkZ, final String context) {
        if (!enabled()) {
            return;
        }

        assertExclusiveOwner(cellKey(chunkX, chunkZ), context);
    }

    /** 释放坐标单元的互斥归属（只释放自己持有的）。 */
    public static void releaseExclusiveCell(final long chunkX, final long chunkZ) {
        if (EXCLUSIVE_OWNERS.isEmpty()) {
            return;
        }

        EXCLUSIVE_OWNERS.remove(cellKey(chunkX, chunkZ), Thread.currentThread());
    }

    /** 坐标单元 key（仅在开关开启时构造，避免无谓分配）。 */
    private static String cellKey(final long chunkX, final long chunkZ) {
        return chunkX + "," + chunkZ;
    }

    /**
     * 断言当前线程是<b>客户端主线程</b>（渲染 / 输入线程）。
     *
     * @param context 上下文描述
     */
    public static void assertClientThread(final String context) {
        if (!enabled()) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();

        if (minecraft != null && minecraft.isSameThread()) {
            return;
        }

        fail(null, Thread.currentThread(), context + "（客户端主线程）");
    }

    /** 断言失败：先写日志（详细信息），再抛异常引发崩溃。 */
    private static void fail(final Thread owner, final Thread current, final String context) {
        final String ownerName = owner == null ? "<null / 无归属>" : owner.getName() + "#" + owner.getId();
        final String currentName = current.getName() + "#" + current.getId();
        final String message = "[MCRe 断言调试] 线程归属断言失败：" + context
                + " | 期望 owner=" + ownerName
                + " | 实际 current=" + currentName;

        LOGGER.error(message);
        LOGGER.error("[MCRe 断言调试] 线程栈（当前线程）：");

        for (final StackTraceElement element : current.getStackTrace()) {
            LOGGER.error("    at {}", element);
        }

        throw new IllegalStateException(message);
    }
}
