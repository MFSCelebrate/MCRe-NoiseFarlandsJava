package net.MinecraftTools.commands;

import java.util.Map;

/**
 * 命令描述表（仅 {@code zh_cn}）。
 * <p>
 * 背景：Java 版 brigadier 命令树<b>不携带描述</b>（{@code LiteralCommandNode.getDescription()} 不存在），
 * 客户端补全面板此前只能用 {@code getSmartUsage(...)} 的用法摘要充当描述（形如 {@code "tp <x> <y> <z>"}）。
 * <p>
 * 本表按 Minecraft Wiki 的命令说明收录中文描述，让补全面板显示真正的人类可读介绍。
 * <p>
 * 语言策略：<b>只有 {@code zh_cn} 使用本表</b>；其它语言（en_us 等）一律回落到原版的「命令用法」显示，
 * 保证非中文玩家看到的仍是原版风格的参数提示，不会被中文描述污染。
 * <p>
 * 数据来源：Minecraft Wiki「命令」条目（Java 版），含调试命令
 * （{@code MC_DEBUG_DEV_COMMANDS} / {@code MC_DEBUG_CHASE_COMMAND} / {@code MC_DEBUG_ENABLED}）。
 */
public final class CommandDescriptions {

    /** 简体中文命令描述表（命令名 → 描述）。 */
    private static final Map<String, String> ZH_CN = Map.ofEntries(
            Map.entry("advancement", "添加、移除或查询玩家的进度。"),
            Map.entry("attribute", "修改或查询生物的属性。"),
            Map.entry("ban", "将玩家加入封禁列表。"),
            Map.entry("ban-ip", "将 IP 地址加入封禁列表。"),
            Map.entry("banlist", "显示封禁列表。"),
            Map.entry("bossbar", "修改或查询 Boss 栏。"),
            Map.entry("clear", "从玩家物品栏中删除物品。"),
            Map.entry("clone", "将特定区域的方块复制到另一处。"),
            Map.entry("compute", "在指定战利品上下文中对数值提供器求值。"),
            Map.entry("damage", "对实体造成伤害。"),
            Map.entry("data", "获取、合并、修改或移除方块实体、实体或命令存储的 NBT 数据。"),
            Map.entry("datapack", "控制或列出加载的数据包。"),
            Map.entry("debug", "开始或结束刻分析器或对函数进行调试。"),
            Map.entry("defaultgamemode", "更改默认的游戏模式。"),
            Map.entry("deop", "撤销玩家的管理员权限。"),
            Map.entry("dialog", "向玩家展示或清除对话框。"),
            Map.entry("difficulty", "设置难度等级。"),
            Map.entry("effect", "添加或移除状态效果。"),
            Map.entry("enchant", "为玩家当前所选物品添加魔咒。"),
            Map.entry("execute", "检测、改变命令上下文并执行命令。"),
            Map.entry("experience", "`/xp` 的替代命令，给予玩家经验。"),
            Map.entry("fetchprofile", "获取玩家档案。"),
            Map.entry("fill", "填充某个区域为特定方块。"),
            Map.entry("fillbiome", "将区域设置为指定生物群系。"),
            Map.entry("forceload", "强制加载区块或取消区块强制加载。"),
            Map.entry("function", "运行函数。"),
            Map.entry("gamemode", "更改玩家的游戏模式。"),
            Map.entry("gamerule", "更改或查询游戏规则。"),
            Map.entry("give", "给予玩家物品。"),
            Map.entry("help", "`/?` 的替代命令，用于查询命令语法。"),
            Map.entry("?", "`/help` 命令的别名，用于查询命令语法。"),
            Map.entry("item", "修改方块或实体物品栏内的物品。"),
            Map.entry("jfr", "开始或结束 JFR 分析。"),
            Map.entry("kick", "将玩家踢出服务器。"),
            Map.entry("kill", "清除实体。"),
            Map.entry("list", "列出服务器中的玩家。"),
            Map.entry("locate", "显示最近的给定结构、生物群系或兴趣点的坐标。"),
            Map.entry("loot", "将指定的战利品放入物品栏或生成在世界中。"),
            Map.entry("me", "显示一条关于命令执行者自己的信息。"),
            Map.entry("msg", "`/tell` 和 `/w` 的替代命令，向另一玩家发送私信。"),
            Map.entry("op", "授予玩家管理员权限。"),
            Map.entry("pardon", "解封玩家。"),
            Map.entry("pardon-ip", "从封禁列表中解封指定 IP。"),
            Map.entry("particle", "创建粒子。"),
            Map.entry("perf", "记录并保存性能分析数据（10 秒内）。"),
            Map.entry("place", "在满足条件的指定位置放置一个已配置的地物、拼图或结构。"),
            Map.entry("playsound", "播放音效。"),
            Map.entry("posteffect", "管理玩家使用的后处理效果。"),
            Map.entry("publish", "向局域网开放单人游戏世界。"),
            Map.entry("random", "生成随机值或控制随机序列。"),
            Map.entry("recipe", "给予或剥夺合成配方。"),
            Map.entry("reload", "从硬盘中重新加载数据包中的函数、进度等文件（不包括魔咒等需要重新进入世界加载的内容）。"),
            Map.entry("return", "在函数内跳过后续命令并更改函数返回值。"),
            Map.entry("ride", "控制一个实体的骑乘状态。"),
            Map.entry("rotate", "更改实体的旋转角度。"),
            Map.entry("save-all", "保存服务器世界状态到硬盘。"),
            Map.entry("save-off", "关闭服务器自动保存。"),
            Map.entry("save-on", "开启服务器自动保存。"),
            Map.entry("say", "向多个玩家发送消息。"),
            Map.entry("schedule", "安排函数在特定的游戏刻后运行。"),
            Map.entry("scoreboard", "管理记分板的记分项和分数持有者。"),
            Map.entry("seed", "显示世界种子。"),
            Map.entry("setblock", "将方块替换为其他方块。"),
            Map.entry("setidletimeout", "设置无操作玩家被踢出的延时。"),
            Map.entry("setworldspawn", "设置世界出生点。"),
            Map.entry("spawnpoint", "设置一个玩家的出生地点。"),
            Map.entry("spectate", "使一个处于旁观模式的玩家旁观一个实体。"),
            Map.entry("spreadplayers", "将实体传送到随机位置。"),
            Map.entry("stop", "关闭服务器。"),
            Map.entry("stopsound", "停止声音播放。"),
            Map.entry("stopwatch", "管理秒表。"),
            Map.entry("swing", "控制实体的手臂挥动。"),
            Map.entry("summon", "生成一个实体。"),
            Map.entry("tag", "修改实体的标签。"),
            Map.entry("team", "修改队伍。"),
            Map.entry("teammsg", "`/tm` 的替代命令，指定要发送给队伍的消息。"),
            Map.entry("teleport", "`/tp` 的替代命令，传送实体。"),
            Map.entry("tell", "`/msg` 和 `/w` 的替代命令，向另一个玩家发送私信。"),
            Map.entry("tellraw", "向玩家发送以文本组件表示的消息。"),
            Map.entry("test", "管理和运行游戏测试。"),
            Map.entry("tick", "停止或开始游戏刻的流逝，或更改游戏刻速率。"),
            Map.entry("time", "更改或查询游戏中的世界时间。"),
            Map.entry("title", "管理屏幕上的标题。"),
            Map.entry("tm", "`/teammsg` 命令的别名，指定要发送给队伍的消息。"),
            Map.entry("tp", "`/teleport` 命令的别名，传送实体。"),
            Map.entry("transfer", "将玩家转移到另一个服务器上。"),
            Map.entry("trigger", "设置一个触发器。"),
            Map.entry("unpublish", "停止将单人游戏世界开放到多人游戏。"),
            Map.entry("version", "输出游戏版本信息。"),
            Map.entry("w", "`/msg` 和 `/tell` 命令的别名，向另一玩家发送私信。"),
            Map.entry("waypoint", "列出或更改路径点。"),
            Map.entry("weather", "设置天气。"),
            Map.entry("whitelist", "管理服务器白名单。"),
            Map.entry("worldborder", "管理世界边界。"),
            Map.entry("xp", "`/experience` 命令的别名，增加或减少一个玩家的经验。"),
            Map.entry("warden_spawn_tracker", "设置幽匿尖啸体生成监守者之前应接收到多少次振动。"),
            Map.entry("debugpath", "计算并测试指定生物是否能够寻路到指定位置。"),
            Map.entry("debugmobspawning", "指定位置上进行一次生物的周期生成。"),
            Map.entry("raid", "管理执行者处的袭击。"),
            Map.entry("serverpack", "配置服务端指定资源包。"),
            Map.entry("spawn_armor_trims", "生成所有不同图案、材料和盔甲类型可能组合出的盔甲纹饰。"),
            Map.entry("debugconfig", "调试网络阶段的配置阶段。"),
            Map.entry("chase", "同步不同游戏实例的玩家的移动。"),
            Map.entry("environmentattribute", "获取服务端环境属性的值。"),
            // —— MCRe 自研命令 ——
            Map.entry("server_debug", "调试改版在服务端层的改动：启动或停止专用测试服务器。")
    );

    private CommandDescriptions() {
    }

    /**
     * 取指定语言环境的命令描述表。
     *
     * @param localeCode 语言代码（如 {@code zh_cn} / {@code en_us}），可为 {@code null}
     * @return 对应语言的描述表；该语言没有描述表时返回空表（调用方据此回落到用法摘要）
     */
    public static Map<String, String> forLocale(final String localeCode) {
        if (localeCode != null && localeCode.startsWith("zh_cn")) {
            return ZH_CN;
        }

        return Map.of();
    }
}
