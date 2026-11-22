package com.orijua.mitofus.core.debug;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.orijua.mitofus.feature.world.MitofusWorld;
import com.orijua.mitofus.feature.world.MitofusOnly;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;

/** What every operator subcommand of {@code /mitofus} shares: who may run it and the warning outside Mitofus. */
final class DebugSupport {
    /** Permission level 2, as {@code /gamemode}. */
    static final Predicate<CommandSourceStack> OPERATOR = source -> source.hasPermission(2);

    private DebugSupport() {
    }

    /** The player running the command, who must be in Mitofus: otherwise the same warning as the skills and the Mitofus-only systems. */
    static ServerPlayer mitofusPlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!MitofusWorld.is(player.level())) {
            throw new SimpleCommandExceptionType(Component.translatable(MitofusOnly.WARNING_KEY)).create();
        }
        return player;
    }
}
