package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.economy.Money;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.progression.ClassService;
import com.orijua.mitofus.feature.progression.ClassSlotRules;
import com.orijua.mitofus.feature.progression.PlayerStats;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.progression.StatType;
import com.orijua.mitofus.feature.progression.XpRate;
import com.orijua.mitofus.feature.progression.XpRateRules;
import com.orijua.mitofus.feature.progression.XpRateService;
import com.orijua.mitofus.feature.progression.XpRateText;
import com.orijua.mitofus.feature.quest.PlayerQuests;
import com.orijua.mitofus.feature.quest.QuestCompletedEvent;
import com.orijua.mitofus.feature.quest.QuestDefinition;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusOnly;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import com.orijua.mitofus.feature.talent.TalentRules;
import com.orijua.mitofus.feature.talent.TalentService;

/** Level, stats, XP, classes, resets and quests: subcommands of {@code /mitofus} for operators ({@link DebugCommands} lists them all). */
public final class ProgressionDebugCommands {
    private static final SuggestionProvider<CommandSourceStack> STATS = (ctx, builder) -> SharedSuggestionProvider.suggest(
            Arrays.stream(StatType.values()).map(StatType::getSerializedName), builder);

    private ProgressionDebugCommands() {
    }

    /** The {@code /mitofus} subcommands of this topic (Level, stats, XP, classes, resets and quests); the caller makes them operator-only. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes(CommandBuildContext context) {
        return List.of(
                Commands.literal("set")
                                .then(Commands.literal("smithing")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
                                                .executes(ctx -> setSmithing(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
                                .then(Commands.literal("level")
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                                .executes(ctx -> setLevel(ctx, IntegerArgumentType.getInteger(ctx, "level")))))
                                .then(Commands.literal("class")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, ClassSlotRules.MAX_SLOTS - 1))
                                                .then(Commands.argument("class", StringArgumentType.word())
                                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                                Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.PLAYER_CLASS).stream()
                                                                        .map(entry -> entry.id().getPath()), builder))
                                                        .executes(ctx -> setClass(ctx, IntegerArgumentType.getInteger(ctx, "slot"),
                                                                StringArgumentType.getString(ctx, "class"))))))
                                .then(Commands.literal("classlevel")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, ClassSlotRules.MAX_SLOTS - 1))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10_000))
                                                        .executes(ctx -> setClassLevel(ctx, IntegerArgumentType.getInteger(ctx, "slot"),
                                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                                .then(Commands.literal("classlevel")
                                        .then(Commands.argument("class", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(placedClasses(ctx), builder))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 10_000))
                                                        .executes(ctx -> setClassLevelByName(ctx, StringArgumentType.getString(ctx, "class"),
                                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                                .then(Commands.literal("job")
                                        .then(Commands.argument("job", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.JOB).stream()
                                                                .map(entry -> entry.id().getPath()), builder))
                                                .executes(ctx -> setJob(ctx, StringArgumentType.getString(ctx, "job")))))
                                .then(Commands.literal("stat")
                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                .suggests(STATS)
                                                .then(Commands.argument("value", IntegerArgumentType.integer(0, 100_000))
                                                        .executes(ctx -> setStat(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))
                                .then(Commands.literal("fatigue")
                                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int percent = IntegerArgumentType.getInteger(ctx, "percent");
                                                    FatigueService.setEnergy(player, percent / 100.0);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_fatigue", percent), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("iron")
                                        .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    long amount = LongArgumentType.getLong(ctx, "amount");
                                                    player.setData(ModAttachments.PURSE.get(), new com.orijua.mitofus.feature.economy.Purse(amount));
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_iron", Money.format(amount)), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("view")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 10_000))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int value = IntegerArgumentType.getInteger(ctx, "value");
                                                    player.setData(ModAttachments.VIEW, value);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_view", value), false);
                                                    return 1;
                                                }))),
                Commands.literal("grade")
                                .then(Commands.literal("up")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, ClassSlotRules.MAX_SLOTS - 1))
                                                .executes(ctx -> gradeUp(ctx, IntegerArgumentType.getInteger(ctx, "slot"))))),
                Commands.literal("talent")
                                .then(Commands.literal("buyall")
                                        .then(Commands.argument("class", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(placedClasses(ctx), builder))
                                                .executes(ctx -> buyAllTalents(ctx, StringArgumentType.getString(ctx, "class")))))
                                .then(Commands.literal("buy")
                                        .then(Commands.argument("slot", IntegerArgumentType.integer(0, ClassSlotRules.MAX_SLOTS - 1))
                                                .then(Commands.argument("talent", ResourceLocationArgument.id())
                                                        .executes(ctx -> buyTalent(ctx, IntegerArgumentType.getInteger(ctx, "slot"),
                                                                ResourceLocationArgument.getId(ctx, "talent"))))))
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            int refunded = TalentService.refundAll(DebugSupport.mitofusPlayer(ctx));
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_talent_reset", refunded), false);
                                            return refunded;
                                        })),
                Commands.literal("xp")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    ProgressionRules.XpProgress progress = ProgressionRules.progress(Balance.current(), ProgressionService.get(player));
                                    XpRate rate = XpRateService.get(player);
                                    java.util.OptionalLong rawPerHour = XpRateRules.xpPerHour(rate);
                                    String perHour = rawPerHour.isPresent() ? String.valueOf(rawPerHour.getAsLong()) : "-";
                                    java.util.OptionalLong eta = XpRateText.etaMinutes(rate, progress.remaining());
                                    Component etaText = eta.isPresent() ? XpRateText.duration(eta.getAsLong()) : Component.literal("-");
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_xp",
                                            progress.level(), progress.xp(), progress.needed(), progress.remaining(), etaText, perHour,
                                            XpRateRules.coveredSeconds(rate), XpRateRules.WINDOW_MINUTES), false);
                                    return 1;
                                })
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            XpRateService.reset(ctx.getSource().getPlayerOrException());
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_xp_reset"), false);
                                            return 1;
                                        })),
                Commands.literal("give")
                                .then(Commands.literal("xp")
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    if (!MitofusWorld.is(player.level())) {
                                                        ctx.getSource().sendFailure(Component.translatable(MitofusOnly.WARNING_KEY));
                                                        return 0;
                                                    }
                                                    ProgressionService.awardXp(player, IntegerArgumentType.getInteger(ctx, "amount"));
                                                    return 1;
                                                })))
                                .then(Commands.literal("gear")
                                        .then(Commands.argument("stat", StringArgumentType.word())
                                                .suggests(STATS)
                                                .then(Commands.argument("requirement", IntegerArgumentType.integer(1, 100_000))
                                                        .executes(ctx -> giveGear(ctx, OptionalInt.empty()))
                                                        .then(Commands.argument("bonus", IntegerArgumentType.integer(1, 100_000))
                                                                .executes(ctx -> giveGear(ctx, OptionalInt.of(IntegerArgumentType.getInteger(ctx, "bonus")))))))),
                Commands.literal("reset")
                                .then(Commands.literal("all")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ModAttachments.resetAll(player);
                                            ProgressionService.set(player, PlayerStats.DEFAULT);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.reset"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("quests")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            player.setData(ModAttachments.PLAYER_QUESTS, PlayerQuests.EMPTY);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.quests_reset"), false);
                                            return 1;
                                        })),
                Commands.literal("quest")
                                .then(Commands.literal("complete")
                                        .then(Commands.argument("quest", ResourceLocationArgument.id())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                                                        Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.QUEST).stream()
                                                                .map(Definitions.Entry::id), builder))
                                                .executes(ProgressionDebugCommands::completeQuest))));
    }

    private static int setSmithing(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ProgressionService.set(player, ProgressionService.get(player).withSmithingXp(ProgressionRules.smithingXpFor(level)));
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_smithing", level,
                ProgressionService.effectiveSmithingLevel(player)), false);
        return level;
    }

    /** Puts any class (basic or hidden) in a slot whatever the player's level, for testing; it keeps the skills already learned. */
    private static int setClass(CommandContext<CommandSourceStack> ctx, int slot, String name) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ResourceLocation id = com.orijua.mitofus.Mitofus.id(name);
        if (!ClassService.forceClass(player, slot, id)) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_no_class", name));
            return 0;
        }
        return 1;
    }

    /** Sets the level of the class in a slot (its XP back to 0). */
    private static int setClassLevel(CommandContext<CommandSourceStack> ctx, int slot, int level) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        if (!ClassService.setClassLevel(player, slot, level)) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_no_class_slot", slot));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_class_level", slot, level), false);
        return level;
    }

    /** The classes the command's player has placed, as the names the commands take. */
    private static java.util.List<String> placedClasses(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) {
            return java.util.List.of();
        }
        return ClassService.classes(player).slots().stream().map(slot -> slot.classId().getPath()).toList();
    }

    /** Like {@code classlevel <slot>} but by class name, so the Profile's slot numbers (from 1) cannot be confused with the command's (from 0). */
    private static int setClassLevelByName(CommandContext<CommandSourceStack> ctx, String name, int level) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        var slot = ClassService.slotOf(player, com.orijua.mitofus.Mitofus.id(name));
        if (slot.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_class_not_placed", name));
            return 0;
        }
        return setClassLevel(ctx, slot.get(), level);
    }

    /** Raises the class level (enough points for the whole tree) and buys every node of its tree, for testing fusions. */
    private static int buyAllTalents(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        var slot = ClassService.slotOf(player, com.orijua.mitofus.Mitofus.id(name));
        if (slot.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_class_not_placed", name));
            return 0;
        }
        var all = TalentService.all(player.level());
        int bought = 0;
        boolean progress = true;
        while (progress) {
            progress = false;
            for (ResourceLocation node : all.keySet()) {
                var placed = ClassService.classes(player).slot(slot.get()).orElseThrow();
                var check = TalentRules.check(all, placed, node);
                if (check == TalentRules.Buy.POINTS) {
                    ClassService.setClassLevel(player, slot.get(), placed.classLevel() + all.get(node).cost());
                } else if (check == TalentRules.Buy.GRADE) {
                    player.setData(ModAttachments.PLAYER_CLASSES, ClassService.classes(player).withSlot(placed.withGrade(all.get(node).minGrade())));
                }
                if (TalentService.buy(player, slot.get(), node) == TalentRules.Buy.OK) {
                    bought++;
                    progress = true;
                }
            }
        }
        int total = bought;
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_talent_buyall", name, total), false);
        return total;
    }

    /** Buys a talent node for the class in a slot, through the same checks as the screen. */
    private static int buyTalent(CommandContext<CommandSourceStack> ctx, int slot, ResourceLocation node) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        if (TalentService.buy(player, slot, node) != TalentRules.Buy.OK) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_talent_failed", node.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_talent_bought", node.toString()), false);
        return 1;
    }

    /** Raises the grade of the class in a slot by one, if the class level and the grade requirements allow it. */
    private static int gradeUp(CommandContext<CommandSourceStack> ctx, int slot) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ClassSlotRules.Advance result = ClassService.advanceGrade(player, slot);
        String failure = switch (result) {
            case OK -> null;
            case NO_CLASS -> "message.mitofus.debug_grade_no_class";
            case MAX_GRADE -> "message.mitofus.debug_grade_max";
            case LEVEL_TOO_LOW -> "message.mitofus.debug_grade_level";
            case JOB_TOO_LOW -> "message.mitofus.debug_grade_job";
        };
        if (failure != null) {
            ctx.getSource().sendFailure(Component.translatable(failure, slot));
            return 0;
        }
        return 1;
    }

    /** Gives the player any job (basic or legendary) as a quest would, for testing. */
    private static int setJob(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        if (!ClassService.grantJob(player, com.orijua.mitofus.Mitofus.id(name))) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_no_job", name));
            return 0;
        }
        return 1;
    }

    /** Raising the level also brings the stat points those levels give, as levelling up would. */
    private static int setLevel(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        PlayerStats stats = ProgressionService.get(player);
        int gained = Math.max(0, level - stats.level()) * Balance.current().statPointsPerLevel();
        ProgressionService.set(player, stats.withProgress(level, 0, stats.freePoints() + gained));
        player.setHealth(player.getMaxHealth());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_level", level, gained), false);
        return level;
    }

    /** The stat named by the {@code stat} argument; fails with {@code debug_bad_stat} if there is none by that name. */
    private static StatType statArg(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String name = StringArgumentType.getString(ctx, "stat");
        return StatType.byName(name)
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_bad_stat", name)).create());
    }

    private static int setStat(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        StatType stat = statArg(ctx);
        ProgressionService.set(player, ProgressionService.get(player).with(stat, value));
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_stat", stat.displayName(), value), false);
        return value;
    }

    /** Gives a sword that requires points of a stat and, with a bonus, a chestplate that gives points of it. */
    private static int giveGear(CommandContext<CommandSourceStack> ctx, OptionalInt bonus) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!MitofusWorld.is(player.level())) {
            ctx.getSource().sendFailure(Component.translatable(MitofusOnly.WARNING_KEY));
            return 0;
        }
        StatType stat = statArg(ctx);
        int requirement = IntegerArgumentType.getInteger(ctx, "requirement");
        int given = DebugGear.give(player, stat, requirement, bonus);
        ctx.getSource().sendSuccess(() -> bonus.isPresent()
                ? Component.translatable("message.mitofus.debug_gear_bonus", stat.displayName(), requirement, bonus.getAsInt())
                : Component.translatable("message.mitofus.debug_gear", stat.displayName(), requirement), false);
        return given;
    }

    /**
     * Marks the quest done along with every quest before it in its chain ({@code requires_quest}), and tells the
     * listeners as if each had been claimed. Hidden ones count as discovered. Gives the item and XP rewards ({@link QuestService#giveRewards})
     * of the named quest only, and only if it was not done yet (so repeating the command does not duplicate them); the earlier quests of the
     * chain give nothing. It does not grant the class reward (it changes the class without the confirmation the window asks for) nor touch loans.
     */
    private static int completeQuest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "quest");
        List<ResourceLocation> chain = new ArrayList<>();
        Optional<ResourceLocation> next = Optional.of(id);
        while (next.isPresent() && !chain.contains(next.get())) {
            ResourceLocation current = next.get();
            QuestDefinition quest = Definitions.get(player.level(), ModDatapackRegistries.QUEST, current)
                    .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_bad_quest", current)).create());
            chain.addFirst(current);
            next = quest.requiresQuest();
        }
        PlayerQuests quests = QuestService.get(player);
        List<ResourceLocation> done = chain.stream().filter(quest -> !quests.isCompleted(quest)).toList();
        PlayerQuests updated = quests;
        for (ResourceLocation quest : done) {
            updated = updated.discover(quest).complete(quest);
        }
        player.setData(ModAttachments.PLAYER_QUESTS, updated);
        if (done.contains(id)) {
            QuestDefinition requested = Definitions.get(player.level(), ModDatapackRegistries.QUEST, id).orElseThrow();
            QuestService.giveRewards(player, requested);
        }
        done.forEach(quest -> NeoForge.EVENT_BUS.post(new QuestCompletedEvent(player, quest)));
        QuestService.checkDiscoveries(player);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_quest_complete", id.toString(), done.size()), false);
        return done.size();
    }
}
