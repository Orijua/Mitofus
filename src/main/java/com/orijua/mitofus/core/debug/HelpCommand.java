package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /mitofus help}: one line per subcommand of {@code /mitofus} with what it is for. Everyone sees {@code logout} and
 * {@code help}; operators see all of them. The texts are {@code message.mitofus.help.<name>}.
 */
public final class HelpCommand {
    /** One subcommand: its name, how it is typed and whether it is operator-only. */
    record Entry(String name, String usage, boolean operator) {
    }

    /** Every subcommand of {@code /mitofus} (except the {@code debug} alias); a test keeps this list and the real commands together. */
    static final List<Entry> ENTRIES = List.of(
            new Entry("help", "help", false),
            new Entry("logout", "logout [force]", false),
            new Entry("validate", "validate", true),
            new Entry("set", "set smithing|level|class <slot>|classlevel <slot|class>|job|stat|fatigue|iron|view …", true),
            new Entry("grade", "grade up <slot> | set <class> <1-5>", true),
            new Entry("talent", "talent buy <slot> <talent> | buyall <class> | reset", true),
            new Entry("xp", "xp [reset]", true),
            new Entry("give", "give xp <n> | gear <stat> <requirement> [bonus]", true),
            new Entry("reset", "reset all|quests", true),
            new Entry("quest", "quest complete <quest>", true),
            new Entry("passive", "passive list|give|remove|clear [passive]", true),
            new Entry("skill", "skill learn <skill>", true),
            new Entry("mastery", "mastery list | <skill> level|uses|reset", true),
            new Entry("appraisal", "appraisal unappraise|reveal|complete|info", true),
            new Entry("terminal", "terminal [work reset | offer [reset]]", true),
            new Entry("purse", "purse", true),
            new Entry("trade", "trade [reset]", true),
            new Entry("death", "death [reset]", true),
            new Entry("fatigue", "fatigue overlay", true),
            new Entry("vrhelmet", "vrhelmet [disconnect | tier | rested | cooldown …]", true),
            new Entry("spawn", "spawn <mob> normal|elite <level> | apprentice <grade> [level]", true),
            new Entry("goto", "goto <place>", true),
            new Entry("simulate", "simulate kill|forge …", true));

    private HelpCommand() {
    }

    public static Set<String> names() {
        return ENTRIES.stream().map(Entry::name).collect(Collectors.toSet());
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("help").executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            boolean operator = DebugSupport.OPERATOR.test(source);
            source.sendSuccess(() -> Component.translatable("message.mitofus.help.header").withStyle(ChatFormatting.GOLD), false);
            int shown = 0;
            for (Entry entry : ENTRIES) {
                if (entry.operator() && !operator) {
                    continue;
                }
                source.sendSuccess(() -> Component.literal("/mitofus " + entry.usage()).withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(" — ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("message.mitofus.help." + entry.name())), false);
                shown++;
            }
            return shown;
        });
    }
}
