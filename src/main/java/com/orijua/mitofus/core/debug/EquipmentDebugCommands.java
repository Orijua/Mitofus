package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.orijua.mitofus.core.config.AppraisalBalance;
import com.orijua.mitofus.core.registry.ModDataComponents;
import com.orijua.mitofus.feature.equipment.Appraisal;
import com.orijua.mitofus.feature.equipment.AppraisalRules;
import com.orijua.mitofus.feature.equipment.AppraisalService;
import com.orijua.mitofus.feature.equipment.EquipmentService;
import com.orijua.mitofus.feature.progression.ProgressionService;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Appraisal of the rated equipment in the main hand: subcommands of {@code /mitofus} for operators ({@link DebugCommands} lists them all). */
public final class EquipmentDebugCommands {
    private EquipmentDebugCommands() {
    }

    /** The {@code /mitofus} subcommands of this topic (Appraisal of the rated equipment in the main hand); the caller makes them operator-only. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes(CommandBuildContext context) {
        return List.of(
                Commands.literal("appraisal")
                                .then(Commands.literal("unappraise")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ItemStack stack = ratedMainHand(player);
                                            AppraisalService.markUnappraised(stack, player.getRandom());
                                            ProgressionService.refreshEffects(player);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_appraisal_unappraised"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("reveal")
                                        .then(Commands.argument("n", IntegerArgumentType.integer(1, AppraisalBalance.MAX_DATA))
                                                .executes(ctx -> {
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                                    AppraisalRules.Step step = AppraisalService.revealNext(ratedMainHand(player), IntegerArgumentType.getInteger(ctx, "n"));
                                                    ProgressionService.refreshEffects(player);
                                                    ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_appraisal_revealed",
                                                            String.join(", ", step.newlyRevealed())), false);
                                                    return 1;
                                                })))
                                .then(Commands.literal("complete")
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            AppraisalService.appraiseFully(ratedMainHand(player));
                                            ProgressionService.refreshEffects(player);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_appraisal_completed"), false);
                                            return 1;
                                        }))
                                .then(Commands.literal("info")
                                        .executes(ctx -> {
                                            ItemStack stack = ratedMainHand(ctx.getSource().getPlayerOrException());
                                            Appraisal appraisal = stack.get(ModDataComponents.APPRAISAL);
                                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_appraisal_info",
                                                    appraisal == null ? "-" : Long.toString(appraisal.seed()),
                                                    appraisal == null ? "-" : String.join(", ", appraisal.revealed()),
                                                    String.join(", ", AppraisalService.keys(stack)),
                                                    Boolean.toString(!AppraisalService.isUnappraised(stack))), false);
                                            return 1;
                                        })));
    }

    /** The piece in the main hand; fails with {@code debug_appraisal_not_equipment} unless it is equipment with a rating. */
    private static ItemStack ratedMainHand(ServerPlayer player) throws CommandSyntaxException {
        ItemStack stack = player.getMainHandItem();
        if (!EquipmentService.isEquipment(stack) || !stack.has(ModDataComponents.ITEM_RATING)) {
            throw new SimpleCommandExceptionType(Component.translatable("message.mitofus.debug_appraisal_not_equipment")).create();
        }
        return stack;
    }
}
