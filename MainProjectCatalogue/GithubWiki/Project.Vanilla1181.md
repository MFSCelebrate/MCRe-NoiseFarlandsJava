# Minecraft 1.18.1 渐消之地：成因链、逐位对照与本改版的还原

> [!NOTE]
> 本页记录 **Minecraft 1.18.1**（以及携带版 0.6.1 的对应现象）的"渐消之地 / 距离现象"成因链，
> 并给出 **1.18.1 ↔ 26.2** 的逐位对照，以及本改版 `MCVanilla1_18_1Pipeline` 的还原方式与已知边界。

---

## 1. 术语与现象

| 现象 | 触发坐标 | 表现 |
| --- | --- | --- |
| **渐消之地**（1.18.1，32 位格点） | 约 **16,873,994,612**（随种子/方向变化） | 偏移噪声越界后数百格内地形失去固体方块，只剩空气与底部岩浆 |
| **渐消之地**（1.19.2+，有 `linearExtend` 保护） | 同上 | 实测可维持 **607,908 格**才崩 |
| **条纹之地 / 切片之地**（携带版 0.6.1 客户端） | **16,777,216**（2^24） | 方块表面网格被 float32 精度切碎 → 周期 3 的竖条纹 / 4~16 格宽的切片 |

---

## 2. 1.18.1 的世界生成体系（**没有密度函数**）

1.18.1 原版**不存在** `DensityFunction`（密度函数是 **1.18.2** 才引入的）。1.18.1 的地形由三件套决定：

| 组件 | 文件 | 作用 |
| --- | --- | --- |
| `NoiseSampler` | `net/minecraft/world/level/levelgen/NoiseSampler.java` | 八度噪声采样（气候通道 + 洞穴 + 矿脉），逐点计算初始密度 |
| `TerrainShaper` | `net/minecraft/world/level/biome/TerrainShaper.java` | **offset / factor / jaggedness** 三条 `CubicSpline` 样条 |
| `CubicSpline` | `net/minecraft/util/CubicSpline.java` | 样条求值与**端点外推** |

### 2.1 1.18.1 的初始密度公式（`NoiseSampler.calculateBaseNoise`）

```java
double f = sampleJaggedNoise(terrainInfo.jaggedness(), x, z);          // 锯齿噪声
double g = (computeBaseDensity(y, terrainInfo) + f) * terrainInfo.factor();
e = g * (g > 0.0 ? 4 : 1);                                             // 分段放大
density = e + blendedNoise;                                            // + 基础三维噪声
```

其中 `computeBaseDensity(y, terrainInfo)`：

```java
return (1.0 - y / 128.0) + terrainInfo.offset();                        // ← 1.18.1 的 depth
```

### 2.2 1.18.1 的样条端点外推（**没有 linearExtend 保护**）

```java
if (i < 0) {   // 输入低于第一个控制点
    return values.get(0).apply(c) + derivatives[0] * (f - locations[0]);
}
if (i == j) {  // 输入高于最后一个控制点
    return values.get(j).apply(c) + derivatives[j] * (f - locations[j]);
}
```

**Minecraft 1.19.2** 才给端点加上保护（斜率为零时直接返回端点值，不做乘法）：

```java
return derivative == 0.0F ? value : value + derivative * (input - location);
```

差别只有一处，但它是**渐消速度**的决定性因素（见 §3）。

---

## 3. 渐消之地的成因链（实测）

### 3.1 第一环：`SHIFT` 偏移噪声越过 32 位格点上限

`SHIFT` 噪声（`minecraft:offset`）由两组 Perlin 叠加，最高有效倍频层为 `-1`（采样倍率 1/2），
第二组还额外乘 `1.0181268882175227`。主世界先把世界坐标换成每 4 格一个单位的采样坐标，于是：

```text
quart        = floor(world / 4)
perlinInput  = quart × 1.0181268882175227 × 0.5 + seedOffset

world ≈ 2^31 × 8 / 1.0181268882175227 ≈ 16,873,996,142 格
```

正向输入达到 `2^31` 时，Java 的 `double → int` 窄化钳在 `2^31 − 1`，**格内小数离开 `[0,1)`**。

### 3.2 第二环：平滑插值变成外推

Perlin 的平滑权重是五次多项式 `6t^5 − 15t^4 + 10t^3`；`t` 越界后权重可远大于 1，
`SHIFT` 输出在几个采样单位内暴涨到数十亿，并被直接加到下一层的采样坐标。

### 3.3 第三环：气候噪声输出超过 float 可表示范围

被放大的坐标让大陆性/侵蚀/怪异度噪声的采样点再次越界，有限的 `double` 输出在很短距离内
就超过 `float` 的上限 → **±Infinity**。

### 3.4 第四环（末环）：地形样条把 `0 × Infinity` 变成 `NaN`

1.18.1 的样条对超出范围的输入执行端点外推：

```text
结果 = 端点值 + 端点斜率 × (输入 − 端点位置)
```

平坦端点的斜率为零 → `0 × Infinity = NaN` → 地形偏移、缩放因子、锯齿度全部变成 `NaN`
→ 参与合成最终密度 → 经网格插值传播 → 填充阶段走"无效密度"分支 → **只剩空气和底部岩浆**。

> 1.19.2 的 `linearExtend` 正是为了掐断这一环：端点斜率为零时直接返回端点值（有限），
> 实测把渐消距离从 **504 格** 拉到 **607,908 格**。

### 3.5 实测数据（种子 2026，Y=64、Z=8 截线）

| X（世界坐标） | Z 偏移（约） | 大陆性采样 | 最终密度 |
| ---: | ---: | ---: | ---: |
| 16,873,994,608 | 0.178 | −0.213 | −64 |
| 16,873,994,612 | 0.514 | −0.213 | −64 |
| 16,873,994,644 | 14,431 | −0.303 | −64 |
| 16,873,994,740 | 6,930,000 | −0.442 | −64 |
| 16,873,994,868 | 1.81 × 10^8 | −0.029 | −64 |
| 16,873,995,116 | 4.30 × 10^9 | −1.04 × 10^44 | **NaN** |

首个越界采样面 → 首个 `NaN`：**16,873,995,116 − 16,873,994,612 = 504 格**。

同一区间的区块固体方块数（`Z=0..15`，`Y=−64..319`）：

| 区块起始 X | 固体方块 | 其中 Y ≥ 150 | 岩浆 |
| ---: | ---: | ---: | ---: |
| 16,873,994,848 | 18,121 | 6,943 | 1,184 |
| 16,873,995,088 | 10,178 | 3,250 | 1,440 |
| 16,873,995,104 | 5,067 | 1,607 | 2,000 |
| 16,873,995,120 | **0** | 0 | 2,560 |

（`2,560 = 16 × 16 × 10` = 底部 10 层填满岩浆）

---

## 4. 携带版 0.6.1 的「条纹之地 / 切片之地」（客户端现象）

携带版 0.6.1 的 `client/renderer/TileRenderer.cpp` 把**绝对方块坐标**直接塞进 float32 顶点：

```cpp
// TileRenderer.cpp:47-49
float xf = (float)x;                 // int 世界坐标 → float32 绝对值
// TileRenderer.cpp:1810-1811（renderFaceUp，其余 5 个面同理）
float x0 = x + tt->xx0;              // ★「+1 格」的偏移在 float32 绝对量级上做
float x1 = x + tt->xx1;
```

float32 的 ULP 在 `2^23…2^24` 区间是 1，到 **2^24 = 16,777,216 变成 2**：
`16777216 + 1` 正好落在两个可表示值正中间，被 **round-half-to-even** 舍回偶数尾数那一侧 → **+1 格被吃掉**。

实测面宽序列（X 轴，Z 同理）：

```text
2^24 起 12 格：0 0 2 0 0 0 2 0 0 0 2 0      ← 周期 3
```

- 宽 **0** 的面 → 塌成线/点 → **不可见**
- 宽 **2** 的面 → 与相邻格重叠 → **变粗**

⇒ 屏幕上就是**周期 3 的竖向条纹**；2^25 起 ULP = 4、2^26 起 ULP = 8 → 面宽按 4/8/16 格跳变 → **切片之地**。

> 携带版的**相机减法**同样是 float32（`RenderList.init(xOff,yOff,zOff)` + `float32(chunkPos)`），
> 所以它在 2^24 还会出现"相机按 2 格跳"的抖动；本改版保留精确的相机相对化，抖动不复刻。

---

## 5. 1.18.1 ↔ 26.2 逐位对照表

| 项 | 1.18.1 | 26.2 | 结论 |
| --- | --- | --- | --- |
| `TerrainShaper` / `TerrainProvider` | `world/level/biome/TerrainShaper.java`（offset / factor / jaggedness） | `data/worldgen/TerrainProvider.java` | **逐点等价** ✅ |
| `CubicSpline` 求值 | 区间二分 + lerp 公式 | 同 | **逐位等价** ✅ |
| `CubicSpline` 端点外推 | `value + slope×(input−loc)`（`0×Inf = NaN`） | `linearExtend` 捷径 | **差异** ⚠ |
| depth | `(1 − Y/128) + offset` | `yClampedGradient(−64,320,1.5,−1.5) + offset` | Y∈[−64,320] 内**完全等价** ✅ |
| 初始密度 | `g×(g>0?4:1)` | `4×quarterNegative(g)` | **逐位等价** ✅ |
| jaggedness 合成 | `h>0 ? d×h : d/2×h` | `halfNegative()` | **逐位等价** ✅ |
| 洞穴 | 1.18.1 无密度函数洞穴（NoiseSampler 内联） | 1.19+ 洞穴（带厚度调制） | **差异** ⚠ |
| 密度函数框架 | **不存在** | 1.18.2+ 引入 | 1.18.1 的世界生成无法"直接"表达 |

---

## 6. 本改版的还原方式

### 6.1 管线 `MCVanilla1_18_1Pipeline`

- **自建 1.18.1 的 overworld 地形参数**（offset / factor / jaggedness / depth / sloped_cheese），
  不再引用 26.2 的 bootstrap holder（旧版引用 26.2 的定义 → 带着 26.2 的 jaggedness、
  窗口感知 depth、1.19+ 洞穴 → 密度场自相矛盾 → 世界不可玩、边境之地形态也不同）；
- `final_density` / `preliminary_surface_level` **逐字沿用**数据包 `1_18_1_overworld.json` 的结构，
  其中 `range_choice` 的下界用数据包的**最小范围哨兵** `-1.0E308`
  （309 位精确十进制 = double `0x1.1ccf385ebc8ap1023`；**不是 -2^1024** —— 2^1024 作 double 会溢出成 Infinity）；
- 管线激活时**强制整条世界生成器链**走 1.18.1 的样条外推语义。

### 6.2 全局开关「1.18.1 样条外推（不走 CubicSpline.linearExtend）」

位于 **世界自定义设置 → 第一页「边境之地设置」**。开启后 `CubicSpline` 的端点外推改用 1.18.1 公式，
即使在普通管线上也能复现"渐消提前"的 1.18.1 行为；默认关闭（= 26.2 原版行为）。

### 6.3 已知边界

1. 数据包的 `final_density` / `preliminary_surface_level` 是 **1.18.2 风格**的密度函数写法
   （1.18.1 原版没有密度函数）—— 本管线逐字沿用该数据包，只替换其中引用的 `overworld/*`；
2. **洞穴**沿用 26.2 的定义（数据包的引用），与 1.18.1 原版 NoiseSampler 内联洞穴有形态差异；
3. **surface_rule / biome_source** 不接管（仍走 26.2）——1.18.1 原版的表面规则体系与 1.18.2 不同。

---

## 7. 参考

- 本页的 1.18.1 源码结论均来自反编译源码 `Materials/1.18.1/`（`net/` + `com/` 为源码，`data/` 为数据包）；
- 「1.19.2 研究版逐网格测量」：<https://github.com/pomidorka1515/64bit-1.19.2/blob/main/WORLDGEN.md>
- 携带版 0.6.1 源码：`Materials/Minecraft0.6.1/client/renderer/TileRenderer.cpp`

---

## 🔗 相关链接

- [首页](Home)
- [项目路线图](Project.RoadMap)
- [功能性测试与单元测试](Project.FuncAndUnitTest)
