# 📘 功能性测试与单元测试

> [!TIP]
> 本文说明 MCRe NoiseFarlandsJava 两套测试体系的目的与分工：**[单元测试](#二目录与命名约定)（`src-unittest/test`）**与 **[功能性测试](#五如何添加功能性测试)（`src-functest/test`）**，以及在测试环境里跑 Minecraft 的 `Bootstrap` 时那些不踩就不知道的坑。

---

## 一、为什么要做这套测试体系

### 1.1 改版的性质决定了「肉眼玩」不是验证手段

MCRe 是对 Minecraft Java **26.2** 的深度改版：坐标 Long 化、窗口化分层生成、C2ME 式多线程 worldgen、自研 DynamicAccuracy 大数库、三条地形管线（Java-1.18.2+ / Java-26.3 / Bedrock-Edition）……

这些改动的共同点是**行为差异极其细微**：噪声多一个 ULP、插值权重差 1e-3、某个 section 的写入归属错了线程……游戏里肉眼看不出差别，但会变成「边境之地形态不对」「气候带错乱」「随机崩溃」。这类问题**只能靠可重复执行的断言来守**。

### 1.2 改造前的现状

- 所有验证脚本都躺在 `tmp/` 下（100+ 个 `XxxTest.java` / `DiagXxx.java`），靠人手动 `javac` + `java` 跑
- CI 只验证**能编译**（日志里长期是 `:test NO-SOURCE`），**不验证行为对**
- 脚本用 `System.out.println("PASS ...")` 表达结果，没有统一的失败语义（部分还直接 `System.exit(1)`）

### 1.3 现在的目标

把 CI 从「**能编译**」升级为「**行为正确**」：

| 层级 | 源集 | 何时执行 | 失败后果 | 覆盖对象 |
|---|---|---|---|---|
| 单元测试 | `src-unittest/test` | 每次 `gradle build`（CI 自动） | **产物构建失败（硬门禁）** | 我们自己的算法/精度/边界逻辑 |
| 功能性测试 | `src-functest/test` | 显式 `gradle functest`（CI 单独步骤） | 该步骤失败，不影响产物 | 与 Minecraft 运行时耦合的部分 |

### 1.4 两层分工的理由

- **单元测试**：不碰 Minecraft 运行时 → 秒级、无需资源、零环境依赖 → 每次 push 都跑，适合当硬门禁
- **功能性测试**：需要 Minecraft 的类、注册表、资源 → 较慢且可能因环境差异而抖动 → 独立跑，**不拖垮产物构建**

---

## 二、目录与命名约定

```text
src-unittest/
├── test/                                  ← 单元测试根（package test）
│   ├── BandIndexUnitTests.java
│   └── DynamicAccuracyUnitTests.java
└── resources/                             ← 单元测试的运行期资源（最小 lang）
    └── assets/minecraft/lang/en_us.json

src-functest/
├── test/                                  ← 功能性测试根（package test）
│   ├── OwnerThreadAssertionTests.java
│   └── NoiseSourceTests.java
└── resources/                             ← 功能性测试的运行期资源
    └── assets/minecraft/lang/en_us.json
```

**为什么两个根同在 `package test` 却不撞类**：类名后缀不同（`*UnitTests` 与 `*Tests`），完整类名自然不同 —— 例如 `test.FooUnitTests` 与 `test.FooTests` 是两个类。Gradle 默认的测试类扫描规则（`**/*Test.class`、`**/*Tests.class`、`**/*TestCase.class`）也能同时命中两种命名。

**它们不会被打包进 jar**：两个目录都是独立的**测试源集**，不是 `main` 源集；`testImplementation` / `functestImplementation` 都不会进入产物。

> [!WARNING]
> **目录必须与包名镜像**：根是 `src-unittest`，那么 `src-unittest/test/Foo.java` 的包声明必须是 `package test;`。否则 javac 的 `-sourcepath` 隐式查找会找不到类，IDE 索引也会错位（这是本项目多源根布局的第一纪律）。

---

## 三、Gradle 与 CI 是怎么接的

### 3.1 源集与依赖（`build.gradle`）

```groovy
// 功能测试源集必须声明在 dependencies 之前：
// Gradle 是「创建源集时」才生成 functestImplementation / functestRuntimeOnly，
// 写在 dependencies 之后会报 "Could not find method functestImplementation()"。
sourceSets {
    functest {
        java { srcDirs = ['src-functest/test']; include '**' }
        resources { srcDirs = ['src-functest/resources']; include '**' }
    }
}

dependencies {
    testImplementation 'org.junit.jupiter:junit-jupiter:5.14.2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    functestImplementation 'org.junit.jupiter:junit-jupiter:5.14.2'
    functestRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

sourceSets {
    test {                                  // 单元测试 → 标准 test 源集 → 进 build
        java { srcDirs = ['src-unittest/test']; include '**' }
        resources { srcDirs = ['src-unittest/resources']; include '**' }
    }
}

// 自定义源集不会继承 main 的依赖与产物 → 手工挂上
sourceSets.functest.compileClasspath += sourceSets.main.compileClasspath
sourceSets.functest.runtimeClasspath += sourceSets.main.runtimeClasspath

tasks.named('test', Test) { useJUnitPlatform() }

tasks.register('functest', Test) {         // 独立 task，不参与 build
    testClassesDirs = sourceSets.functest.output.classesDirs
    classpath = sourceSets.functest.runtimeClasspath
    useJUnitPlatform()
}

tasks.named('build') { dependsOn 'functestClasses' }   // 只编译、不运行
```

### 3.2 任务速查

| 命令 | 源集 | 进 `build`？ | 说明 |
|---|---|---|---|
| `./gradlew test` | `src-unittest/test` | ✅（build → check → test） | 单元测试，秒级 |
| `./gradlew functest` | `src-functest/test` | ❌ | 功能性测试，需显式执行 |
| `./gradlew functestClasses` | `src-functest/test` | ✅（被 build 依赖） | 只编译不运行 → 功能测试写错代码 CI 也会红 |
| `./gradlew build` | — | ✅ | 产物构建 + 单元测试 |

### 3.3 CI 里的位置

`.github/workflows/build.yml` 的顺序是：

1. **Test 1**：残留 Patch Diff 检查（覆盖 `src/common`、`src-deps`、`src-testinfrastructure`、`src-unittest`、`src-functest`）
2. **build**：`./gradlew build`（含单元测试）
3. **Test 2**：`./gradlew functest`（功能性测试）

> [!NOTE]
> 功能性测试**不挂在 build 上**，所以「功能测试写错了」不会让产物构建失败；但 `functestClasses` 会被 build 依赖，**编译错误**依然会在 CI 里暴露。

---

## 四、如何添加单元测试

### 4.1 判断标准

> **只依赖 JDK + 我们自己的 `net.MinecraftTools.*`** → 属于单元测试。

`net.MinecraftTools` 是本项目自研的库（DynamicAccuracy 大数、`_256bit_Refactoring` 精度层、向量库、地形管线等），**不需要 Minecraft 运行时**，因此仍算单元测试。

只要触碰了 `net.minecraft.*`（注册表、命令、配置、资源、渲染），就应当放到[功能性测试](#五如何添加功能性测试)。

### 4.2 标准写法

```java
package test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.MinecraftTools.Math.DynamicAccuracy.BigDecimal;
import net.MinecraftTools.Math.DynamicAccuracy.RoundingMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DynamicAccuracyUnitTests {

    @Test
    @DisplayName("BigDecimal 精确十进制：0.1 + 0.2 = 0.3（double 会得 0.30000000000000004）")
    void bigDecimalIsExact() {
        assertEquals("0.3", BigDecimal.valueOf(0.1).add(BigDecimal.valueOf(0.2)).toString());
    }

    @Test
    @DisplayName("BigDecimal 舍入：2.5 HALF_UP → 3")
    void bigDecimalRounding() {
        assertEquals("3", BigDecimal.valueOf(2.5).setScale(0, RoundingMode.HALF_UP).toString());
    }
}
```

要点：

- 类名以 **`UnitTests`** 结尾，**不要 public**（JUnit 5 允许包级可见）
- `@DisplayName` 写清楚「这条断言在保护什么」，失败时一眼能看懂
- 断言要写**期望值**，不要写「跑通就算过」

### 4.3 从旧的自检脚本迁移（只换外壳，保留原逻辑与期望值）

历史上大量验证脚本是 `main()` + `System.out.println("PASS ...")` 的形态。迁移时**不要重写逻辑**（期望值是当时实测锤出来的宝贵资产），只把外壳换成 JUnit：

```java
@Test
@DisplayName("分带索引：floorMod 防护与原版环形语义一致")
void scriptRuns() throws Exception {
    final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
    final java.io.PrintStream originalOut = System.out;
    System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
    try {
        // ↓↓↓ 原脚本的 main() 主体原样搬进来 ↓↓↓
        int LEN = 192;
        boolean allSame = true;
        for (int y = 0; y <= 384; y += 7) {
            for (int offset = -4; offset <= 4; offset += 2) {
                if (Math.floorMod(y + offset, LEN) != (y + offset + LEN) % LEN) {
                    allSame = false;
                }
            }
        }
        // ↑↑↑ 原脚本的 main() 主体原样搬进来 ↑↑↑
    } finally {
        System.setOut(originalOut);
    }

    final String output = buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    // 先剔除「0 项 FAIL / 0 FAIL」这类零失败汇总行，否则会被文本标记误判
    final String meaningful = output.replaceAll("(?i)0\\s*(项|个)?\\s*FAIL", "");
    assertFalse(meaningful.contains("FAIL"), "脚本自检出现 FAIL：\n" + output);
}
```

迁移时必查的三个坑：

| 坑 | 现象 | 处理 |
|---|---|---|
| 汇总行里含字面 `FAIL` | 明明「11 PASS / 0 FAIL」却被判失败 | 先剔除零失败汇总行再判定 |
| 脚本里有 `System.exit(...)` | 测试 JVM **直接消失**，CI 看不到任何结果 | 删掉 `System.exit`，改用断言表达失败 |
| `Map.ofEntries` 建表 | 类初始化时对重复键抛异常 | 建表前先断言「键唯一」 |

> [!TIP]
> 我们保留了批量迁移脚本（当前在 `tmp/migrate_tests.py`，属临时目录，后续会归档）与元数据校验脚本（`tmp/validate_jsonc.py`）；新增迁移时可复用其思路。

### 4.4 本地怎么跑

```bash
./gradlew test                       # 正常路径（需要联网拉 JUnit）
```

离线 / 无网环境（CI 才是最终裁判）：可用 `javac` + JUnit 控制台启动器，或临时写一份 **JUnit API 桩 + 反射 runner** 把测试真跑一遍 —— 桩只验证「我们的代码与 API 用法」，框架本身交给 CI 验。

---

## 五、如何添加功能性测试

### 5.1 判断标准

> **需要 `net.minecraft.*` 的类**（命令树、配置读写、注册表、区块/光照系统、客户端界面）→ 属于功能性测试。

### 5.2 标准写法

```java
package test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import test.DebugAssertions.OwnerThreadAssertion;

class OwnerThreadAssertionTests {

    private WorldMainSettingScreen.FarLandsConfigData config;

    @BeforeEach
    void setUp() {
        this.config = WorldMainSettingScreen.FarLandsConfigData.activeConfig;
        this.config.debugAssertionsMultiThread = false;      // 改全局状态前先归位
    }

    @AfterEach
    void tearDown() {
        this.config.debugAssertionsMultiThread = false;      // 用完必须还原
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
}
```

要点：

- **凡是改动全局状态（配置单例、静态字段、注册表）的测试，必须在 `@AfterEach` 还原**，否则会污染后续用例
- 功能性测试**不挂 `build`** → 逻辑错误不会被产物构建抓到，记得显式跑 `./gradlew functest`
- 需要 Minecraft 启动相关能力（注册表、物品栏页签）时，见下一章

---

## 六、Minecraft Bootstrap 相关

### 6.1 为什么需要 Bootstrap

`Bootstrap.bootStrap()` 会完成一整套游戏启动期初始化：内置注册表（`BuiltInRegistries`）、**物品栏页签校验**（`CreativeModeTabs.validate`）、实体选择器选项、熔炉/ Dispense 行为等。

凡是依赖这些数据结构的逻辑（读物品、查注册表、校验页签），测试里必须先 bootstrap，否则要么 NPE，要么拿到空注册表。

### 6.2 标准两步

```java
net.minecraft.SharedConstants.tryDetectVersion();   // 版本元数据（DataFixerUpper / 注册表版本依赖它）
net.minecraft.server.Bootstrap.bootStrap();       // 注册表引导
```

`Bootstrap.bootStrap()` 本身是**幂等**的，重复调用安全。

### 6.3 ⚠️ 必须提供 `/assets/minecraft/lang/en_us.json`

这是本项目测试体系里**最容易踩、报错最迷惑**的一个坑：

1. `net.minecraft.locale.Language` 的静态字段 `DEFAULT_INSTANCE` 在**类初始化**时读取 classpath 根的 `/assets/minecraft/lang/en_us.json`
2. 该文件缺失 → **Language 类初始化失败**
3. 随后 `Bootstrap.bootStrap()` → `CreativeModeTabs.validate()` → `TranslatableContents.decompose()` → `Language.getInstance()` 拿到空值 → **抛 NPE**

也就是说：**报出来的错是「注册表引导时 NPE」，根因却是「少了一个语言文件」**，完全指不到正确方向。

**解决办法**：两个测试源集各放一份最小语言文件 ——

```text
src-unittest/resources/assets/minecraft/lang/en_us.json
src-functest/resources/assets/minecraft/lang/en_us.json
```

内容只需要够用即可（我们用的最小版本）：

```json
{
  "language.name": "English",
  "language.region": "United States",
  "menu.singleplayer": "Singleplayer",
  "menu.multiplayer": "Multiplayer",
  "menu.options": "Options",
  "menu.quit": "Quit",
  "multiplayer.player": "Player",
  "multiplayer.status.pinging": "Pinging...",
  "multiplayer.status.connecting": "Connecting to the server...",
  "multiplayer.disconnect.generic": "Disconnected",
  "chat.type.text": "<%s> %s",
  "block.minecraft.stone": "Stone",
  "block.minecraft.dirt": "Dirt",
  "block.minecraft.grass_block": "Grass Block",
  "item.minecraft.stone": "Stone",
  "entity.minecraft.creeper": "Creeper",
  "entity.minecraft.zombie": "Zombie",
  "entity.minecraft.pig": "Pig",
  "entity.minecraft.cow": "Cow",
  "entity.minecraft.player": "Player",
  "death.attack.generic": "%1$s died",
  "death.attack.player": "%1$s was slain by %2$s",
  "gui.done": "Done",
  "gui.cancel": "Cancel",
  "gui.back": "Back",
  "gui.yes": "Yes",
  "gui.no": "No"
}
```

> [!TIP]
> 注意路径是 **`/assets/minecraft/lang/en_us.json`**（前导斜杠 = classpath 根）。放进 `src/resources` 是**不够**的 —— 那是主源集的资源，测试运行时不在测试 classpath 上。

### 6.4 其它注意事项

- **绝不要在测试里调用 `System.exit(...)`**：会直接杀掉测试 JVM，CI 只能看到「进程消失」
- **不要依赖客户端静态初始化**：无客户端环境下，`Minecraft.getInstance()` 可能为 `null`；尽量只触碰纯数据类（例如只取 `WorldMainSettingScreen.FarLandsConfigData` 这个嵌套数据类，而不是整个屏幕类）
- **注意同名类陷阱**：本项目自研的 `BigDecimal` 使用的是 `net.MinecraftTools.Math.DynamicAccuracy.RoundingMode`，**不是** `java.math.RoundingMode`

---

## 七、我们当前的测试清单

### 7.1 单元测试（16 个类 / 19 个用例）

| 测试类 | 守住的东西 |
|---|---|
| `DynamicAccuracyUnitTests` | 自研大数库：精确十进制（0.1+0.2=0.3）、大数不丢精度、舍入语义 |
| `IntRefactor256UnitTests` / `DynamicNumberUnitTests` / `MathHelperRefactorUnitTests` | 256 位重构版的整数/动态精度/数学辅助函数正确性 |
| `FloatExtensionUnitTests` | 36 位浮点中间层（FloatExtension）正确性 |
| `ToExactUnitTests` / `ToStrCheckUnitTests` | 精确十进制字符串输出、进制输出 |
| `VectorMathUnitTests` | 向量化后端与标量**逐位一致**（bit-identical） |
| `BandIndexUnitTests` | 黏土带索引：`floorMod` 防护与原版环形语义一致 |
| `WrapBedrockUnitTests` / `BedrockExactNoStretchUnitTests` | Bedrock 模式的 wrap 折叠落点、精确路径**无地形拉伸** |
| `Noise0601UnitTests` / `Diag0601DomainUnitTests` | 0.6.1 携带版管线的噪声逐位复刻与取值域 |

### 7.2 功能性测试（3 个类 / 6 个用例）

| 测试类 | 守住的东西 |
|---|---|
| `OwnerThreadAssertionTests` | 「断言调试」线程归属断言：开关关闭不抛 / owner 正确不抛 / owner 错误抛异常 / 互斥归属冲突抛异常 |
| `NoiseSourceTests` | 噪声源可重建且确定性（seed 与参数敏感） |
| `DiagFloatChainMathTests` | Float 密度链与主线逐点对照（20 采样 + canary 反证） |

### 7.3 暂时未迁移的脚本（及原因）

| 脚本 | 原因 |
|---|---|
| `ThreeChainsTest` / `ClimateQuantTest` / `YIdentityPrecisionTest` / `PerlinLerpTraceTest` | 纯诊断型（只打印观察结果、没有判定）→ 需要先补上断言再迁 |
| `Float256Check` / `BigMulCheck` / `FinalTest` | 依赖**已被整包删除**的旧 `net.MinecraftTools.Math._256Bit` 包 → 脚本本身已失效 |
| `FloatRefactorCorrectness` | 依赖外部参考实现 `com.inf.long2.binaries`（不在本仓库内） |
| `Noise0601Batch2Test` | 其中「角点逐位对照」**按设计就不可能一致**（PE add 3D 的 yOld 缓存用旧 y 小数算 grad）→ 属诊断脚本而非门禁 |
| `TestValueOfDouble` | 断言的是 `java.math.BigDecimal(double)` 的精确二进制语义，而**自研 `BigDecimal.valueOf(double)` 语义不同** → 待裁定后再决定是改实现还是改预期 |

---

## 八、常见问题排查

| 症状 | 原因 | 处理 |
|---|---|---|
| CI 里 `:test NO-SOURCE` | 测试源集没接上 | 检查 `build.gradle` 的 `sourceSets` |
| `Could not find method functestImplementation()` | 自定义源集声明晚于 `dependencies` | 把 `sourceSets { functest { … } }` 整块提前 |
| Bootstrap 时 NPE（`TranslatableContents.decompose`） | classpath 根缺 `/assets/minecraft/lang/en_us.json` | 往测试源集的 `resources` 放最小 lang |
| 测试 JVM 突然消失 | 脚本里有 `System.exit(...)` | 删掉，改用断言 |
| 类找不到 / IDE 报红 | 目录与包名不镜像 | 对齐 `package` 声明 |
| 明明全 PASS 却判失败 | 脚本汇总行含字面 `FAIL` | 先剔除零失败汇总行再判定 |
| `Map.ofEntries` 初始化抛异常 | 表里有重复键 | 建表前断言键唯一 |

---

## 九、相关页面

- [首页](Home)
- [项目路线图](Project.RoadMap)

---

> [!NOTE]
> 本体系仍在扩充中。新增测试请遵循上述**命名（`*UnitTests` / `*Tests`）**与**分层（是否依赖 `net.minecraft.*`）**约定，并确保单元测试不引入 Minecraft 运行时依赖 —— 这是它能当硬门禁的前提。
