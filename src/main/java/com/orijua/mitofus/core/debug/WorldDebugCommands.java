package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.orijua.mitofus.core.config.Balance;
import com.orijua.mitofus.core.config.VrHelmetBalance;
import com.orijua.mitofus.core.config.DeathBalance;
import com.orijua.mitofus.core.config.FatigueBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetRules;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetService;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetText;
import com.orijua.mitofus.feature.vrhelmet.VrHelmetTier;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeathRules;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeathService;
import com.orijua.mitofus.feature.vrhelmet.MitofusDeaths;
import com.orijua.mitofus.feature.vrhelmet.WorldSeparation;
import com.orijua.mitofus.feature.character.Characters;
import com.orijua.mitofus.feature.character.WorldSide;
import com.orijua.mitofus.feature.equipment.ItemRating;
import com.orijua.mitofus.feature.fatigue.FatigueService;
import com.orijua.mitofus.feature.mob.MobKilledEvent;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.mob.MobLevelService;
import com.orijua.mitofus.feature.npc.ApprenticeContractItem;
import com.orijua.mitofus.feature.npc.ApprenticeSmith;
import com.orijua.mitofus.feature.npc.NpcGrade;
import com.orijua.mitofus.feature.progression.ProgressionEvents;
import com.orijua.mitofus.feature.progression.ProgressionRules;
import com.orijua.mitofus.feature.smithing.ItemForgedEvent;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusOnly;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;

/** Death, fatigue, VR helmet, places, spawns and simulated events: subcommands of {@code /mitofus} for operators ({@link DebugCommands} lists them all). */
public final class WorldDebugCommands {
    private static final SimpleCommandExceptionType NOT_A_MOB =
            new SimpleCommandExceptionType(Component.translatable("message.mitofus.simulate_not_a_mob"));

    private WorldDebugCommands() {
    }

    /** The {@code /mitofus} subcommands of this topic (Death, fatigue, VR helmet, places, spawns and simulated events); the caller makes them operator-only. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes(CommandBuildContext context) {
        return List.of(
                Commands.literal("death")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    MitofusDeaths deaths = MitofusDeathService.deaths(player);
                                    long ago = Math.max(0L, MitofusDeathService.now(player.server) - deaths.lastDeathAt());
                                    Component last = deaths.lastDeathAt() <= 0
                                            ? Component.translatable("message.mitofus.debug_death_never")
                                            : Component.translatable("message.mitofus.debug_death_ago", ago / 20);
                                    Component pending = Component.translatable(deaths.pendingDisconnect()
                                            ? "message.mitofus.debug_terminal_offer_yes" : "message.mitofus.debug_terminal_offer_no");
                                    String lockLeft = MitofusDeathRules.formatRemaining(MitofusDeathService.lockRemaining(player));
                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_death", last,
                                            DeathBalance.current().doubleDeathWindowMinutes(), pending,
                                            DeathBalance.current().reconnectLockMinutes(), lockLeft), false);
                                    return 1;
                                })
                                .then(Commands.literal("reset")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            player.removeData(ModAttachments.MITOFUS_DEATHS.get());
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_death_reset"), false);
                                            return 1;
                                        })),
                Commands.literal("fatigue")
                                .then(Commands.literal("overlay")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            boolean on = FatigueService.toggleDebug(player);
                                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                                    on ? "message.mitofus.debug_fatigue_overlay_on" : "message.mitofus.debug_fatigue_overlay_off"), false);
                                            return 1;
                                        })),
                Commands.literal("spawn")
                                .then(Commands.argument("mob", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                        .then(Commands.literal("normal")
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> spawnLeveledMob(ctx, false))))
                                        .then(Commands.literal("elite")
                                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> spawnLeveledMob(ctx, true)))))
                                .then(Commands.literal("apprentice")
                                        .then(Commands.argument("grade", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(NpcGrade.values()).map(grade -> grade.name().toLowerCase()), builder))
                                                .executes(ctx -> spawnApprentice(ctx, -1))
                                                .then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
                                                        .executes(ctx -> spawnApprentice(ctx, IntegerArgumentType.getInteger(ctx, "level")))))),
                Commands.literal("goto")
                                .then(Commands.argument("site", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(SiteLanding.sites(), builder))
                                        .executes(WorldDebugCommands::gotoSite)),
                Commands.literal("vrhelmet")
                                .executes(ctx -> vrHelmetInfo(ctx.getSource()))
                                .then(Commands.literal("disconnect")
                                        .executes(ctx -> disconnectFromMitofus(ctx.getSource().getPlayerOrException())))
                                .then(Commands.literal("tier")
                                        .then(Commands.argument("tier", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(VrHelmetTier.values()).map(VrHelmetTier::getSerializedName), builder))
                                                .executes(WorldDebugCommands::setVrHelmetTier)))
                                .then(Commands.literal("rested")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    player.setData(ModAttachments.RESTED_SECONDS.get(), seconds);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_vrhelmet_rested", VrHelmetText.clock(seconds)), false);
                                                    return seconds;
                                                })))
                                .then(Commands.literal("cooldown")
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
                                                    player.setData(ModAttachments.RESTED_COOLDOWN.get(), seconds);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_vrhelmet_cooldown", VrHelmetText.clock(seconds)), false);
                                                    return seconds;
                                                }))),
                Commands.literal("simulate")
                                .then(Commands.literal("kill")
                                        .then(Commands.argument("entity", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                                                .executes(ctx -> simulateKills(ctx, 1))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> simulateKills(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
                                .then(Commands.literal("forge")
                                        .then(Commands.argument("rating", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(ItemRating.values()).map(ItemRating::getSerializedName), builder))
                                                .executes(ctx -> simulateForges(ctx, 1))
                                                .then(Commands.argument("amount", IntegerArgumentType.integer(1, 1000))
                                                        .executes(ctx -> simulateForges(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    /** The VR helmet tier of the player, what it gives (rested, session, recovery). */
    private static int vrHelmetInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        VrHelmetTier tier = player.getData(ModAttachments.VR_HELMET_TIER.get());
        FatigueBalance fatigue = FatigueBalance.current();
        VrHelmetBalance vrhelmet = VrHelmetBalance.current();
        source.sendSuccess(() -> Component.translatable("message.mitofus.debug_vrhelmet", tier.tierName(),
                VrHelmetText.clock(FatigueService.restedSeconds(player)),
                VrHelmetText.clock(VrHelmetRules.cooldownLeft(FatigueService.restedCooldown(player), vrhelmet)),
                VrHelmetText.minutes(vrhelmet.sessionMinutes(tier, fatigue)),
                VrHelmetText.minutes(vrhelmet.recoveryMinutes(tier, fatigue))), false);
        return 1;
    }

    private static int setVrHelmetTier(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "tier");
        Optional<VrHelmetTier> tier = Arrays.stream(VrHelmetTier.values()).filter(t -> t.getSerializedName().equals(name)).findFirst();
        if (tier.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_vrhelmet_bad_tier", name));
            return 0;
        }
        player.setData(ModAttachments.VR_HELMET_TIER.get(), tier.get());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_vrhelmet_tier", tier.get().tierName()), false);
        return 1;
    }

    private static int simulateKills(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        EntityType<?> type = ResourceArgument.getEntityType(ctx, "entity").value();
        if (!(type.create(player.serverLevel()) instanceof LivingEntity sample)) {
            throw NOT_A_MOB.create();
        }
        if (ProgressionEvents.kindOf(sample) == ProgressionEvents.KillKind.NO_XP) {
            throw new SimpleCommandExceptionType(
                    Component.translatable("message.mitofus.simulate_not_counted", type.getDescription())).create();
        }
        for (int i = 0; i < amount; i++) {
            // A fresh, never-spawned entity per kill, as listeners may keep or inspect the victim.
            NeoForge.EVENT_BUS.post(new MobKilledEvent(player, (LivingEntity) type.create(player.serverLevel())));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.simulated_kills", amount, type.getDescription()), false);
        return amount;
    }

    private static int simulateForges(CommandContext<CommandSourceStack> ctx, int amount) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        String name = StringArgumentType.getString(ctx, "rating");
        ItemRating rating = Arrays.stream(ItemRating.values()).filter(r -> r.getSerializedName().equals(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.simulate_bad_rating", name)).create());
        for (int i = 0; i < amount; i++) {
            NeoForge.EVENT_BUS.post(new ItemForgedEvent(player, new ItemStack(Items.IRON_SWORD), rating));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.simulated_forges", amount, rating.displayName()), false);
        return amount;
    }

    /**
     * Hires an apprentice of the given grade where the player stands, as a contract would (the smithing level still
     * limits how many), optionally with the smithing level he has learned ({@code level} &lt; 0: a new one's).
     */
    /** Spawns a mob two blocks in front of the player with the given level, elite or not (it has no random roll). Only in Mitofus, where levels exist. */
    private static int spawnLeveledMob(CommandContext<CommandSourceStack> ctx, boolean elite) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!MitofusWorld.is(player.level())) {
            ctx.getSource().sendFailure(Component.translatable(MitofusOnly.WARNING_KEY));
            return 0;
        }
        EntityType<?> type = ResourceArgument.getEntityType(ctx, "mob").value();
        if (!(type.create(player.serverLevel()) instanceof net.minecraft.world.entity.Mob mob)) {
            throw NOT_A_MOB.create();
        }
        int level = IntegerArgumentType.getInteger(ctx, "level");
        net.minecraft.world.phys.Vec3 at = player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(2));
        mob.moveTo(at.x, at.y, at.z, player.getYRot() + 180, 0);
        player.serverLevel().addFreshEntity(mob);
        MobLevelService.assign(mob, new MobLevel(level, elite), Balance.current());
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_spawn_mob", mob.getDisplayName(),
                Component.translatable(elite ? "message.mitofus.debug_spawn_elite" : "message.mitofus.debug_spawn_normal"), level), false);
        return 1;
    }

    private static int spawnApprentice(CommandContext<CommandSourceStack> ctx, int level) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "grade");
        NpcGrade grade = Arrays.stream(NpcGrade.values()).filter(g -> g.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_bad_grade", name)).create());
        ApprenticeSmith apprentice = ApprenticeContractItem.hire(player, player.blockPosition())
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_apprentice_refused")).create());
        apprentice.setGrade(grade);
        if (level >= 0) {
            apprentice.setSmithingXp(ProgressionRules.smithingXpFor(level));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_apprentice", apprentice.getDisplayName(),
                grade.name().toLowerCase(), apprentice.smithingLevel()), false);
        return 1;
    }

    /** Out of Mitofus and back beside the VR helmet, as the logout will (until it exists, the only way back). */
    private static int disconnectFromMitofus(ServerPlayer player) {
        boolean was = VrHelmetService.disconnect(player, VrHelmetService.Reason.DEBUG);
        if (!was) {
            player.displayClientMessage(Component.translatable("message.mitofus.vrhelmet.in_real_world"), false);
        }
        return was ? 1 : 0;
    }

    /** To the surface in front of one of the world's places, in the Mitofus dimension, facing it. */
    private static int gotoSite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String site = StringArgumentType.getString(ctx, "site");
        ServerLevel mitofus = MitofusWorld.of(ctx.getSource().getServer())
                .orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_goto_none", site)).create());
        SiteLanding.Landing found = SiteLanding.locate(mitofus, site);
        BlockPos landing = found.pos().orElseThrow(() -> new SimpleCommandExceptionType(Component.translatable(
                switch (found.reason()) {
                    case UNKNOWN -> "message.mitofus.debug_goto_unknown";
                    case SEA -> "message.mitofus.debug_goto_sea";
                    default -> "message.mitofus.debug_goto_none";
                }, site)).create());
        if (!Characters.canCross(player, WorldSide.MITOFUS)) {
            throw new SimpleCommandExceptionType(Component.translatable("message.mitofus.vrhelmet.character_unreadable")).create();
        }
        // Facing the place: the landing is in front of it.
        // A debug crossing between the real world and Mitofus is allowed (operators only).
        Characters.Result swap = WorldSeparation.cross(player, () ->
                player.teleportTo(mitofus, landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5, found.yaw(), 0.0f));
        if (swap == Characters.Result.FAILED) {
            // The player is there but still carries the other world's things; the next dimension change, respawn or login retries.
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.vrhelmet.character_unreadable"));
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_goto", site,
                landing.getX(), landing.getY(), landing.getZ()), false);
        return 1;
    }
}
