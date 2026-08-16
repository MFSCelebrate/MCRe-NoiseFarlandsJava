# MCRe-NoiseFarlandsJava
> 基于 Minecraft Java 版未混淆版本构建的边境之地 Mod  
> 源码来源于 [Hexeption/MCP-Reborn](https://github.com/Hexeption/MCP-Reborn/)  
> 实际的理想来源于我的另一项目 [MFSCelebrate/MCRe-NoiseFarlands](https://github.com/MFSCelebrate/MCRe-NoiseFarlands) 的携带版边境之地 Mod

> [!TIP]
> 该版本全权由 MFSCelebrate 制作，遵循 [GNU General Public License](./LICENSE) 协议之后才能进行对该项目的其他操作与管理  
> 作者本人: [MFSCelebrate(Bilibili)](https://b23.tv/hTl7eI5)

> [!WARNING]
> 该版本较不稳定，Mojang 在该版本正式引用了 Vulkan 渲染，所有关于原版的 Bug (特性)与该改版无关  
> 由于该改版使用了特殊的 version.json 用来支持 点对点联机，请你在测试版 Release 或者是 Github Actions 下载该文件并放在和 jar 同目录中

## 源代码方面的警告 
对于该项目的 Minecraft Java 源码，您需要注意以下几点:  
 1. 该项目完全使用了 Minecraft Java 的源码，并且 Mojang 完全移除了 26.1+ 的混淆，开放了 Minecraft 的修改  
 2. 但由于 [Minecraft Eula](https://www.minecraft.net/zh-hans/eula) 明确规定了如下内容  
      - 除非获得明确许可，否则不得“分发”我们(Mojang / Microsoft)的任何作品。这包括：  
      - 1. 分享副本(核心条款)：向任何人提供游戏软件或内容副本，这包含“禁止“分发”官方游戏或其任何修改版本。例如，将修改后的客户端发给他人”。  
      - 2. 商业使用：将任何我们创作的内容用于商业用途。  
      - 3. 直接盈利：试图利用我们的任何作品赚钱。  
      - 4. 不公访问：以不公平或不合理的方式允许他人访问我们的内容。  
 
 所以，您不得付费售卖、变相卖出该项目的源码，大幅度公开该改版，以及任何违反GNU General Public License和Minecraft Eula的行为。 (但是如果你付费买的你就是被骗了，傻得）  
   
 3. 该项目作为个人为了学习或开发 Mod 的基础，阅读、修改这些代码，就是官方默许或者鼓励的行为，不构成 [Minecraft Eula](https://www.minecraft.net/zh-hans/eula) 中的“破解”或“非正当访问”。  
   
 4. 该改版使用 MCP-Reborn，虽然其协议允许使用它反编译的产物，但最终能做什么，必须遵守 Mojang 的 Minecraft Eula  
   
 5. 该改版源码增加了 Minecraft 使用的原生库，均在 [Mojang](https://github.com/Mojang/) 的仓库开源  
   
 6. 该改版使用了自实现的 256bit 有/无符号整数与浮点数和数学计算工具，并将Minecraft内部的 version.json 彻底分离到 ModMetadata

## 技术预览
本改版通过**直接修改 Minecraft 底层**，实现了一些探索边境之地 / 距离现象的工具
  - 🛠 更加精确的调试面板，使用 256bit 有符号浮点数 计算玩家坐标
  - 🔗 允许控制世界生成器的生成类型，无论是 32bit / 64bit的边境之地，还是基岩版的边境之地都可以设置
  - ~~🤓 更加牛逼的设置~~

## 基于该仓库进行开发
由于本地构建过于麻烦，你只需要 Fork 此仓库或者将仓库源码上传到你自己的仓库，游戏会自动构建  
如果你需要本地构建，请你安装 Java 25+ 和 Gradle 9.5.1+，你可以参考如下命令(前提你装了 Java 25):
```I_am_a_script.sh
git clone https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava.git

cd MCRe-NoiseFarlandsJava

./gradlew build
```
此时你可以在 `build/lib` 里看见产物，如果是在 Github 仓库，产物和信息会自动上传到 Github Actions 的 WorkFlows

## 改版兼容性说明
- **支持版本**: ![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-blue)
- **兼容性**: 目前还没有引入专门给改版使用的 Fabric 加载器，先告一段落

## 每日构建版本
想要体验我们的最新改动 / 最新的功能？如果当天我们有代码改动，我们会上传到 [这里](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/releases/tag/nightly)

每日构建版本的改版文件均上传到**一个统一的 Github Release 页面**上

这些版本包含了最新的测试改动，但也有不稳定性和存档损坏风险

## 未来规划
此顺序不代表更新的优先级。
- [X] 对 F3 调试面板做进一步调整
- [X] 修复 33554432 问题
- [X] 解决 世界边界/界限 限制，参考 [INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler)
- [ ] 突破32位整数限制
- [ ] 加入缩放，偏移等一系列功能，参考同上
- [X] 加入世界的自定义设置 (参考 [边境旅者 SmallmanSeries/FarLandsTraveler](https://github.com/SmallmanSeries/FarLandsTraveler))
   - 重构排版，使其看起来更美观，并加入滚动面板以支持更多的设置
   - 天空网格的开关控件
   - 精度控制实现加强
   - 移除边境旅者的部分设置内容，并进行重命名
   - 即将到来: 偏移和缩放的高精度实现
   - 即将到来: 天空网格设置重构，不再彻底根据边境旅者实现 MathUtil
   - 高版本的 渐进式边境之地
   - 即将到来: 重构设置页面，加入换页功能，并加入适配新版本的 [自定义世界类型](https://zh.minecraft.wiki/w/%E8%87%AA%E5%AE%9A%E4%B9%89/Java%E7%89%881.13%E5%89%8D)
- [ ] 将部分在常规无法开启的调试环境 / 内部开关使用 [调试工具](https://zh.minecraft.wiki/w/%E8%B0%83%E8%AF%95%E5%B7%A5%E5%85%B7) 控制
- [ ] 加入该改版仓库的 Wiki 页面，参考 [INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler)

## 许可证
本项目依据 GNU General Public License 协议开源，详见 [LICENSE](./LICENSE) 文件