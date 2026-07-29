package net.minecraft.network.chat;

import com.google.common.collect.Lists;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.DataFixUtils;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

public class ComponentUtils {
    public static final String DEFAULT_SEPARATOR_TEXT = ", ";
    public static final Component DEFAULT_SEPARATOR = Component.literal(", ").withStyle(ChatFormatting.GRAY);
    public static final Component DEFAULT_NO_STYLE_SEPARATOR = Component.literal(", ");

    @CheckReturnValue
    public static MutableComponent mergeStyles(final MutableComponent component, final Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style inner = component.getStyle();
            if (inner.isEmpty()) {
                return component.setStyle(style);
            } else {
                return inner.equals(style) ? component : component.setStyle(inner.applyTo(style));
            }
        }
    }

    @CheckReturnValue
    public static Component mergeStyles(final Component component, final Style style) {
        if (style.isEmpty()) {
            return component;
        } else {
            Style inner = component.getStyle();
            if (inner.isEmpty()) {
                return component.copy().setStyle(style);
            } else {
                return inner.equals(style) ? component : component.copy().setStyle(inner.applyTo(style));
            }
        }
    }

    public static Optional<MutableComponent> resolve(final ResolutionContext context, final Optional<Component> component, final int recursionDepth) throws CommandSyntaxException {
        return component.isPresent() ? Optional.of(resolve(context, component.get(), recursionDepth)) : Optional.empty();
    }

    public static MutableComponent resolve(final ResolutionContext context, final Component component) throws CommandSyntaxException {
        return resolve(context, component, 0);
    }

    public static MutableComponent resolve(final ResolutionContext context, final Component component, final int recursionDepth) throws CommandSyntaxException {
        // $VF: Couldn't be decompiled
        // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
        // java.lang.OutOfMemoryError: Java heap space
        //   at java.base/java.util.HashMap.resize(HashMap.java:711)
        //   at java.base/java.util.HashMap.compute(HashMap.java:1307)
        //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.getNextFreeVersion(SFormsConstructor.java:502)
        //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SSAConstructorSparseEx.initParameter(SSAConstructorSparseEx.java:150)
        //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.createFirstMap(SFormsConstructor.java:433)
        //   at org.jetbrains.java.decompiler.modules.decompiler.sforms.SFormsConstructor.splitVariables(SFormsConstructor.java:87)
        //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:54)
        //   at org.jetbrains.java.decompiler.modules.decompiler.StackVarsProcessor.simplifyStackVars(StackVarsProcessor.java:43)
        //   at org.jetbrains.java.decompiler.main.rels.MethodProcessor.codeToJava(MethodProcessor.java:238)
        //
        // Bytecode:
        // 00: iload 2
        // 01: aload 0
        // 02: invokevirtual net/minecraft/network/chat/ResolutionContext.depthLimit ()I
        // 05: if_icmple 48
        // 08: getstatic net/minecraft/network/chat/ComponentUtils$1.$SwitchMap$net$minecraft$network$chat$ResolutionContext$LimitBehavior [I
        // 0b: aload 0
        // 0c: invokevirtual net/minecraft/network/chat/ResolutionContext.depthLimitBehavior ()Lnet/minecraft/network/chat/ResolutionContext$LimitBehavior;
        // 0f: invokevirtual net/minecraft/network/chat/ResolutionContext$LimitBehavior.ordinal ()I
        // 12: iaload
        // 13: lookupswitch 25 2 1 35 2 46
        // 2c: new java/lang/MatchException
        // 2f: dup
        // 30: aconst_null
        // 31: aconst_null
        // 32: invokespecial java/lang/MatchException.<init> (Ljava/lang/String;Ljava/lang/Throwable;)V
        // 35: athrow
        // 36: getstatic net/minecraft/network/chat/CommonComponents.ELLIPSIS Lnet/minecraft/network/chat/Component;
        // 39: invokeinterface net/minecraft/network/chat/Component.copy ()Lnet/minecraft/network/chat/MutableComponent; 1
        // 3e: goto 47
        // 41: aload 1
        // 42: invokeinterface net/minecraft/network/chat/Component.copy ()Lnet/minecraft/network/chat/MutableComponent; 1
        // 47: areturn
        // 48: aload 1
        // 49: invokeinterface net/minecraft/network/chat/Component.getContents ()Lnet/minecraft/network/chat/ComponentContents; 1
        // 4e: aload 0
        // 4f: iload 2
        // 50: bipush 1
        // 51: iadd
        // 52: invokeinterface net/minecraft/network/chat/ComponentContents.resolve (Lnet/minecraft/network/chat/ResolutionContext;I)Lnet/minecraft/network/chat/MutableComponent; 3
        // 57: astore 3
        // 58: aload 1
        // 59: invokeinterface net/minecraft/network/chat/Component.getSiblings ()Ljava/util/List; 1
        // 5e: invokeinterface java/util/List.iterator ()Ljava/util/Iterator; 1
        // 63: astore 4
        // 65: aload 4
        // 67: invokeinterface java/util/Iterator.hasNext ()Z 1
        // 6c: ifeq 8c
        // 6f: aload 4
        // 71: invokeinterface java/util/Iterator.next ()Ljava/lang/Object; 1
        // 76: checkcast net/minecraft/network/chat/Component
        // 79: astore 5
        // 7b: aload 3
        // 7c: aload 0
        // 7d: aload 5
        // 7f: iload 2
        // 80: bipush 1
        // 81: iadd
        // 82: invokestatic net/minecraft/network/chat/ComponentUtils.resolve (Lnet/minecraft/network/chat/ResolutionContext;Lnet/minecraft/network/chat/Component;I)Lnet/minecraft/network/chat/MutableComponent;
        // 85: invokevirtual net/minecraft/network/chat/MutableComponent.append (Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;
        // 88: pop
        // 89: goto 65
        // 8c: aload 3
        // 8d: aload 0
        // 8e: aload 1
        // 8f: invokeinterface net/minecraft/network/chat/Component.getStyle ()Lnet/minecraft/network/chat/Style; 1
        // 94: iload 2
        // 95: invokestatic net/minecraft/network/chat/ComponentUtils.resolveStyle (Lnet/minecraft/network/chat/ResolutionContext;Lnet/minecraft/network/chat/Style;I)Lnet/minecraft/network/chat/Style;
        // 98: invokevirtual net/minecraft/network/chat/MutableComponent.withStyle (Lnet/minecraft/network/chat/Style;)Lnet/minecraft/network/chat/MutableComponent;
        // 9b: areturn
    }

    private static Style resolveStyle(final ResolutionContext context, final Style style, final int recursionDepth) throws CommandSyntaxException {
        if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component text)) {
            HoverEvent resolved = new HoverEvent.ShowText(resolve(context, text, recursionDepth + 1));
            return style.withHoverEvent(resolved);
        } else {
            return style;
        }
    }

    public static Component formatList(final Collection<String> values) {
        return formatAndSortList(values, v -> Component.literal(v).withStyle(ChatFormatting.GREEN));
    }

    public static <T extends Comparable<T>> Component formatAndSortList(final Collection<T> values, final Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return CommonComponents.EMPTY;
        }

        if (values.size() == 1) {
            return formatter.apply(values.iterator().next());
        }

        List<T> sorted = Lists.newArrayList(values);
        sorted.sort(Comparable::compareTo);
        return formatList(sorted, formatter);
    }

    public static <T> Component formatList(final Collection<? extends T> values, final Function<T, Component> formatter) {
        return formatList(values, DEFAULT_SEPARATOR, formatter);
    }

    public static <T> MutableComponent formatList(
        final Collection<? extends T> values, final Optional<? extends Component> separator, final Function<T, Component> formatter
    ) {
        return formatList(values, DataFixUtils.orElse(separator, DEFAULT_SEPARATOR), formatter);
    }

    public static Component formatList(final Collection<? extends Component> values, final Component separator) {
        return formatList(values, separator, Function.identity());
    }

    public static <T> MutableComponent formatList(final Collection<? extends T> values, final Component separator, final Function<T, Component> formatter) {
        if (values.isEmpty()) {
            return Component.empty();
        }

        if (values.size() == 1) {
            return formatter.apply((T)values.iterator().next()).copy();
        }

        MutableComponent result = Component.empty();
        boolean first = true;

        for (T value : values) {
            if (!first) {
                result.append(separator);
            }

            result.append(formatter.apply(value));
            first = false;
        }

        return result;
    }

    public static MutableComponent wrapInSquareBrackets(final Component inner) {
        return Component.translatable("chat.square_brackets", inner);
    }

    public static Component fromMessage(final Message message) {
        return message instanceof Component component ? component : Component.literal(message.getString());
    }

    public static boolean isTranslationResolvable(final @Nullable Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            String fallback = translatable.getFallback();
            return fallback != null || Language.getInstance().has(key);
        } else {
            return true;
        }
    }

    public static MutableComponent copyOnClickText(final String text) {
        return wrapInSquareBrackets(
            Component.literal(text)
                .withStyle(
                    s -> s.withColor(ChatFormatting.GREEN)
                        .withClickEvent(new ClickEvent.CopyToClipboard(text))
                        .withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click")))
                        .withInsertion(text)
                )
        );
    }
}