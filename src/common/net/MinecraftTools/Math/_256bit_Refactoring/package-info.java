/**
 * 🔧 MCRe 256bit 重构版（第三版）——自包含、无 DynamicAccuracy 依赖。
 *
 * <p>重写动机（大佬 2026-11-01 指令）：旧 _256Bit 包 bug 多（AxorBisInfinitive 118 点测试
 * 预检 1661 错误）且效率不及 Long2/ScalarWide。本包从零重写，设计原则：
 * <ol>
 *   <li><b>字段布局最简</b>：纯 long 字段（无 int[] 数组、无 BigDecimal 缓存）——48 字节；
 *       指数/尾数字段独立不拆分（旧版 10 bug 全是"b 字段高 16 位拆指数"类跨域陷阱）；</li>
 *   <li><b>单点收口</b>：浮点打包走 make()、尾数铺位走 packLong/packInt256——全项目只有
 *       一条位场路径，杜绝风格漂移；</li>
 *   <li><b>手写展开</b>：进位链/部分积全展开（ScalarWide 的 register-specialized 思路），
 *       热路径零数组零循环；</li>
 *   <li><b>测试先行</b>：每方法 JDK（java.math.BigInteger / double 位语义）强对照，
 *       随机 10 万 + 全 k 扫描 + 病态边界（旧版教训：实测锤 > 读代码）。</li>
 * </ol>
 *
 * <p>算法出处：整数乘法 = Long2 的 unsignedMultiplyHigh 部分积累加（补码模 2^256 按无符号
 * 分组）；除法 = Knuth Algorithm D（OpenJDK MutableBigInteger.divideKnuth 的 32-bit limb
 * 教科书移植，试商 long 单步无溢出）；浮点 = SoftFloat 风格 GRS 工作区（尾数域 + 3 位
 * guard/round/sticky），消位只发生在指数差 ≤ 1（此时无截断，定理保证）。
 *
 * <p>路线（大佬：先做基层，一个一个加）：Int256 ✅ → Float256 ✅ → UInt256/UFloat256 →
 * DynamicNumber（可切换 Long/Int256/BigInteger 的坐标运算容器）。
 */
package net.MinecraftTools.Math._256bit_Refactoring;
