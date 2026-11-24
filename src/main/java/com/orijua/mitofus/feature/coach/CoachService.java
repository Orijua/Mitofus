package com.orijua.mitofus.feature.coach;

import com.orijua.mitofus.core.config.CoachBalance;
import com.orijua.mitofus.core.debug.SiteLanding;
import com.orijua.mitofus.feature.economy.CoinPurse;
import com.orijua.mitofus.feature.economy.MoneyText;
import com.orijua.mitofus.feature.economy.Wallet;
import com.orijua.mitofus.feature.quest.QuestService;
import com.orijua.mitofus.feature.vrhelmet.ResidenceService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A trip with the coachman. First the window gets the total fare of each road; when the player accepts, the trip checks the
 * place and the balance, and for a few seconds the player rides blind and still; then the fare is charged and they
 * land on the safe ground of the destination.
 */
public final class CoachService {
    private static final int FADE_IN = 5;
    private static final int FADE_OUT = 15;
    private static final int TICKS_PER_SECOND = 20;
    /** Amplifier of Slowness that leaves no speed at all. */
    private static final int STILL = 6;
    /** Amplifier of Jump Boost that makes the jump impossible (a negative jump). */
    private static final int NO_JUMP = 128;

    /** The trips on the road: who, where to and when they arrive (game time). */
    private static final Map<UUID, Trip> TRIPS = new HashMap<>();

    private record Trip(String destination, long fare, long arrival) {
    }

    private CoachService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(CoachService::onTick);
    }

    /** Sends the window the total fare for each village the coachman can take the player to from here. */
    public static void sendInfo(ServerPlayer player, CoachmanNpc coachman) {
        if (!inReach(player, coachman)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        String origin = ResidenceService.siteNear(level, coachman.blockPosition());
        List<String> destinations = new ArrayList<>();
        List<Long> fares = new ArrayList<>();
        for (String destination : CoachRules.DESTINATIONS) {
            if (!CoachRules.canGo(origin, destination)) {
                continue;
            }
            SiteLanding.Landing landing = SiteLanding.locate(level, destination);
            if (landing.pos().isPresent()) {
                destinations.add(destination);
                fares.add(fare(coachman, landing.pos().get()));
            }
        }
        PacketDistributor.sendToPlayer(player, new CoachInfoPayload(coachman.getId(), destinations, fares));
    }

    /** True if the trip was started. Money is taken only on arrival, and only when there is somewhere to land. */
    public static boolean travel(ServerPlayer player, CoachmanNpc coachman, String destination) {
        if (!inReach(player, coachman) || TRIPS.containsKey(player.getUUID())) {
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
        long fare = fare(coachman, landing.pos().get());
        if (!Wallet.canAfford(player, fare)) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.no_money", MoneyText.iron(fare)), true);
            return false;
        }
        Component place = Component.translatable("site.mitofus." + destination);
        int ticks = CoachBalance.current().travelSeconds() * TICKS_PER_SECOND;
        if (ticks <= 0) {
            return arrive(player, destination, fare);
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, ticks, FADE_OUT));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.mitofus.coach.travelling", place)));
        applyRide(player, ticks + FADE_OUT);
        TRIPS.put(player.getUUID(), new Trip(destination, fare, level.getGameTime() + ticks));
        return true;
    }

    private static void applyRide(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, ticks, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, STILL, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, NO_JUMP, false, false, false));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TRIPS.isEmpty()) {
            return;
        }
        Trip trip = TRIPS.get(player.getUUID());
        if (trip == null) {
            return;
        }
        if (!player.isAlive() || !MitofusWorld.is(player.level())) {
            TRIPS.remove(player.getUUID());
            return;
        }
        if (player.level().getGameTime() >= trip.arrival()) {
            TRIPS.remove(player.getUUID());
            arrive(player, trip.destination(), trip.fare());
        }
    }

    /** The trip's end: charges the fare and lands the player (or tells them it cannot be done and frees them). */
    private static boolean arrive(ServerPlayer player, String destination, long fare) {
        ServerLevel level = player.serverLevel();
        SiteLanding.Landing landing = SiteLanding.locate(level, destination);
        if (landing.pos().isEmpty()) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.no_road"), true);
            endRide(player);
            return false;
        }
        if (!CoinPurse.pay(player, fare)) {
            player.displayClientMessage(Component.translatable("message.mitofus.coach.no_money", MoneyText.iron(fare)), true);
            endRide(player);
            return false;
        }
        BlockPos target = landing.pos().get();
        Component place = Component.translatable("site.mitofus." + destination);
        player.teleportTo(level, target.getX() + 0.5, target.getY(), target.getZ() + 0.5, landing.yaw(), 0.0f);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
        // The blindness stays a moment on arrival, so the player comes out of the dark instead of jumping out of it.
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FADE_OUT, 0, false, false, false));
        player.displayClientMessage(Component.translatable("message.mitofus.coach.arrived", place, MoneyText.iron(fare)), false);
        return true;
    }

    private static void endRide(ServerPlayer player) {
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        player.removeEffect(MobEffects.JUMP);
    }

    private static long fare(CoachmanNpc coachman, BlockPos target) {
        return CoachRules.fareIron(Math.sqrt(coachman.blockPosition().distSqr(target)));
    }

    private static boolean inReach(ServerPlayer player, CoachmanNpc coachman) {
        return MitofusWorld.is(player.level()) && coachman.isAlive()
                && coachman.distanceToSqr(player) <= QuestService.GIVER_REACH * QuestService.GIVER_REACH;
    }
}
