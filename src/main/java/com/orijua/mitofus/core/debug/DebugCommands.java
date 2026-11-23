package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Test helpers under /mitofus (op only; the rest of /mitofus, like logout, is open to everyone), so titles and quests can be tried without grinding:
 * <ul>
 *   <li>{@code /mitofus simulate kill <entity> [amount]}: counts kills as if they happened.</li>
 *   <li>{@code /mitofus simulate forge <rating> [amount]}: counts forges of that rating.</li>
 *   <li>{@code /mitofus reset quests}: forgets accepted, completed and discovered quests.</li>
 *   <li>{@code /mitofus give xp <amount>}: awards XP; {@code /mitofus reset all} resets every system.</li>
 *   <li>{@code /mitofus give gear <stat> <requirement> [bonus]}: gives a sword that requires points of a stat and, with a bonus, a chestplate that gives them.</li>
 *   <li>Standard: {@code /mitofus <verb> <what> [class] [value]} with the verbs set, give, remove, reset and list; classes by name,
 *   slots from 1 (see mod-tools/CLAUDE.md in the docs).</li>
 *   <li>{@code /mitofus appraisal unappraise|reveal <n>|complete|info}: on the rated equipment in the main hand: marks it
 *   unappraised, reveals n data without spending an Eye, appraises it in full, or shows its seed, revealed data and all its data.</li>
 *   <li>{@code /mitofus set smithing <level>}: sets the smithing level (its experience).</li>
 *   <li>{@code /mitofus set level <level>}: sets the level; levels gained bring their stat points.</li>
 *   <li>{@code /mitofus set stat <stat> <value>}: sets the points spent in a stat.</li>
 *   <li>{@code /mitofus set view <value>}: sets View.</li>
 *   <li>{@code /mitofus set iron <amount>}: sets the iron of the purse.</li>
 *   <li>{@code /mitofus purse}: shows the coins of the purse.</li>
 *   <li>{@code /mitofus trade}: lists the pressure and the price percentage of each trade key; {@code /mitofus reset trade} clears them.</li>
 *   <li>{@code /mitofus xp}: shows level, XP, XP missing and the measured XP per hour; {@code /mitofus reset xp} clears the measurement.
 *   {@code simulate kill} refuses mobs that give no XP (villagers and the mod's peaceful NPCs).</li>
 *   <li>{@code /mitofus spawn <mob> <elite|normal> <level>}: spawns that mob two blocks in front of the player with that level, elite or normal; only in Mitofus.</li>
 *   <li>{@code /mitofus spawn apprentice <grade> [level]}: hires an apprentice of that grade where the player stands.</li>
 *   <li>{@code /mitofus goto <place>}: to the surface in front of a structure (covadonga, altamira, torre_del_oro,
 *   eibar) or on dry ground at a place of the map (sierra_nevada...); refused in the sea.</li>
 *   <li>{@code /mitofus give quest <quest>}: marks it and the quests before it in its chain done; only the named
 *   quest, and only if it was not done yet, gives its item and XP rewards (not its class reward nor its loans; the ones before it give nothing).</li>
 * </ul>
 * Every subcommand is operator-only (permission level 2) except {@code help} and {@code logout}; {@code /mitofus <subcommand>} still works
 * as an alias of {@code /mitofus <subcommand>}. The commands live in the topic classes ({@link ProgressionDebugCommands},
 * {@link EconomyDebugCommands}, {@link EquipmentDebugCommands}, {@link WorldDebugCommands}, {@link SkillDebugCommands}); {@link HelpCommand} lists them.
 * Simulations post the same domain events as real play, so every listener (counters, titles,
 * hidden quests, notifications) reacts the same way. No mob dies and no item is created, except the quest rewards of {@code quest complete} and the test equipment of {@code give gear}.
 */
public final class DebugCommands {
    private DebugCommands() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(DebugCommands::onRegisterCommands);
    }

    /** Every operator subcommand of {@code /mitofus}, from all the topics. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> operatorNodes(net.minecraft.commands.CommandBuildContext context) {
        List<LiteralArgumentBuilder<CommandSourceStack>> nodes = new ArrayList<>();
        nodes.addAll(ProgressionDebugCommands.nodes(context));
        nodes.addAll(EconomyDebugCommands.nodes(context));
        nodes.addAll(EquipmentDebugCommands.nodes(context));
        nodes.addAll(WorldDebugCommands.nodes(context));
        nodes.addAll(SkillDebugCommands.nodes());
        return nodes;
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> mitofus = Commands.literal("mitofus");
        for (LiteralArgumentBuilder<CommandSourceStack> node : operatorNodes(event.getBuildContext())) {
            mitofus.then(node.requires(DebugSupport.OPERATOR));
        }
        mitofus.then(HelpCommand.node());
        LiteralCommandNode<CommandSourceStack> registered = event.getDispatcher().register(mitofus);
        // The old spelling, /mitofus <subcommand>, goes to the same place.
        event.getDispatcher().register(Commands.literal("mitofus")
                .then(Commands.literal("debug").requires(DebugSupport.OPERATOR).redirect(registered)));
    }
}
