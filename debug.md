🚀 分批完成！灰飞烟灭的 191 个错误已经按依赖链切好 6 大战役，大佬！🔥

---

📦 分批结果（按文件路径聚合）

批次 文件数 典型错误
Batch 1 – Codec / 泛型地狱 ~55 个 RecordCodecBuilder 泛型推断失败、MapCodec 类型参数缺失、validate 方法引用、xmap 转换、forGetter 方法引用、group 参数数量不匹配、ImmutableList/Map 构建器类型推断
Batch 2 – 实体 AI Brain 协变返回 24 个 getBrain() 返回 Brain<CAP#1> 无法转换为 Brain<具体实体>（全部是实体类 + 部分 AI 助手类）
Batch 3 – 值提供者 / 密度函数 0 个 ✅ 已全部修复，不再报错
Batch 4 – 注册表 / 数据加载器 5 个 RegistryDataLoader、BuiltInRegistries、HolderSetCodec、RegistrySetBuilder、ResourceKey 泛型
Batch 5 – 网络协议 / Packet 3 个 ClientboundUpdateEnabledFeaturesPacket、LastSeenMessages、ArgumentSignatures 构造函数歧义 + readCollection 类型推断
Batch 6 – 杂项 / 客户端 / 渲染 / DataFixer ~104 个 客户端渲染、GUI、模型、数据生成、命令执行、DataFixer 泛型、变量重复定义、异常变量缺失、instanceof 类型安全等

---

🗂️ Batch 1 – Codec 泛型地狱（55 个文件）

这些文件全部需要 显式类型参数 + lambda 替代方法引用：

```
net/minecraft/network/codec/StreamCodec.java
net/minecraft/network/syncher/EntityDataSerializer.java
net/minecraft/world/level/block/Block.java
net/minecraft/core/component/TypedDataComponent.java
net/minecraft/core/component/DataComponentType.java
net/minecraft/core/Registry.java
net/minecraft/core/component/DataComponentPatch.java
net/minecraft/locale/Language.java
net/minecraft/world/attribute/EnvironmentAttributeMap.java
net/minecraft/network/protocol/game/ClientboundGameRuleValuesPacket.java
net/minecraft/server/packs/metadata/MetadataSectionType.java
net/minecraft/util/ExtraCodecs.java
net/minecraft/world/item/crafting/RecipeMap.java
net/minecraft/world/level/block/entity/TestInstanceBlockEntity.java
net/minecraft/world/level/biome/Climate.java
net/minecraft/world/attribute/modifier/AttributeModifier.java
net/minecraft/server/level/ChunkTaskPriorityQueue.java
net/minecraft/util/CubicSpline.java
net/minecraft/network/protocol/common/custom/CustomPacketPayload.java
net/minecraft/server/dedicated/Settings.java
net/minecraft/server/jsonrpc/methods/GameRulesService.java
net/minecraft/server/jsonrpc/api/MethodInfo.java
net/minecraft/server/jsonrpc/api/Schema.java
net/minecraft/server/jsonrpc/methods/DiscoveryService.java
net/minecraft/world/level/gamerules/GameRuleMap.java
net/minecraft/world/entity/EntityReference.java
net/minecraft/world/entity/ai/Brain.java
net/minecraft/world/level/dimension/end/EnderDragonFight.java
net/minecraft/advancements/predicates/MinMaxBounds.java
net/minecraft/core/RegistrySetBuilder.java
net/minecraft/core/component/predicates/DataComponentPredicate.java
net/minecraft/util/EncoderCache.java
net/minecraft/world/item/AdventureModePredicate.java
net/minecraft/world/entity/variant/PriorityProvider.java
net/minecraft/resources/RegistryDataLoader.java (Comparator 部分)
net/minecraft/core/registries/BuiltInRegistries.java
net/minecraft/network/syncher/EntityDataSerializers.java
net/minecraft/advancements/predicates/entity/PlayerPredicate.java
net/minecraft/server/dialog/action/StaticAction.java
net/minecraft/client/gui/font/providers/BitmapProvider.java
net/minecraft/client/renderer/item/properties/select/ComponentContents.java
net/minecraft/client/renderer/block/dispatch/VariantSelector.java
net/minecraft/client/data/models/blockstates/PropertyValueList.java
net/minecraft/world/level/storage/loot/functions/SetCustomModelDataFunction.java
net/minecraft/world/level/storage/loot/predicates/EnvironmentAttributeCheck.java
net/minecraft/world/level/storage/loot/predicates/LootItemBlockStatePropertyCondition.java
net/minecraft/world/attribute/modifier/ColorModifier.java
net/minecraft/world/attribute/modifier/IntegerModifier.java
net/minecraft/world/attribute/modifier/FloatModifier.java
net/minecraft/util/datafix/fixes/OptionsKeyTranslationFix.java
net/minecraft/util/datafix/fixes/LeavesFix.java
net/minecraft/util/datafix/fixes/BlockEntityUUIDFix.java
net/minecraft/util/datafix/fixes/ChunkProtoTickListFix.java
net/minecraft/util/datafix/fixes/OptionsKeyLwjgl3Fix.java
net/minecraft/util/datafix/fixes/EntitySpawnerItemVariantComponentFix.java
net/minecraft/util/SortedArraySet.java
net/minecraft/util/HashOps.java
net/minecraft/data/info/RegistryDumpReport.java
net/minecraft/util/AbstractListBuilder.java
net/minecraft/Util.java (makeEnumMap)
```

🗂️ Batch 4 – 注册表 / 数据加载器（5 个）

```
net/minecraft/resources/RegistryDataLoader.java (ERROR_KEY_COMPARATOR 部分)
net/minecraft/core/registries/BuiltInRegistries.java
net/minecraft/resources/HolderSetCodec.java
net/minecraft/core/RegistrySetBuilder.java
net/minecraft/server/jsonrpc/methods/GameRulesService.java (GameRuleUpdate 部分)
```

---

🗂️ Batch 5 – 网络协议 / Packet（3 个）

```
net/minecraft/network/protocol/configuration/ClientboundUpdateEnabledFeaturesPacket.java
net/minecraft/network/chat/LastSeenMessages.java
net/minecraft/commands/arguments/ArgumentSignatures.java
```

---

🗂️ Batch 6 – 杂项 / 客户端 / 渲染 / DataFixer（104 个）

包括：

· 客户端 GUI / 渲染：Minecraft.java, EntityRenderDispatcher.java, FontManager.java, PeriodicNotificationManager.java, GuiSpriteScaling.java, ModelFeatureRenderer.java, ChatComponent.java, OptionInstance.java, WorldCreationUiState.java, CreateWorldScreen.java, LevelEventHandler.java, ModelBakery.java, BlockStateModelDispatcher.java, WaypointStyle.java, BookViewScreen.java, PackSelectionScreen.java, AbstractGameRulesScreen.java, PresetFlatWorldScreen.java, ConditionalItemModel.java, AddressCheck.java, ModelTemplate.java, BlockModelGenerators.java
· 命令执行：BuildContexts.java, SuggestionProviders.java, ArgumentTypeInfos.java
· DataFixer 异常：ChunkBedBlockEntityInjecterFix.java, FileFixerUpper.java
· 其他：BaseEntityBlock.java, SpawnPlacements.java, MultifaceGrowthFeature.java, HoneycombItem.java, PlaceholderLookupProvider.java, LightEngine.java, ServerPlayer.java, ServerGamePacketListenerImpl.java, PiercingWeapon.java, KineticWeapon.java 等

---

🛠️ 执行方案

大佬，这批文件数量庞大，建议按以下顺序推进：

1. 先 Batch 1（55 个 Codec 文件）——修完编译错误会骤减 60%
2. 再 Batch 2（24 个 Brain 文件）——全部是机械性强制转换，5 分钟搞定
3. 然后 Batch 4 + 5（8 个注册表/网络文件）
4. 最后 Batch 6（104 个杂项）——拆成 3 轮子分批处理

---

大佬，你想从哪个 Batch 开始？ 直接把该批次的所有完整文件打包发我，我按文件逐个修好发回，标注 // ===== 修改：=====。冲它丫的！💥

