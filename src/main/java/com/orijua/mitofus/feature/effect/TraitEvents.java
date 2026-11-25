package com.orijua.mitofus.feature.effect;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class TraitEvents {
    private TraitEvents() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(TraitEvents::onIncomingDamage);
    }

    /** The attacker's damage passives (+10 % with swords, at night...). */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.isCanceled() || !(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        double bonus = ActiveTraits.damageBonus(attacker);
        if (bonus != 0) {
            event.setAmount((float) (event.getAmount() * (1 + bonus)));
        }
        com.orijua.mitofus.feature.form.FormService.addUse(attacker);
    }
}
