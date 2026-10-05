# Contributing Guide
=> [简体中文](./CONTRIBUTING.md) / **English** <=  
Thank you for your willingness to contribute to **MCRe-NoiseFarlandsJava**!

This project is a Far Lands mod built on the unobfuscated version of Minecraft Java Edition. The source code comes from [Hexeption/MCP-Reborn](https://github.com/Hexeption/MCP-Reborn/), and the actual idea comes from the Pocket Edition Far Lands mod in my other project [MFSCelebrate/MCRe-NoiseFarlands](https://github.com/MFSCelebrate/MCRe-NoiseFarlands).

Before you start contributing, please read this guide, the project [README](./README.md), the [MIT License](./LICENSE), and the [Minecraft EULA](https://www.minecraft.net/zh-hans/eula).

---

## Table of Contents

- [How You Can Contribute](#how-you-can-contribute)
- [Quick Practice](#quick-practice)
- [Development Environment](#development-environment)
- [Building and Testing](#building-and-testing)
- [Reporting Issues](#reporting-issues)
- [Submitting Feature Suggestions](#submitting-feature-suggestions)
- [Submitting Code](#submitting-code)
- [Code and Commit Guidelines](#code-and-commit-guidelines)
- [Documentation and Wiki Contributions](#documentation-and-wiki-contributions)
- [License and EULA Notes](#license-and-eula-notes)
- [Contact](#contact)

---

## How You Can Contribute

We welcome all contributions, including issue reports, feature suggestions, and code submissions.

You can participate in the following ways:

1. **Report issues**: Report bugs, crashes, compatibility issues, or abnormal behavior in GitHub Issues.
2. **Suggest features**: Suggest new debugging tools, world generation controls, precision improvements, or Wiki content.
3. **Submit code**: Fork the repository, modify the code, and submit via Pull Request.
4. **Improve documentation**: Improve the README, contributing guide, Wiki pages, or usage instructions.
5. **Participate in testing**: Test nightly builds, different world generation types, and Far Lands-related features.

---

## Quick Practice

Whether you're reporting a bug or suggesting a new feature, you're welcome to **submit an Issue**! To help us understand and respond efficiently, we've prepared two convenient structured templates for you:

*   **[Report an Issue](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues/new?template=bug_report_zh.yml)**: For submitting reproducible bugs.
*   **[Provide Feedback](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues/new?template=suggestion_zh.yml)**: For sharing ideas for new features or improvements.

Before submitting, [searching existing Issues](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/issues) and [checking the Wiki](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/wiki) may help you find an answer quickly.

---

## Development Environment

This project recommends the following environment for development:

- **Java**: Java 25+
- **Gradle**: Gradle 9.5.1+
- **Git**
- **Minecraft version**: Minecraft 26.2 (subject to the README and the actual project)
- GitHub Actions automatic builds are recommended to reduce local environment setup costs.

If you need to build locally, please install Java 25+ and Gradle 9.5.1+ first.

---

## Building and Testing

### Using GitHub Actions for Automatic Builds

Because local builds may be cumbersome, you can directly fork this repository or upload the repository source to your own repository, and the game will build automatically.

If you are in a GitHub repository, the artifacts and information will be automatically uploaded to GitHub Actions Workflows.

### Local Build

You can refer to the following commands:

```bash
git clone https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava.git

cd MCRe-NoiseFarlandsJava

./gradlew build
```

After the build completes, you can find the artifacts in `build/lib`.

### Testing Notes

- This mod is incompatible with multiplayer, but you can join the test server that the mod itself can enable.
- Because this mod uses a special `version.json` to support peer-to-peer multiplayer, please download this file from the beta Release or GitHub Actions and place it in the same directory as the jar.
- Nightly builds contain the latest test changes, but there is a risk of instability and save corruption. Back up your saves before testing.
- When reporting issues, please try to distinguish between "vanilla bugs" and "mod bugs".

---

## Reporting Issues

Before submitting an issue, please search whether a similar issue already exists.

A high-quality issue report should include:

- **Problem description**: Clearly explain what happened.
- **Reproduction steps**: How to reliably reproduce the issue.
- **Expected behavior**: What you expected to happen.
- **Actual behavior**: What actually happened.
- **Environment information**:
  - Whether you are using a Release or a nightly build
- **Logs and screenshots**: Relevant crash logs, console output, or screenshots.
- **Additional notes**: Whether you modified world generator settings, etc.

Please do not post content that violates the Minecraft EULA in Issues, such as Mojang original resources, decompiled code copies, or paid distribution content.

---

## Submitting Feature Suggestions

Feature suggestions can revolve around the following directions:

- More precise debug screen
- 256-bit signed floating-point coordinate calculation
- World generator type control
- 32-bit / 64-bit Far Lands
- Bedrock Edition Far Lands
- World border / boundary limits
- Scaling, offset, and high-precision implementation
- Sky grid settings
- Custom world settings
- Debug tools and internal switches
- Wiki documentation improvements

When submitting suggestions, please explain:

- What problem you want to solve
- Suggested implementation approach or reference projects
- Versions and compatibility that may be affected
- Whether you are willing to participate in implementation

---

## Submitting Code

### Basic Process

1. Fork this repository.
2. Create a new branch from the default branch, for example:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Make your changes.
4. Ensure the code builds successfully.
5. Commit your changes with a clear commit message.
6. Push the branch to your fork.
7. Submit a Pull Request to the default branch of this repository.

### A Pull Request Should Include

- **Change description**: What this PR does and why it is needed.
- **Related issue**: If there is a related issue, link it with `#number`.
- **Testing method**: How you tested it and which scenarios you tested.
- **Screenshots or logs**: If it involves UI, debug screen, or world generation, please provide screenshots or logs.
- **Compatibility notes**: Whether it affects multiplayer, saves, world generation, or `version.json`.
- **Checklist**:
  - [ ] The code builds successfully.
  - [ ] Basic testing has been performed.
  - [ ] No Mojang original resources or decompiled code copies are included.
  - [ ] The Minecraft EULA is not violated.
  - [ ] Relevant documentation or Wiki has been updated (if needed).
  - [ ] No build artifacts, local configuration, or keys have been committed.

### Licensing of Code Contributions

Unless otherwise explicitly stated, your contributions will be licensed under the project's **MIT License (MIT)**.

Please ensure you have the right to submit the relevant code and that it does not contain third-party infringing content.

---

## Code and Commit Guidelines

- Follow the project's existing code style and directory structure.
- A Pull Request should focus on one feature or one fix as much as possible.
- Commit messages should be concise and clear, explaining the purpose of the change.
- Do not commit build artifacts, caches, local environment configuration, or keys.
- For changes involving core features such as 256-bit math, world generation, debug screen, and sky grid, please explain the implementation approach and test results in detail in the PR.
- If you modify parts related to Minecraft source code, pay special attention to EULA restrictions and do not publicly distribute Mojang works.
- If you use MCP-Reborn-related artifacts, please comply with its license and ultimately comply with Mojang's Minecraft EULA.

---

## Documentation and Wiki Contributions

This project plans to continuously improve its Wiki pages, with references to projects such as [UltimateScaler INF32768/UltimateScaler](https://github.com/INF32768/UltimateScaler).

Contributions are welcome for the following content:

- Installation and build instructions
- Feature usage tutorials
- World generator settings explanations
- Debug screen explanations
- Far Lands and distance phenomena overview
- FAQ and troubleshooting
- Version compatibility notes

When contributing to the Wiki, please note:

- Content should be accurate, verifiable, and easy to understand.
- Do not upload Mojang original resources, decompiled code, or copyrighted materials unless you confirm that the license permits it.
- If a Wiki page has a separate license notice at the bottom (for example, CC BY-NC-SA 3.0), please follow that notice.
- When citing other projects or Wiki content, please indicate the source and comply with their license.

---

## License and EULA Notes

### Project License

This project is open source under the **MIT License (MIT)**. See the [LICENSE](./LICENSE) file for details.

### Minecraft and Mojang

- Original Minecraft code, resources, and related content are copyright **Mojang Studios**.
- Original Minecraft code, resources, decompiled code, and Mojang content are not covered by this project's MIT License.
- When using this project, you must comply with the [Minecraft EULA](https://www.minecraft.net/zh-hans/eula).
- Unless explicitly permitted, you may not distribute any of Mojang's works. This includes sharing game software or content copies, and distributing the official game or any modified version of it.
- You may not use content created by Mojang for commercial purposes.
- You may not directly profit from any of Mojang's works.
- You may not allow others to access Mojang content in an unfair or unreasonable way.
- You may not sell the project source code for a fee, sell it in a disguised form, widely distribute this mod, or commit any act that violates the Minecraft EULA.

### Learning and Development Use

As a foundation for individuals to learn or develop mods, reading and modifying these codes is behavior tacitly permitted or encouraged by Mojang and does not constitute "cracking" or "unauthorized access" under the Minecraft EULA.

This project uses **MCP-Reborn**. Although its license permits the use of its decompiled artifacts, what you can ultimately do must comply with Mojang's Minecraft EULA.

The source code of this mod adds native libraries used by Minecraft, all of which are open source in [Mojang](https://github.com/Mojang/)'s repositories.

This mod uses self-implemented 256-bit signed/unsigned integer, floating-point, and mathematical calculation tools, and completely separates Minecraft's internal `version.json` into `ModMetadata`.

---

## Contact

- **Author**: [MFSCelebrate (Bilibili)](https://b23.tv/hTl7eI5)
- **Known collaborator (please do not disturb, thank you)**: [\_\_Infinitive\_\_ (Bilibili)](https://space.bilibili.com/1196843580)
- **Issue feedback**: Please prioritize GitHub Issues

Thank you for your contribution!