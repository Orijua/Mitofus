package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.effect.ActiveTraits;
import com.orijua.mitofus.feature.effect.PassiveDefinition;
import com.orijua.mitofus.feature.effect.PassiveOverrides;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.skill.MasteryRules;
import com.orijua.mitofus.feature.skill.MasteryText;
import com.orijua.mitofus.feature.skill.SkillMastery;
import com.orijua.mitofus.feature.skill.SkillService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator shortcuts to test class passives, skills and their mastery without playing for hours. They join the
 * {@code /mitofus} root through {@link DebugCommands}:
 * <ul>
 *   <li>{@code /mitofus passive list}: the active passives and where each one comes from (class, debug, skill).</li>
 *   <li>{@code /mitofus passive give|remove <passive>}: adds a passive whatever the class and level, or hides one
 *   the class gives; {@code clear} undoes both. Cleared too by {@code reset all}.</li>
 *   <li>{@code /mitofus skill learn <skill>}: teaches a skill (it takes the first free slot).</li>
 *   <li>{@code /mitofus mastery list}: mastery level, uses and uses to the next level of each learned skill.</li>
 *   <li>{@code /mitofus mastery <skill> level <1-20>|uses <n>|reset}: sets the mastery of a skill.</li>
 * </ul>
 */
public final class SkillDebugCommands {
    private static final SuggestionProvider<CommandSourceStack> PASSIVES = (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.PASSIVE).stream().map(Definitions.Entry::id), builder);
    private static final SuggestionProvider<CommandSourceStack> SKILLS = (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.SKILL).stream().map(Definitions.Entry::id), builder);

    private SkillDebugCommands() {
    }

    /** {@code give passive|skill}, {@code remove}, {@code list}, {@code reset passives} and {@code mastery}, to be hung under {@code /mitofus} (operators only). */
    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                Commands.literal("give")
                        .then(Commands.literal("passive")
                                .then(Commands.argument("passive", ResourceLocationArgument.id()).suggests(PASSIVES)
                                        .executes(ctx -> changePassive(ctx, true))))
                        .then(Commands.literal("skill")
                                .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILLS)
                                        .executes(SkillDebugCommands::learnSkill))),
                Commands.literal("remove")
                        .then(Commands.literal("passive")
                                .then(Commands.argument("passive", ResourceLocationArgument.id()).suggests(PASSIVES)
                                        .executes(ctx -> changePassive(ctx, false)))),
                Commands.literal("list")
                        .then(Commands.literal("passives").executes(SkillDebugCommands::listPassives)),
                Commands.literal("reset")
                        .then(Commands.literal("passives").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.setData(ModAttachments.PASSIVE_OVERRIDES, PassiveOverrides.EMPTY);
                            ProgressionService.refreshEffects(player);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_passive_cleared"), false);
                            return 1;
                        })),
                Commands.literal("mastery")
                        .then(Commands.literal("list").executes(SkillDebugCommands::listMastery))
                        .then(Commands.argument("skill", ResourceLocationArgument.id()).suggests(SKILLS)
                                .then(Commands.literal("level")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, MasteryRules.MAX_LEVEL))
                                                .executes(ctx -> setMastery(ctx, MasteryRules.usesToReach(IntegerArgumentType.getInteger(ctx, "level"))))))
                                .then(Commands.literal("uses")
                                        .then(Commands.argument("uses", IntegerArgumentType.integer(0, 1_000_000))
                                                .executes(ctx -> setMastery(ctx, IntegerArgumentType.getInteger(ctx, "uses")))))
                                .then(Commands.literal("reset").executes(ctx -> setMastery(ctx, 0)))));
    }

    private static ServerPlayer mitofusPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return DebugSupport.mitofusPlayer(ctx);
    }

    private static int listPassives(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        var active = ActiveTraits.active(player);
        if (active.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_passive_none"), false);
            return 0;
        }
        PassiveOverrides overrides = player.getData(ModAttachments.PASSIVE_OVERRIDES);
        for (PassiveDefinition passive : active) {
            ResourceLocation id = idOf(player, passive);
            String source = overrides.granted().contains(id) ? "debug" : "class";
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_passive_line",
                    passive.name(), String.valueOf(id), Component.translatable("message.mitofus.debug_passive_from." + source)), false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_passive_total", active.size(),
                String.format("%.0f", ActiveTraits.total(player, com.orijua.mitofus.feature.effect.BonusType.MAX_MANA))), false);
        return active.size();
    }

    private static ResourceLocation idOf(ServerPlayer player, PassiveDefinition passive) {
        return Definitions.all(player.level(), ModDatapackRegistries.PASSIVE).stream()
                .filter(entry -> entry.value() == passive || entry.value().equals(passive))
                .map(Definitions.Entry::id).findFirst().orElse(Mitofus.id("unknown"));
    }

    private static int changePassive(CommandContext<CommandSourceStack> ctx, boolean give) throws CommandSyntaxException {
        ServerPlayer player = mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "passive");
        if (Definitions.get(player.level(), ModDatapackRegistries.PASSIVE, id).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_passive_unknown", id.toString()));
            return 0;
        }
        PassiveOverrides before = player.getData(ModAttachments.PASSIVE_OVERRIDES);
        player.setData(ModAttachments.PASSIVE_OVERRIDES, give ? before.give(id) : before.remove(id));
        ProgressionService.refreshEffects(player);
        ctx.getSource().sendSuccess(() -> Component.translatable(give ? "message.mitofus.debug_passive_given" : "message.mitofus.debug_passive_removed", id.toString()), false);
        return 1;
    }

    private static int learnSkill(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "skill");
        if (!SkillService.learn(player, id)) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_skill_not_learned", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_skill_learned", id.toString()), false);
        return 1;
    }

    private static int listMastery(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SkillMastery mastery = player.getData(ModAttachments.SKILL_MASTERY);
        var learned = SkillService.get(player).learned();
        if (learned.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_mastery_none"), false);
            return 0;
        }
        for (ResourceLocation id : learned) {
            int level = mastery.level(id);
            int toNext = level >= MasteryRules.MAX_LEVEL ? 0 : MasteryRules.usesToReach(level + 1) - mastery.uses(id);
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_mastery_line", id.toString(),
                    MasteryText.full(level), level, mastery.uses(id), toNext), false);
        }
        return learned.size();
    }

    private static int setMastery(CommandContext<CommandSourceStack> ctx, int uses) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "skill");
        if (Definitions.get(player.level(), ModDatapackRegistries.SKILL, id).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_skill_unknown", id.toString()));
            return 0;
        }
        Map<ResourceLocation, Integer> updated = new HashMap<>(player.getData(ModAttachments.SKILL_MASTERY).uses());
        if (uses == 0) {
            updated.remove(id);
        } else {
            updated.put(id, uses);
        }
        player.setData(ModAttachments.SKILL_MASTERY, new SkillMastery(Map.copyOf(updated)));
        int level = MasteryRules.level(uses);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_mastery_set", id.toString(),
                MasteryText.full(level), level, uses), false);
        if (!SkillService.get(player).knows(id)) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_mastery_not_learned", id.toString()), false);
        }
        return level;
    }
}
