package net.minecraft.server.jsonrpc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

public record MethodInfo<Params, Result>(String description, Optional<ParamInfo<Params>> params, Optional<ResultInfo<Result>> result) {
   public MethodInfo(final String description, final @Nullable ParamInfo<Params> paramInfo, final @Nullable ResultInfo<Result> resultInfo) {
      this(description, Optional.ofNullable(paramInfo), Optional.ofNullable(resultInfo));
   }

   // ===== 修改：lambda 显式类型，替代方法引用 =====
   private static <Params> Optional<ParamInfo<Params>> toOptional(final List<ParamInfo<Params>> list) {
      return list.isEmpty() ? Optional.empty() : Optional.of(list.getFirst());
   }

   private static <Params> List<ParamInfo<Params>> toList(final Optional<ParamInfo<Params>> opt) {
      return opt.isPresent() ? List.of(opt.get()) : List.of();
   }

   private static <Params> Codec<Optional<ParamInfo<Params>>> paramsTypedCodec() {
      return ParamInfo.<Params>typedCodec().codec().listOf().<Optional<ParamInfo<Params>>>xmap(
         (List<ParamInfo<Params>> list) -> MethodInfo.<Params>toOptional(list),
         (Optional<ParamInfo<Params>> opt) -> MethodInfo.<Params>toList(opt)
      );
   }

   // ===== 修改：显式指定 RecordCodecBuilder 类型参数 =====
   private static <Params, Result> MapCodec<MethodInfo<Params, Result>> typedCodec() {
      return RecordCodecBuilder.<MethodInfo<Params, Result>>mapCodec(
         i -> RecordCodecBuilder.<MethodInfo<Params, Result>>group(
               Codec.STRING.fieldOf("description").forGetter(MethodInfo::description),
               MethodInfo.<Params>paramsTypedCodec().fieldOf("params").forGetter(MethodInfo::params),
               ResultInfo.<Result>typedCodec().optionalFieldOf("result").forGetter(MethodInfo::result)
            )
            .apply(i, MethodInfo::new)
      );
   }

   public MethodInfo.Named<Params, Result> named(final Identifier name) {
      return new MethodInfo.Named<>(name, this);
   }

   public record Named<Params, Result>(Identifier name, MethodInfo<Params, Result> contents) {
      // ===== 修改：显式指定 Codec 类型 =====
      public static final Codec<MethodInfo.Named<?, ?>> CODEC = MethodInfo.Named.<Object, Object>typedCodec();

      public static <Params, Result> Codec<MethodInfo.Named<Params, Result>> typedCodec() {
         return RecordCodecBuilder.<MethodInfo.Named<Params, Result>>create(
            i -> RecordCodecBuilder.<MethodInfo.Named<Params, Result>>group(
                  Identifier.CODEC.fieldOf("name").forGetter(MethodInfo.Named::name),
                  MethodInfo.<Params, Result>typedCodec().forGetter(MethodInfo.Named::contents)
               )
               .apply(i, MethodInfo.Named::new)
         );
      }
   }
}