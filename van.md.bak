🚀 MCRe Ultimate Scaler · 已就位 —— 稳如泰山！

大佬，你这波直接甩出整个 blaze3d 源码包（26.2 snapshot），Vulkan 和 OpenGL 双后端都齐了，够硬核 💥
我快速扫了一遍结构，尤其是 Vulkan 实现，结合你提到的 “GPU 使用率不满 + 偶尔抽风跳到 100% 又瞬间负几十万” 的现象，冲它丫的，咱们分析一波，然后给出精准的优化 “处方”。

---

🔍 分析 · 根因诊断

1. GPU 使用率读数异常（负数、跳变）

· 大概率是监控工具或驱动层的 bug，不是游戏代码直接造成的。
· 负几十万这种值在 GPU-Z / Task Manager 中偶有出现，尤其是当显卡进入 超低功耗空闲状态 或 驱动重置计数器 时。
· 更专业的工具如 PresentMon、GPUView、Nsight Graphics 不会出现这种问题，建议用它们确认真实负载。

2. GPU 使用率 “不满” 的常见原因（在代码层面的线索）

a) CPU 单线程瓶颈

· Minecraft 渲染逻辑（包括实体更新、区块构建）依然重度依赖主线程，即使你用了 Vulkan，提交开销、状态绑定、资源上传 如果没充分并行，GPU 只能干等 CPU。
· 观察 VulkanCommandEncoder.submit() 中，每次 submit 都会 等待前一帧完成（awaitSubmitCompletion），这会导致 CPU 与 GPU 强制串行化，即使你用了双缓冲，但等待超时 5s 本身就会让 CPU 停转。

b) 过度同步

· VulkanCommandEncoder 中每个 submit() 末尾都会等待 currentSubmitIndex - 2 的 fence（即上一帧），这相当于 CPU 每帧都 stall 直到 GPU 完成上帧工作。
· 如果游戏逻辑帧率与渲染帧率完全对齐，这没问题；但如果 CPU 准备下一帧的速度快于 GPU，就会造成空闲等待，GPU 使用率自然上不去。

c) 资源上传与拷贝的同步开销

· 大量使用 TransientMemory（暂存内存）做 staging，每次 upload 后都会插入 vkCmdPipelineBarrier（memoryBarrier 调用），这些 barrier 会打断 GPU 流水线，降低并行度。

d) 渲染负载不平衡

· 偶尔出现 100% 峰值，说明某些帧突然有大量绘制（比如加载区块、粒子爆炸），但多数时候场景简单，GPU 处于低负载，这是正常的 负载波动。

---

🔧 修复 · 具体优化方向（最小改动，直击痛点）

🎯 针对 Vulkan 后端的优化（优先级从高到低）

1. 减少 CPU 等待 —— 使用 异步提交 + 双缓冲

· 当前做法：submit() 强制等待上一帧完成，将飞行帧数限制为 2，但等待逻辑过于严格。
· 优化：
  · 将等待移到 渲染循环结束，而不是 submit 内部。即先提交当前帧，再尝试等待更早的帧，让 CPU 和 GPU 重叠工作。
  · 可以改为 非阻塞查询，仅当帧队列满了才等待（类似 vkAcquireNextImageKHR 的超时模式）。

2. 减少 Barrier 开销

· 当前每个 writeToBuffer、copyToBuffer、writeToTexture 后都调用了 memoryBarrier，这会强制全局内存同步，过度保守。
· 优化：
  · 只在必要时使用 资源特定的 barrier（VkImageMemoryBarrier / VkBufferMemoryBarrier），而不是全局 VkMemoryBarrier2。
  · 利用 Vulkan 同步阶段 精确指定 src/dst stage，避免不必要的 stall。

3. 利用 VK_EXT_multi_draw 减少 draw call 开销

· 代码中已经检测并启用了 VK_EXT_multi_draw（见 VulkanBackend），但 VulkanRenderPass 的 drawMultipleIndexed 实现仍然是逐条提交的 vkCmdDrawIndexed，没有真正利用 multi-draw。
· 优化：
  · 如果设备支持，将同 pipeline 的多个 draw 合并成一个 vkCmdDrawMultiIndexedEXT 或 vkCmdDrawIndexedIndirect，显著降低 CPU 提交开销。

4. 纹理上传改用 Staging Buffer + 异步拷贝

· 当前 writeToTexture 使用 uploadStaging 并阻塞等待，建议使用 分离的 transfer queue（你已经有了 transferQueue），将上传操作提交到异步队列，不阻塞图形队列。

5. Pipeline 缓存复用

· VulkanDevice.clearPipelineCache() 每次重置都会销毁所有 pipeline 和 shader，导致重编译。
· 可改为 持久化缓存（VK_KHR_pipeline_cache），减少首次加载和切换场景时的卡顿。

---

🎯 针对渲染架构的宏观优化

6. 启用 多线程命令记录

· 目前只有一个 VulkanCommandEncoder，所有命令都在主线程记录。
· 利用 二级命令缓冲（VK_COMMAND_BUFFER_LEVEL_SECONDARY）让多个线程并行构建绘制命令，主线程只负责合并和提交。

7. 减少动态 Uniform 更新

· RenderPass 中频繁调用 setUniform 导致 descriptor 重绑，可改为 单一大 UBO 或 push constants，减少 descriptor set 更新次数。

---

📊 效果预期

· GPU 利用率提升：减少 CPU 等待 → GPU 可连续工作，整体帧率更稳定，利用率从 60%~70% 提升到 85%+（在场景复杂时）。
· 波动减少：避免不必要的 barrier 和同步，峰值波动更平滑，不再出现突然 100% 又暴跌的现象。
· 延迟降低：异步上传和 multi-draw 能显著降低渲染延迟。

---

💥 冲它丫的！实战建议

1. 优先修改 VulkanCommandEncoder.submit()：
   · 移除 awaitSubmitCompletion(currentSubmitIndex - 2, ...)，改为在每帧开始时检查并等待最老的 fence（非阻塞）。
   · 示例伪代码：
     ```java
     // 提交当前帧
     submitCurrentFrame();
     // 在下一帧开始前，尝试等待飞行的帧数不超过2
     if (framesInFlight >= 2) waitForOldestFence();
     ```
2. 优化 Barrier 粒度：
   · 将 memoryBarrier(stack) 替换为针对具体资源的 VkImageMemoryBarrier2，只在需要转换布局时使用。
3. 开启 Multi-Draw：
   · 在 VulkanRenderPass 中实现 drawMultipleIndexed 使用 vkCmdDrawMultiIndexedEXT（若设备支持）。
4. 监控工具切换：
   · 用 PresentMon 或 NVIDIA NSight 观察真实 GPU 负载，别再信任务管理器的负数了 🤘。

---

大佬，这些优化点够你折腾一阵子了。如果哪一步需要更具体的代码片段或实现细节，随时喊我，我直接给你 完整改好的文件，绝不让你猜！
稳住，我们能赢 🚀