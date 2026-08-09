package net.minecraft.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * MCRe NoiseFarlands：客户端本地命令执行器。
 * 主界面/未连接世界时，聊天框仍可打开并执行本地命令（/help、/quit 等）。
 * 命令注册在类加载时初始化，不依赖服务器连接，随时可用。
 */
@OnlyIn(Dist.CLIENT)
public final class LocalCommandExecutor {
    private static final CommandDispatcher<SharedSuggestionProvider> DISPATCHER = new CommandDispatcher<>();
    private static final SharedSuggestionProvider SOURCE = new LocalSource();
    private static final List<String> COMMAND_NAMES = List.of("help", "say", "quit", "server_debug");
    // MCRe: keep a reference to the local DedicatedServer instance (started via /server_debug start)
    private static volatile net.minecraft.server.dedicated.DedicatedServer runningServer = null;

    private LocalCommandExecutor() {
    }

    static {
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("help").executes(ctx -> {
                Minecraft minecraft = Minecraft.getInstance();
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("=== 本地命令（未进入世界）===").withStyle(ChatFormatting.GOLD)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/help — 显示此帮助").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/say <消息> — 在聊天框输出消息").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/quit — 退出游戏").withStyle(ChatFormatting.GRAY)
                );
                minecraft.gui.hud.getChat().addClientSystemMessage(
                    Component.literal("/server_debug <stop│start> <Options> — 用于调试改版在服务端层的改动").withStyle(ChatFormatting.GRAY)
                );
                return 1;
            })
        );
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("say")
                .then(
                    RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String message = StringArgumentType.getString(ctx, "message");
                            Minecraft minecraft = Minecraft.getInstance();
                            minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("§f" + message));
                            return 1;
                        })
                )
        );
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("quit").executes(ctx -> {
                Minecraft.getInstance().stop();
                return 1;
            })
        );
        DISPATCHER.register(
            LiteralArgumentBuilder.<SharedSuggestionProvider>literal("server_debug")
                .executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Usage: /server_debug <start|stop> [options]").withStyle(ChatFormatting.YELLOW));
                    return 1;
                })
                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("stop")
                    .executes(ctx -> {
                        Minecraft mc = Minecraft.getInstance();
                        net.minecraft.server.dedicated.DedicatedServer server = runningServer;
                        if (server != null) {
                            server.halt(true);
                            runningServer = null;
                            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Server stopped.").withStyle(ChatFormatting.GREEN));
                        } else {
                            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("Server not running.").withStyle(ChatFormatting.RED));
                        }
                        return 1;
                    })
                )
                .then(LiteralArgumentBuilder.<SharedSuggestionProvider>literal("start")
                    .then(RequiredArgumentBuilder.<SharedSuggestionProvider, String>argument("options", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String optStr = StringArgumentType.getString(ctx, "options");
                            java.util.List<String> args = new java.util.ArrayList<>();
                            if (!optStr.isBlank()) {
                                for (String token : optStr.split("\\s+")) {
                                    if (token.isEmpty()) continue;
                                    int eqIdx = token.indexOf('=');
                                    if (eqIdx > 0) {
                                        String key = token.substring(0, eqIdx);
                                        String value = token.substring(eqIdx + 1);
                                        args.add("--" + key);
                                        args.add(value);
                                    } else {
                                        args.add("--" + token);
                                    }
                                }
                            }
                            String[] argsArray = args.toArray(new String[0]);
                            try {
                                net.minecraft.server.Main.main(argsArray);
                                // Store reference set by Main after start
                                runningServer = net.minecraft.server.Main.getRunningServer();
                                Minecraft mc2 = Minecraft.getInstance();
                                mc2.gui.hud.getChat().addClientSystemMessage(Component.literal("Server started.").withStyle(ChatFormatting.GREEN));
                            } catch (Throwable t) {
                                Minecraft mc3 = Minecraft.getInstance();
                                mc3.gui.hud.getChat().addClientSystemMessage(Component.literal("Server start failed: " + t.getMessage()).withStyle(ChatFormatting.RED));
                                t.printStackTrace();
                            }
                            return 1;
                        })
                    )
                )
        );
    }

    public static CommandDispatcher<SharedSuggestionProvider> getDispatcher() {
        return DISPATCHER;
    }

    public static SharedSuggestionProvider getSource() {
        return SOURCE;
    }

    public static List<String> getCommandNames() {
        return COMMAND_NAMES;
    }

    /** 主界面/未连接时执行本地命令；未知命令显示红色提示 */
    public static void execute(final Minecraft minecraft, final String command) {
        try {
            ParseResults<SharedSuggestionProvider> parse = DISPATCHER.parse(new StringReader(command), SOURCE);
            DISPATCHER.execute(parse);
        } catch (CommandSyntaxException e) {
            minecraft.gui.hud.getChat().addClientSystemMessage(
                Component.literal("未知或不完整的命令：/" + command).withStyle(ChatFormatting.RED)
            );
        }
    }

    /** 最小 SharedSuggestionProvider 实现（主界面无连接/无世界数据） */
    private static final class LocalSource implements SharedSuggestionProvider {
        @Override
        public PermissionSet permissions() {
            return PermissionSet.NO_PERMISSIONS;
        }

        @Override
        public Collection<String> getOnlinePlayerNames() {
            return Collections.emptyList();
        }

        @Override
        public Collection<String> getAllTeams() {
            return Collections.emptyList();
        }

        @Override
        public Stream<Identifier> getAvailableSounds() {
            return Stream.empty();
        }

        @Override
        public CompletableFuture<Suggestions> customSuggestion(final CommandContext<?> context) {
            return Suggestions.empty();
        }

        @Override
        public Set<ResourceKey<Level>> levels() {
            return Collections.emptySet();
        }

        @Override
        public RegistryAccess registryAccess() {
            return RegistryAccess.EMPTY;
        }

        @Override
        public FeatureFlagSet enabledFeatures() {
            return FeatureFlagSet.of();
        }

        @Override
        public CompletableFuture<Suggestions> suggestRegistryElements(
            final ResourceKey<? extends Registry<?>> key,
            final SharedSuggestionProvider.ElementSuggestionType elements,
            final SuggestionsBuilder builder,
            final CommandContext<?> context
        ) {
            return builder.buildFuture();
        }
    }
}
