# MCRe-NoiseFarlandsJava
> 基于 Minecraft Java 版未混淆版本构建的边境之地 Mod  
> 源码来源于 [Hexeption/MCP-Reborn](https://github.com/Hexeption/MCP-Reborn/)  
> 实际的理想来源于我的另一项目 [MFSCelebrate/MCRe-NoiseFarlands](https://github.com/MFSCelebrate/MCRe-NoiseFarlands) 的携带版边境之地 Mod

> [!TIP]
> 该版本全权由 MFSCelebrate 制作，遵循 [GNU General Public License](./LICENSE) 协议之后才能进行对该项目的其他操作与管理  
> 作者本人: [MFSCelebrate(Bilibili)](https://b23.tv/hTl7eI5)

> [!WARNING]
> 该版本较不稳定，Mojang 在该版本正式引用了 Vulkan 渲染，所有关于原版的 Bug (特性)与该改版无关

## 源代码( Source Code )方面的警告 
对于该项目的 Minecraft Java 源码，您需要注意以下几点:  
 1. 该项目完全使用了 Minecraft Java 的源码，并且 Mojang 完全移除了 26.1+ 的混淆，开放了 Minecraft 的修改  
 2. 但由于 [Minecraft Eula](https://www.minecraft.net/zh-hans/eula) 明确规定了如下内容  
      - 除非获得明确许可，否则不得“分发”我们(Mojang / Microsoft)的任何作品。这包括：  
      - 1. 分享副本(核心条款)：向任何人提供游戏软件或内容副本，这包含“禁止“分发”官方游戏或其任何修改版本。例如，将修改后的客户端发给他人”。  
      - 2. 商业使用：将任何我们创作的内容用于商业用途。  
      - 3. 直接盈利：试图利用我们的任何作品赚钱。  
      - 4. 不公访问：以不公平或不合理的方式允许他人访问我们的内容。  
 
 所以，您不得付费售卖、变相卖出该项目的源码，大幅度公开该改版，以及任何违反GNU General Public License和Minecraft Eula的行为。 (虽然但是公布源码本来就违反了“禁止向任何其他人提供我们的游戏软件或内容”和“不允许他人以不公平或不合理的方式访问”这一条，虽然但是如果你付费买的你就是被骗了，傻得）  
 3. 该项目作为个人为了学习或开发 Mod 的基础，阅读、修改这些代码，就是官方默许或者鼓励的行为，不构成 [Minecraft Eula](https://www.minecraft.net/zh-hans/eula) 中的“破解”或“非正当访问”。  
 4. 该改版使用 MCP-Reborn，虽然其协议允许使用它反编译的产物，但最终能做什么，必须遵守 Mojang 的 Minecraft Eula  
 5. 该改版源码增加了 Minecraft 使用的原生库，均在 [Mojang](https://github.com/Mojang/) 的仓库开源  

## 未来的更新
- [ ] 对 F3 调试面板做进一步调整
- [ ] 修复 33554432 问题
- [ ] 解决 世界边界/界限 限制，参考 [INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler/)
- [ ] 突破32位整数限制
- [ ] 加入缩放，偏移等一系列功能，参考同上