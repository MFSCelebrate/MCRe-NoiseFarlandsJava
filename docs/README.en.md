![Logo](/docs/images/MCRe-NoiseFarlandsJava-Logo.png)

# MCRe-NoiseFarlandsJava
=> [简体中文](../README.md) / **English** <=
> A Far Lands mod built on the deobfuscated version of Minecraft Java Edition  
> Source code comes from [Hexeption/MCP-Reborn](https://github.com/Hexeption/MCP-Reborn/)  
> The original idea comes from the Pocket Edition Far Lands mod in my other project [MFSCelebrate/MCRe-NoiseFarlands](https://github.com/MFSCelebrate/MCRe-NoiseFarlands)

> [!TIP]
> This repository follows the [MIT License (MIT)](../LICENSE)  
> Author: [MFSCelebrate(Bilibili)](https://b23.tv/hTl7eI5)  
> Known collaborator: [\_\_Infinitive\_\_(BiliBili)](https://space.bilibili.com/1196843580)

> [!WARNING]
> This version is relatively unstable. Mojang officially introduced Vulkan rendering in this version, so all vanilla bugs (features) are unrelated to this mod.  
> Because this mod uses a special `version.json` to support peer-to-peer multiplayer, please download this file from the beta Release or GitHub Actions and place it in the same directory as the jar.

## Source Code Warning
For this project's Minecraft Java source code, you need to pay attention to the following:  
 1. This project fully uses Minecraft Java's source code, and Mojang has completely removed obfuscation starting from 26.1+, opening up Minecraft modifications.  
 2. However, the [Minecraft EULA](https://www.minecraft.net/en-us/eula) explicitly states the following:  
      - You may not "distribute" any of our (Mojang / Microsoft) works unless you have explicit permission. This includes:  
      - 1. Sharing copies (core clause): providing copies of the game software or content to anyone. This includes "distributing" the official game or any modified version of it. For example, sending a modified client to others.  
      - 2. Commercial use: using any content we create for commercial purposes.  
      - 3. Direct monetization: attempting to make money from any of our works.  
      - 4. Unfair access: allowing others to access our content in an unfair or unreasonable way.  
 
 Therefore, you must not sell for money, indirectly sell, mass-publicize this project's source code, publicly distribute this mod on a large scale, or commit any act that violates the Minecraft EULA.  
   
 3. As a foundation for individuals learning or developing mods, reading and modifying this code is behavior tacitly permitted or encouraged by the official rules, and does not constitute "cracking" or "unauthorized access" under the [Minecraft EULA](https://www.minecraft.net/en-us/eula).  
   
 4. This mod uses MCP-Reborn. Although its license allows the use of its decompiled output, what you can ultimately do with it must comply with Mojang's Minecraft EULA.  
   
 5. This mod's source code adds native libraries used by Minecraft, all of which are open-sourced in [Mojang](https://github.com/Mojang/) repositories.  
   
 6. This mod uses self-implemented 256-bit signed/unsigned integers, floating-point numbers, and mathematical calculation tools, and completely separates Minecraft's internal `version.json` into `ModMetadata`.

## Technical Preview
This mod implements some tools for exploring the Far Lands / distance phenomena by **directly modifying Minecraft's internals**.
  - 🛠 A more precise debug screen, using 256-bit signed floating-point numbers to calculate player coordinates
  - 🔗 Allows control over the world generator type, whether it is 32-bit / 64-bit Far Lands or Bedrock Edition Far Lands
  - ~~🤓 Even more badass settings~~

## Developing Based on This Repository
Because local building is too much of a hassle, you only need to fork this repository or upload the repository source code to your own repository, and it will build automatically.  
If you need to build locally, please install Java 25+ and Gradle 9.5.1+. You can refer to the following commands (assuming you have Java 25 installed):
```I_am_a_script.sh
git clone https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava.git

cd MCRe-NoiseFarlandsJava

./gradlew build
```
At this point, you can see the artifacts in `build/lib`. If you are using a GitHub repository, the artifacts and information will be automatically uploaded to GitHub Actions Workflows.  
**Contributing**: We welcome all contributions, including issue reports, feature suggestions, and code submissions. Before starting, please read the [Contributing Guide](./CONTRIBUTIN.en.md).

## Mod Compatibility
- **Supported version**: ![Minecraft 26.2](https://img.shields.io/badge/Minecraft-26.2-blue)
- **Compatibility**: Not compatible with multiplayer (but you can join the test server that the mod itself can enable)

## Nightly Builds
Want to try our latest changes / newest features? If we make code changes that day, we will upload them [here](https://github.com/MFSCelebrate/MCRe-NoiseFarlandsJava/releases/tag/nightly).

Nightly build files are all uploaded to **a single unified GitHub Release page**.

These versions contain the latest test changes, but also carry instability and save corruption risks.

## License
This project is open-sourced under the MIT License (MIT). See the [LICENSE](../LICENSE) file for details.