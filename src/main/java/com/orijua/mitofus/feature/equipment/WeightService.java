package com.orijua.mitofus.feature.equipment;

import com.orijua.mitofus.Mitofus;
import com.orijua.mitofus.core.config.WeightBalance;
import com.orijua.mitofus.feature.progression.ProgressionService;
import com.orijua.mitofus.feature.world.MitofusWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The weight of a smith, once a second in Mitofus and only when {@code weight.weightEnabled}: over the limit (Stamina
 * raises it) the movement speed drops by half the excess and weakness may strike. In the real world there is no weight.
 */
public final class WeightService {
    static final ResourceLocation SLOWDOWN = Mitofus.id("weight_slowdown");
    private static final int CHECK_TICKS = 20;
    private static final int WEAKNESS_TICKS = 60;

    private WeightService() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(WeightService::onTick);
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % CHECK_TICKS == 0) {
            update(player);
        }
    }

    /** Recomputes the player's load and applies (or removes) the slowdown and the chance of weakness. */
    public static void update(ServerPlayer player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        WeightBalance balance = WeightBalance.current();
        if (!balance.enabled() || !MitofusWorld.is(player.level())) {
            speed.removeModifier(SLOWDOWN);
            return;
        }
        long limit = WeightRules.limitGrams(balance, ProgressionService.effectiveStats(player).stamina());
        double excess = WeightRules.excess(ItemWeights.carried(player.getInventory()), limit);
        if (excess <= 0.0) {
            speed.removeModifier(SLOWDOWN);
            return;
        }
        speed.addOrReplacePermanentModifier(new AttributeModifier(SLOWDOWN, -WeightRules.slowdown(excess),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        if (player.getRandom().nextDouble() < WeightRules.weaknessChance(excess)) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAKNESS_TICKS, 0, true, false));
        }
    }
}
