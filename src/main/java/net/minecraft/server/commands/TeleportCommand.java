package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class TeleportCommand {
   private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
      Component.translatable("commands.teleport.invalidPosition")
   );

   public static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
      LiteralCommandNode<CommandSourceStack> teleport = dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("teleport")
                     .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
                  .then(
                     Commands.argument("location", Vec3Argument.vec3())
                        .executes(
                           c -> teleportToPos(
                              (CommandSourceStack)c.getSource(),
                              Collections.singleton(((CommandSourceStack)c.getSource()).getEntityOrException()),
                              ((CommandSourceStack)c.getSource()).getLevel(),
                              Vec3Argument.getCoordinates(c, "location"),
                              null,
                              null
                           )
                        )
                  ))
               .then(
                  Commands.argument("destination", EntityArgument.entity())
                     .executes(
                        c -> teleportToEntity(
                           (CommandSourceStack)c.getSource(),
                           Collections.singleton(((CommandSourceStack)c.getSource()).getEntityOrException()),
                           EntityArgument.getEntity(c, "destination")
                        )
                     )
               ))
            .then(
               ((RequiredArgumentBuilder)Commands.argument("targets", EntityArgument.entities())
                     .then(
                        ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("location", Vec3Argument.vec3())
                                 .executes(
                                    c -> teleportToPos(
                                       (CommandSourceStack)c.getSource(),
                                       EntityArgument.getEntities(c, "targets"),
                                       ((CommandSourceStack)c.getSource()).getLevel(),
                                       Vec3Argument.getCoordinates(c, "location"),
                                       null,
                                       null
                                    )
                                 ))
                              .then(
                                 Commands.argument("rotation", RotationArgument.rotation())
                                    .executes(
                                       c -> teleportToPos(
                                          (CommandSourceStack)c.getSource(),
                                          EntityArgument.getEntities(c, "targets"),
                                          ((CommandSourceStack)c.getSource()).getLevel(),
                                          Vec3Argument.getCoordinates(c, "location"),
                                          RotationArgument.getRotation(c, "rotation"),
                                          null
                                       )
                                    )
                              ))
                           .then(
                              ((LiteralArgumentBuilder)Commands.literal("facing")
                                    .then(
                                       Commands.literal("entity")
                                          .then(
                                             ((RequiredArgumentBuilder)Commands.argument("facingEntity", EntityArgument.entity())
                                                   .executes(
                                                      c -> teleportToPos(
                                                         (CommandSourceStack)c.getSource(),
                                                         EntityArgument.getEntities(c, "targets"),
                                                         ((CommandSourceStack)c.getSource()).getLevel(),
                                                         Vec3Argument.getCoordinates(c, "location"),
                                                         null,
                                                         new LookAt.LookAtEntity(EntityArgument.getEntity(c, "facingEntity"), EntityAnchorArgument.Anchor.FEET)
                                                      )
                                                   ))
                                                .then(
                                                   Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                      .executes(
                                                         c -> teleportToPos(
                                                            (CommandSourceStack)c.getSource(),
                                                            EntityArgument.getEntities(c, "targets"),
                                                            ((CommandSourceStack)c.getSource()).getLevel(),
                                                            Vec3Argument.getCoordinates(c, "location"),
                                                            null,
                                                            new LookAt.LookAtEntity(
                                                               EntityArgument.getEntity(c, "facingEntity"), EntityAnchorArgument.getAnchor(c, "facingAnchor")
                                                            )
                                                         )
                                                      )
                                                )
                                          )
                                    ))
                                 .then(
                                    Commands.argument("facingLocation", Vec3Argument.vec3())
                                       .executes(
                                          c -> teleportToPos(
                                             (CommandSourceStack)c.getSource(),
                                             EntityArgument.getEntities(c, "targets"),
                                             ((CommandSourceStack)c.getSource()).getLevel(),
                                             Vec3Argument.getCoordinates(c, "location"),
                                             null,
                                             new LookAt.LookAtPosition(Vec3Argument.getVec3(c, "facingLocation"))
                                          )
                                       )
                                 )
                           )
                     ))
                  .then(
                     Commands.argument("destination", EntityArgument.entity())
                        .executes(
                           c -> teleportToEntity(
                              (CommandSourceStack)c.getSource(), EntityArgument.getEntities(c, "targets"), EntityArgument.getEntity(c, "destination")
                           )
                        )
                  )
            )
            // 🔧 MCRe（大佬 10-15 派活）：/tp real <x> <y> <z>——支持科学记数法的无限精度传送
            // ⚠️ 必须是 teleport 的顶层子命令（不能嵌套在 targets 里面，否则 "real" 会被当作实体选择器解析）
            .then(
               Commands.literal("real")
                  .then(
                     Commands.argument("x", StringArgumentType.word())
                        .then(Commands.argument("y", StringArgumentType.word())
                           .then(Commands.argument("z", StringArgumentType.word())
                              .executes(ctx -> realTeleport(
                                 StringArgumentType.getString(ctx, "x"),
                                 StringArgumentType.getString(ctx, "y"),
                                 StringArgumentType.getString(ctx, "z"),
                                 (CommandSourceStack)ctx.getSource()
                              ))
                           )
                        )
                  )
            )
      );
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("tp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
            .redirect(teleport)
      );
   }

   /**
    * 🔧 MCRe（大佬 10-15 派活）：/tp real <x> <y> <z>——支持科学记数法的无限精度传送。
    * 坐标 ≤ 2^31 - 3647 时正常传送；超过时设置世界偏移并触发世界重载。
    */
   private static int realTeleport(final String xs, final String ys, final String zs,
         final CommandSourceStack source) throws CommandSyntaxException {
      java.math.BigDecimal x;
      java.math.BigDecimal z;
      int y;
      try {
         x = new java.math.BigDecimal(xs);
         z = new java.math.BigDecimal(zs);
         y = new java.math.BigDecimal(ys).intValueExact();
      } catch (ArithmeticException | NumberFormatException e) {
         source.sendFailure(Component.literal("§c坐标解析失败: " + e.getMessage()));
         return 0;
      }

      // ── 判断是否超出 int 窗口（2^31 - 3647 = 2147480000）──
      java.math.BigInteger intLimit = java.math.BigInteger.valueOf(2147483647L - 3647L);
      boolean inWindow = x.toBigInteger().abs().compareTo(intLimit) <= 0
          && z.toBigInteger().abs().compareTo(intLimit) <= 0;

      if (inWindow) {
         // ── 正常传送（转为 double，与原版 /tp 行为一致）──
         double dx = x.doubleValue();
         double dz = z.doubleValue();
         Entity entity = source.getEntityOrException();
         if (entity instanceof net.minecraft.server.level.ServerPlayer sp) {
            sp.teleportTo(dx, y, dz);
         } else {
            entity.absSnapTo(dx, y, dz);
         }
         source.sendSuccess(() -> Component.literal(
             "§a已传送到 (" + x.toPlainString() + ", " + y + ", " + z.toPlainString() + ")"), false);
         return 1;
      }

      // ── 超出 int 窗口 → 设置世界偏移 + 触发世界重载 ──
      return triggerWorldRelocation(source, x, z, y);
   }

   /**
    * 🔧 MCRe：超出 int 窗口的世界重定位——重建世界（非重开）。
    * 流程：读旧世界配置 → 更新偏移 → 断开（保存） → 删除旧世界 → createFreshLevel（偏移已生效）
    * 玩家在本地 (0, y, 0) 出生 = 真实坐标 (targetX, y, targetZ)
    */
   private static int triggerWorldRelocation(final CommandSourceStack source,
         final java.math.BigDecimal x, final java.math.BigDecimal z, final int y) {
      net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
      net.minecraft.server.MinecraftServer server = source.getServer();

      // 1. 获取 configData
      net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen.FarLandsConfigData config =
          net.minecraft.client.gui.screens.worldselection.WorldMainSettingScreen.FarLandsConfigData.activeConfig;
      if (config == null) {
         source.sendFailure(Component.literal("§c无法获取 FarLands 配置"));
         return 0;
      }

      // 2. 更新偏移配置
      config.enabledTerrainOffsets = true;
      config.xWorldOffset = x.toPlainString();
      config.yWorldOffset = String.valueOf(y);
      config.zWorldOffset = z.toPlainString();

      // 3. 保存配置到 farlands_config.json
      net.minecraft.client.gui.screens.worldselection.FarLandsConfigStorage.save(
          mc.gameDirectory, config);

      // 3.5 调用 WorldReposition.refresh() 让 SCALE/SHIFT 静态数组立即生效
      net.MinecraftTools.Math.DynamicAccuracy.BigDecimal _one =
          net.MinecraftTools.Math.DynamicAccuracy.BigDecimal.ONE;
      net.MinecraftTools.Math.DynamicAccuracy.BigDecimal _zero =
          net.MinecraftTools.Math.DynamicAccuracy.BigDecimal.ZERO;
      config.enabledTerrainOffsets = true;
      net.minecraft.world.level.levelgen.WorldReposition.refresh(
          new net.minecraft.world.level.levelgen.WorldReposition.RepositionConfig(
          config.enabledTerrainScaler ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.xWorldScaler, _one) : _one,
          config.enabledTerrainScaler ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.yWorldScaler, _one) : _one,
          config.enabledTerrainScaler ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.zWorldScaler, _one) : _one,
          config.enabledTerrainOffsets ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.xWorldOffset, _zero) : _zero,
          config.enabledTerrainOffsets ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.yWorldOffset, _zero) : _zero,
          config.enabledTerrainOffsets ? net.minecraft.world.level.levelgen.WorldReposition.parseOrFallback(config.zWorldOffset, _zero) : _zero,
          config.enabledYClampedGradientOffset,
          config.surfaceNoiseOffset
      ));

      // 4. 获取 levelId
      String levelId = server.getStorageSource().getLevelId();

      // 5. 通知玩家
      source.sendSuccess(() -> Component.literal(
          "§e目标超出 int 窗口，正在重建世界...\n"
          + "§7偏移: X=" + x.toPlainString() + "  Z=" + z.toPlainString() + "\n"
          + "§7旧世界数据将被清除，新世界将在目标位置生成地形"), false);

      // 6. 在客户端线程调度完整的世界重建流程
      final net.minecraft.server.MinecraftServer srv = server;
      mc.execute(() -> {
         try {
            // ── Step A: 读取旧世界配置（在断开前，服务端还活着时）──
            var flows = mc.createWorldOpenFlows();
            var access = mc.getLevelSource().validateAndCreateAccess(levelId);
            var old = flows.recreateWorldData(access);
            var levelSettings = old.getFirst();       // 游戏模式/难度/游戏规则等
            var creationContext = old.getSecond();    // 种子/维度/数据包等
            access.close();

            // ── Step B: 断开世界（保存 → halt server → 清除 level，阻塞直到完成）──
            mc.disconnectWithSavingScreen();
            // 此时服务端已停止，level 目录已解锁

            // ── Step C: 删除旧世界目录（彻底消去旧地形）──
            try (var delAccess = mc.getLevelSource().validateAndCreateAccess(levelId)) {
               delAccess.deleteLevel();
            }

            // ── Step D: 用旧配置 + 新偏移创建全新世界 ──
            // WorldReposition 已刷新 → 噪声生成器在目标位置采样
            // 玩家在本地 (0, y, 0) 出生 = 真实 (targetX, y, targetZ)
            flows.createFreshLevel(levelId, levelSettings, creationContext.options(),
                provider -> creationContext.selectedDimensions(), null);

            System.out.println("[MCRe] /tp real world recreated: levelId=" + levelId
                + " offset=(" + config.xWorldOffset + "," + config.zWorldOffset + ")");
         } catch (Exception e) {
            System.err.println("[MCRe] World relocation failed: " + e.getMessage());
            e.printStackTrace();
         }
      });

      return 1;
   }

   private static int teleportToEntity(final CommandSourceStack source, final Collection<? extends Entity> entities, final Entity destination) throws CommandSyntaxException {
      for (Entity entity : entities) {
         performTeleport(
            source,
            entity,
            (ServerLevel)destination.level(),
            destination.getX(),
            destination.getY(),
            destination.getZ(),
            EnumSet.noneOf(Relative.class),
            destination.getYRot(),
            destination.getXRot(),
            null
         );
      }

      if (entities.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable("commands.teleport.success.entity.single", entities.iterator().next().getDisplayName(), destination.getDisplayName()),
            true
         );
      } else {
         source.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.multiple", entities.size(), destination.getDisplayName()), true);
      }

      return entities.size();
   }

   private static int teleportToPos(
      final CommandSourceStack source,
      final Collection<? extends Entity> entities,
      final ServerLevel level,
      final Coordinates destination,
      final @Nullable Coordinates rotation,
      final @Nullable LookAt lookAt
   ) throws CommandSyntaxException {
      Vec3 pos = destination.getPosition(source);
      Vec2 rot = rotation == null ? null : rotation.getRotation(source);

      for (Entity entity : entities) {
         Set<Relative> relatives = getRelatives(destination, rotation, entity.level().dimension() == level.dimension());
         if (rot == null) {
            performTeleport(source, entity, level, pos.x, pos.y, pos.z, relatives, entity.getYRot(), entity.getXRot(), lookAt);
         } else {
            performTeleport(source, entity, level, pos.x, pos.y, pos.z, relatives, rot.y, rot.x, lookAt);
         }
      }

      if (entities.size() == 1) {
         source.sendSuccess(
            () -> Component.translatable(
               "commands.teleport.success.location.single",
               entities.iterator().next().getDisplayName(),
               formatDouble(pos.x),
               formatDouble(pos.y),
               formatDouble(pos.z)
            ),
            true
         );
      } else {
         source.sendSuccess(
            () -> Component.translatable(
               "commands.teleport.success.location.multiple", entities.size(), formatDouble(pos.x), formatDouble(pos.y), formatDouble(pos.z)
            ),
            true
         );
      }

      return entities.size();
   }

   private static Set<Relative> getRelatives(final Coordinates destination, final @Nullable Coordinates rotation, final boolean sameDimension) {
      Set<Relative> dir = Relative.direction(destination.isXRelative(), destination.isYRelative(), destination.isZRelative());
      Set<Relative> pos = sameDimension ? Relative.position(destination.isXRelative(), destination.isYRelative(), destination.isZRelative()) : Set.of();
      Set<Relative> rot = rotation == null ? Relative.ROTATION : Relative.rotation(rotation.isYRelative(), rotation.isXRelative());
      return Relative.union(dir, pos, rot);
   }

   private static String formatDouble(final double value) {
      return String.format(Locale.ROOT, "%f", value);
   }

   private static void performTeleport(
      final CommandSourceStack source,
      final Entity victim,
      final ServerLevel level,
      final double x,
      final double y,
      final double z,
      final Set<Relative> relatives,
      final float yRot,
      final float xRot,
      final @Nullable LookAt lookAt
   ) throws CommandSyntaxException {
      double relativeOrAbsoluteX = relatives.contains(Relative.X) ? x - victim.getX() : x;
      double relativeOrAbsoluteY = relatives.contains(Relative.Y) ? y - victim.getY() : y;
      double relativeOrAbsoluteZ = relatives.contains(Relative.Z) ? z - victim.getZ() : z;
      float relativeOrAbsoluteYRot = relatives.contains(Relative.Y_ROT) ? yRot - victim.getYRot() : yRot;
      float relativeOrAbsoluteXRot = relatives.contains(Relative.X_ROT) ? xRot - victim.getXRot() : xRot;
      float newYRot = Mth.wrapDegrees(relativeOrAbsoluteYRot);
      float newXRot = Mth.wrapDegrees(relativeOrAbsoluteXRot);
      if (victim.teleportTo(level, relativeOrAbsoluteX, relativeOrAbsoluteY, relativeOrAbsoluteZ, relatives, newYRot, newXRot, true)) {
         if (lookAt != null) {
            lookAt.perform(source, victim);
         }

         if (!(victim instanceof LivingEntity living && living.isFallFlying())) {
            victim.setDeltaMovement(victim.getDeltaMovement().multiply(1.0, 0.0, 1.0));
            victim.setOnGround(true);
         }

         if (victim instanceof PathfinderMob mob) {
            mob.getNavigation().stop();
         }
      }
   }
}
