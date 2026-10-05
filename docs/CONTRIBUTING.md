# 贡献指南
=> **简体中文** / [English](./docs/CONTRIBUTING.en.md) <=  
感谢你愿意为 **MCRe-NoiseFarlandsJava** 做出贡献！  

本项目是一个基于 Minecraft Java 版未混淆版本构建的边境之地 Mod，源码来源于 [Hexeption/MCP-Reborn](https://github.com/Hexeption/MCP-Reborn/)，实际灵感来源于 [MFSCelebrate/MCRe-NoiseFarlands](https://github.com/MFSCelebrate/MCRe-NoiseFarlands) 的携带版边境之地 Mod。

在开始贡献之前，请先阅读本指南、项目 [README](./README.md)、[MIT License](./LICENSE) 以及 [Minecraft EULA](https://www.minecraft.net/zh-hans/eula)。

---

## 目录

- [你可以如何贡献](#你可以如何贡献)
- [快速实践](#快速实践)
- [开发环境](#开发环境)
- [构建与测试](#构建与测试)
- [报告问题](#报告问题)
- [提交功能建议](#提交功能建议)
- [提交代码](#提交代码)
- [代码与提交规范](#代码与提交规范)
- [文档与 Wiki 贡献](#文档与-wiki-贡献)
- [许可证与 EULA 注意事项](#许可证与-eula-注意事项)
- [联系方式](#联系方式)

---

## 你可以如何贡献

我们欢迎包括问题报告、功能建议和代码提交在内的所有贡献。

你可以通过以下方式参与：

1. **报告问题**：在 GitHub Issues 中反馈 Bug、崩溃、兼容性问题或异常行为。
2. **提出功能建议**：建议新的调试工具、世界生成控制、精度改进或 Wiki 内容。
3. **提交代码**：Fork 仓库，修改代码，并通过 Pull Request 提交。
4. **改进文档**：完善 README、贡献指南、Wiki 页面或使用说明。
5. **参与测试**：测试每日构建版本、不同世界生成类型和边境之地相关功能。

---

## 快速实践

无论是报告 Bug 还是提出新功能建议，都欢迎**提交 Issue**！为了确保我们能高效地理解和响应，我们为您准备了两种便捷的结构化模板：

*   **[报告问题](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues/new?template=bug_report_zh.yml)**：用于提交可复现的 Bug。
*   **[提供反馈](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues/new?template=suggestion_zh.yml)**：用于分享新功能或改进的想法。

在提交前，[搜索现有 Issues](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues) 和 [查阅 Wiki](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/wiki) 可能会为您快速找到答案。

---

## 开发环境

本项目推荐使用以下环境进行开发：

- **Java**：Java 25+
- **Gradle**：Gradle 9.5.1+
- **Git**
- **Minecraft 版本**：Minecraft 26.2（以 README 和实际项目为准）
- 推荐使用 GitHub Actions 自动构建，减少本地环境配置成本。

如果你需要本地构建，请先安装 Java 25+ 和 Gradle 9.5.1+。

---

## 构建与测试

### 使用 GitHub Actions 自动构建

由于本地构建可能较为麻烦，你可以直接 Fork 此仓库，或将仓库源码上传到你自己的仓库，游戏会自动构建。

如果是在 GitHub 仓库中，产物和信息会自动上传到 GitHub Actions 的 Workflows。

### 本地构建

你可以参考以下命令：

```bash
git clone https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava.git

cd MCRe-NoiseFarlandsJava

./gradlew build
```

构建完成后，你可以在 `build/lib` 中看到产物。

### 测试注意事项

- 该改版不兼容多人联机，但可以进入改版本身就可开启的测试服务器。
- 由于该改版使用了特殊的 `version.json` 用来支持点对点联机，请在测试版 Release 或 GitHub Actions 中下载该文件，并放在与 jar 同目录中。
- 每日构建版本包含最新测试改动，但存在不稳定性和存档损坏风险。测试前请备份存档。
- 报告问题时，请尽量区分“原版 Bug”与“改版 Bug”。

---

## 报告问题

在提交 Issue 之前，请先搜索是否已有类似问题。

一个高质量的问题报告建议包含：

- **问题描述**：清晰说明发生了什么。
- **复现步骤**：如何稳定复现该问题。
- **期望行为**：你期望发生什么。
- **实际行为**：实际发生了什么。
- **环境信息**：
  - 使用的是 Release，还是 nightly 构建
- **日志与截图**：相关崩溃日志、控制台输出或截图。
- **附加说明**：是否修改了世界生成器设置等。

请勿在 Issue 中发布违反 Minecraft EULA 的内容，例如 Mojang 原始资源、反编译代码副本或付费分发内容。

---

## 提交功能建议

功能建议可以围绕以下方向展开：

- 更加精确的调试面板
- 256bit 有符号浮点数坐标计算
- 世界生成器类型控制
- 32bit / 64bit 边境之地
- 基岩版边境之地
- 世界边界 / 界限限制
- 缩放、偏移、高精度实现
- 天空网格设置
- 自定义世界设置
- 调试工具与内部开关
- Wiki 文档改进

提交建议时，请说明：

- 你希望解决什么问题
- 建议的实现方式或参考项目
- 可能影响的版本和兼容性
- 是否愿意参与实现

---

## 提交代码

### 基本流程

1. Fork 本仓库。
2. 从默认分支创建一个新的分支，例如：
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. 进行修改。
4. 确保代码可以构建通过。
5. 提交更改，并撰写清晰的提交信息。
6. 推送分支到你的 Fork。
7. 向本仓库默认分支提交 Pull Request。

### Pull Request 建议包含

- **变更说明**：这个 PR 做了什么，为什么需要它。
- **关联 Issue**：如果有相关 Issue，请使用 `#编号` 关联。
- **测试方式**：你如何测试的，测试了哪些场景。
- **截图或日志**：如果涉及界面、调试面板或世界生成，请提供截图或日志。
- **兼容性说明**：是否影响多人联机、存档、世界生成或 `version.json`。
- **检查清单**：
  - [ ] 代码可以成功构建。
  - [ ] 已进行基本测试。
  - [ ] 未包含 Mojang 原始资源或反编译代码副本。
  - [ ] 未违反 Minecraft EULA。
  - [ ] 已更新相关文档或 Wiki（如需要）。
  - [ ] 未提交构建产物、本地配置或密钥。

### 贡献代码的许可

除非另有明确说明，你提交的贡献将按本项目的 **MIT License (MIT)** 进行授权。

请确保你有权提交相关代码，并且没有包含第三方侵权内容。

---

## 代码与提交规范

- 遵循项目现有代码风格和目录结构。
- 一个 Pull Request 尽量专注于一个功能或一个修复。
- 提交信息应简洁明了，说明变更目的。
- 不要提交构建产物、缓存、本地环境配置或密钥。
- 对于涉及 256bit 数学、世界生成、调试面板、天空网格等核心功能的修改，请在 PR 中详细说明实现思路和测试结果。
- 如果修改了与 Minecraft 源码相关的部分，请特别注意 EULA 限制，不要公开分发 Mojang 作品。
- 如果使用 MCP-Reborn 相关产物，请遵守其协议，并最终遵守 Mojang 的 Minecraft EULA。

---

## 文档与 Wiki 贡献

本项目计划持续完善 Wiki 页面，参考了 [终极缩放器 INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler) 等项目。

欢迎贡献以下内容：

- 安装与构建说明
- 功能使用教程
- 世界生成器设置说明
- 调试面板说明
- 边境之地与距离现象科普
- 常见问题与故障排除
- 版本兼容性说明

贡献 Wiki 时请注意：

- 内容应准确、可验证、易于理解。
- 不要上传 Mojang 原始资源、反编译代码或受版权保护的素材，除非确认许可允许。
- 如果 Wiki 页面底部另有许可声明（例如 CC BY-NC-SA 3.0），请以该声明为准。
- 引用其他项目或 Wiki 内容时，请注明来源并遵守其许可协议。

---

## 许可证与 EULA 注意事项

### 项目许可证

本项目依据 **MIT License (MIT)** 开源，详见 [LICENSE](./LICENSE) 文件。

### Minecraft 与 Mojang

- 原始 Minecraft 代码、资源及相关内容版权归 **Mojang Studios** 所有。
- Minecraft 原始代码、资源、反编译代码及 Mojang 内容不在本项目 MIT 许可范围内。
- 使用本项目时，必须遵守 [Minecraft EULA](https://www.minecraft.net/zh-hans/eula)。
- 除非获得明确许可，否则不得分发 Mojang 的任何作品。这包括分享游戏软件或内容副本、分发官方游戏或其修改版本。
- 不得将 Mojang 创作的内容用于商业用途。
- 不得利用 Mojang 的任何作品直接盈利。
- 不得以不公平或不合理的方式允许他人访问 Mojang 内容。
- 不得付费售卖、变相卖出该项目的源码，不得大幅度公开该改版，不得进行任何违反 Minecraft EULA 的行为。

### 学习与开发用途

该项目作为个人为了学习或开发 Mod 的基础，阅读、修改这些代码，属于官方默许或鼓励的行为，不构成 Minecraft EULA 中的“破解”或“非正当访问”。

该项目使用 **MCP-Reborn**。虽然其协议允许使用它反编译的产物，但最终能做什么，必须遵守 Mojang 的 Minecraft EULA。

该改版源码增加了 Minecraft 使用的原生库，均在 [Mojang](https://github.com/Mojang/) 的仓库开源。

该改版使用了自实现的 256bit 有/无符号整数与浮点数和数学计算工具，并将 Minecraft 内部的 `version.json` 彻底分离到 `ModMetadata`。

---

## 联系方式

- **作者**：[MFSCelebrate（Bilibili）](https://b23.tv/hTl7eI5)
- **已知协作者 (请勿打扰，谢谢)**：[\_\_Infinitive\_\_（Bilibili）](https://space.bilibili.com/1196843580)
- **问题反馈**：请优先使用 GitHub Issues

感谢你的贡献！