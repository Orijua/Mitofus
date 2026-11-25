package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.orijua.mitofus.core.registry.Definitions;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.core.registry.ModDatapackRegistries;
import com.orijua.mitofus.feature.form.FormCatalogue;
import com.orijua.mitofus.feature.form.FormRules;
import com.orijua.mitofus.feature.form.FormService;
import com.orijua.mitofus.feature.form.PlayerForms;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Operator commands for forms: {@code set form|formlevel}, {@code give form}, {@code remove form}, {@code list forms}, {@code reset forms}. */
final class FormDebugCommands {
    private static final SuggestionProvider<CommandSourceStack> FORMS = (ctx, builder) -> SharedSuggestionProvider.suggestResource(
            Definitions.all(ctx.getSource().getLevel(), ModDatapackRegistries.FORM).stream().map(Definitions.Entry::id), builder);

    private FormDebugCommands() {
    }

    static List<LiteralArgumentBuilder<CommandSourceStack>> nodes() {
        return List.of(
                Commands.literal("set")
                        .then(Commands.literal("form")
                                .then(Commands.argument("form", ResourceLocationArgument.id()).suggests(FORMS)
                                        .executes(FormDebugCommands::setForm)))
                        .then(Commands.literal("formlevel")
                                .then(Commands.argument("form", ResourceLocationArgument.id()).suggests(FORMS)
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, FormRules.MAX_LEVEL))
                                                .executes(FormDebugCommands::setFormLevel)))),
                Commands.literal("give")
                        .then(Commands.literal("form")
                                .then(Commands.argument("form", ResourceLocationArgument.id()).suggests(FORMS)
                                        .executes(FormDebugCommands::giveForm))),
                Commands.literal("remove")
                        .then(Commands.literal("form").executes(FormDebugCommands::removeForm)),
                Commands.literal("list")
                        .then(Commands.literal("forms").executes(FormDebugCommands::listForms)),
                Commands.literal("reset")
                        .then(Commands.literal("forms").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.setData(ModAttachments.PLAYER_FORMS, PlayerForms.EMPTY);
                            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_forms_reset"), false);
                            return 1;
                        })));
    }

    private static int setForm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "form");
        if (!FormService.forceEquip(player, id)) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_form_unknown", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_equipped", id.toString()), false);
        return 1;
    }

    private static int setFormLevel(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "form");
        int level = IntegerArgumentType.getInteger(ctx, "level");
        if (!FormService.setLevel(player, id, level)) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_form_unknown", id.toString()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_level", id.toString(), level), false);
        return 1;
    }

    private static int giveForm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "form");
        if (Definitions.get(player.level(), ModDatapackRegistries.FORM, id).isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("message.mitofus.debug_form_unknown", id.toString()));
            return 0;
        }
        FormService.grant(player, id);
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_granted", id.toString()), false);
        return 1;
    }

    private static int removeForm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        player.setData(ModAttachments.PLAYER_FORMS, FormService.forms(player).withEquipped(java.util.Optional.empty()));
        ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_removed"), false);
        return 1;
    }

    private static int listForms(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = DebugSupport.mitofusPlayer(ctx);
        List<FormCatalogue.Owned> owned = FormService.owned(player);
        if (owned.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_none"), false);
            return 0;
        }
        var equipped = FormService.forms(player).equipped();
        for (FormCatalogue.Owned form : owned) {
            boolean worn = equipped.isPresent() && equipped.get().equals(form.id());
            ctx.getSource().sendSuccess(() -> Component.translatable("message.mitofus.debug_form_line", form.id().toString(),
                    form.form().type().toString(), form.level(), worn ? "*" : ""), false);
        }
        return owned.size();
    }
}
