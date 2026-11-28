package net.MinecraftTools.render;

/**
 * 🔧 MCRe「基岩版 条纹之地与切片之地」——携带版 0.6.1 {@code TileRenderer} 的 <b>float32 顶点量化</b>（纯数学）。
 *
 * <p><b>它复刻的是什么</b>：携带版 0.6.1（{@code Materials/Minecraft0.6.1/client/renderer/TileRenderer.cpp}）把
 * <b>绝对方块坐标</b>直接塞进 float32 顶点：
 *
 * <pre>{@code
 * // TileRenderer.cpp:47-49   tesselateBlockInWorld
 * float xf = (float)x;                 // int 世界坐标 → float32 绝对值
 * // TileRenderer.cpp:1810-1811 renderFaceUp（另外 5 个面同理）
 * float x0 = x + tt->xx0;              // ★「+1 格」的偏移在 float32 绝对量级上做
 * float x1 = x + tt->xx1;
 * }</pre>
 *
 * <p>float32 的 ULP 在 2<sup>23</sup>…2<sup>24</sup> 区间是 1，到 <b>2<sup>24</sup> = 16777216 变成 2</b> ——
 * {@code 16777216 + 1} 正好落在两个可表示值正中间，被 <b>round-half-to-even</b> 舍回偶数尾数那一侧，
 * 于是「+1 格」的偏移<b>被吃掉</b>。实测面宽序列（X 轴，Z 同理）：
 *
 * <pre>{@code
 * 2^24 起 12 格：0 0 2 0 0 0 2 0 0 0 2 0      ← 周期 3
 * }</pre>
 *
 * <ul>
 *   <li>宽 <b>0</b> 的面 → 塌成线/点 → <b>不可见</b>（那一列「空掉」）</li>
 *   <li>宽 <b>2</b> 的面 → 与相邻格重叠 → <b>变粗</b></li>
 * </ul>
 *
 * 屏幕上就是 <b>周期 3 的竖向条纹 = 条纹之地</b>；再往上（2<sup>25</sup> 起 ULP=4、2<sup>26</sup> 起 ULP=8）
 * 面宽直接跳到 4/8/16 格 → 方块被「切片」→ <b>切片之地</b>。
 *
 * <p><b>本类提供什么</b>：把上述数学抽成<b>不依赖任何 Minecraft 类</b>的纯函数，方便单元测试直接锤。
 * 客户端接线见 {@link BedrockTileRenderer}。
 *
 * <p><b>逐位一致性</b>：携带版的完整链条是
 * <pre>{@code
 *   顶点 = float32(绝对方块坐标) + 角点 + float32(-区块原点)
 * }</pre>
 * 而本类的 {@link #localCorner} 算的是
 * <pre>{@code
 *   局部 = (float32(绝对方块坐标) + 角点) - float32(区块原点)
 * }</pre>
 * 两者<b>逐位相同</b>：区块原点是 16 的倍数 → float32 精确可表示 → {@code a + (-b)} 与 {@code a - b} 在 IEEE 下等价，
 * 且差值量级极小 → 减法不再引入二次舍入。
 *
 * @author MCRe NoiseFarlandsJava
 * @since 2026-11-28
 */
public final class BedrockFloatVertex {

    private BedrockFloatVertex() {
    }

    /**
     * 携带版 {@code (float)x}：把绝对方块坐标量化到 float32（就近舍入）。
     *
     * @param blockCoord 绝对方块坐标（方块，不是 section）
     * @return float32 量化后的绝对坐标
     */
    public static float quantizeBlockCoord(final long blockCoord) {
        return (float) blockCoord;
    }

    /**
     * 逐位复刻携带版顶点位置：<b>角点必须在绝对量级上相加</b>，最后再减去区块原点。
     *
     * @param absBlockCoord 绝对方块坐标
     * @param corner        角点偏移（0 / 1 / 0.5 …）
     * @param origin        区块（section）原点的 float32 值
     * @return 写入顶点缓冲的局部坐标
     */
    public static float localCorner(final long absBlockCoord, final float corner, final float origin) {
        return (quantizeBlockCoord(absBlockCoord) + corner) - origin;
    }

    /**
     * 同一个方块两个角点（0 与 1）之间的<b>实际面宽</b> —— 条纹/切片现象的直接度量。
     *
     * <p>正常区间恒为 1；越过 2<sup>24</sup> 后变成 0 / 2 / 4 / 8 … 的跳变。
     *
     * @param absBlockCoord 绝对方块坐标
     * @param origin        区块（section）原点的 float32 值
     * @return 面宽（1 = 正常）
     */
    public static float localFaceWidth(final long absBlockCoord, final float origin) {
        return localCorner(absBlockCoord, 1.0F, origin) - localCorner(absBlockCoord, 0.0F, origin);
    }
}
