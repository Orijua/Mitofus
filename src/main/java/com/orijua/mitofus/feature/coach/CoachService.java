package com.orijua.mitofus.feature.coach;

import com.orijua.mitofus.core.debug.SiteLanding;
import com.orijua.mitofus.feature.vrhelmet.ResidenceService;
import com.orijua.mitofus.feature.economy.CoinPurse;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** A trip with the coachman: checks the place, the destination and the fare, charges it and takes the player there. */
public final class CoachService {
    private static final int FADE_IN = 5;
    private static final int STAY = 40;
    private static final int FADE_OUT = 15;

    private CoachService() {
    }

    /** True if the trip was made. Money is taken only when there is somewhere to land. */
    public static boolean travel(ServerPlayer player, CoachmanNpc coachman, String destination) {
        if (!MitofusWorld.is(player.level()) || coachman.distanceToSqr(player) > QuestService.GIVER_REACH * QuestService.GIVER_REACH) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        String origin = ResidenceService.siteNear(level, coachman.blockPosition());
        if (!CoachRules.canGo(origin, destination)) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.here"), true);
            return false;
        }
        SiteLanding.Landing landing = SiteLanding.locate(level, destination);
        if (landing.pos().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.no_road"), true);
            return false;
        }
        BlockPos target = landing.pos().get();
        long fare = CoachRules.fareIron(Math.sqrt(coachman.blockPosition().distSqr(target)));
        if (!CoinPurse.pay(player, fare)) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.no_money", MoneyText.iron(fare)), true);
            return false;
        }
        Component place = Component.translatable("site.mitofus." + destination);
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.mitofus.coach.travelling", place)));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, STAY, 0, false, false, false));
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, landing.yaw(), 0.0f);
        player.displayClientMessage(Component.translatable("message.mitofus.coach.arrived", place, MoneyText.iron(fare)), false);
        return true;
    }
}
