package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.core.registry.ModAttachments;
import com.orijua.mitofus.feature.mob.MobLevel;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.OptionalInt;

/**
 * The level of the peaceful named characters (Smith, the village chief, Velasco, Mencia), so Jade and the name tag show one
 * like for any mob. The numbers are the mod's own design and live in the config. Apprentices
 * have their own level and are left out. The level is set each time the NPC joins the world, so a config change applies then.
 */
public final class NpcLevels {
    private NpcLevels() {
    }

    public static void register(IEventBus gameBus) {
        gameBus.addListener(NpcLevels::onJoin);
    }

    /** The configured level of this kind of NPC, or empty when it has none. */
    public static OptionalInt levelOf(RuleBalance balance, Class<? extends Entity> type) {
        if (BlacksmithNpc.class.isAssignableFrom(type)) {
            return OptionalInt.of(balance.smithLevel());
        }
        if (VillageChiefNpc.class.isAssignableFrom(type)) {
            return OptionalInt.of(balance.chiefLevel());
        }
        if (VelascoNpc.class.isAssignableFrom(type)) {
            return OptionalInt.of(balance.velascoLevel());
        }
        if (MenciaNpc.class.isAssignableFrom(type)) {
            return OptionalInt.of(balance.menciaLevel());
        }
        return OptionalInt.empty();
    }

    private static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob)) {
            return;
        }
        levelOf(RuleBalance.current(), mob.getClass())
                .ifPresent(level -> mob.setData(ModAttachments.MOB_LEVEL, new MobLevel(level, false)));
    }
}
