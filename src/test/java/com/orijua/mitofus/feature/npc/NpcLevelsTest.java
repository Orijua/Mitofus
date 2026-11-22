package com.orijua.mitofus.feature.npc;

import com.orijua.mitofus.core.config.RuleBalance;
import com.orijua.mitofus.feature.eibar.MenciaNpc;
import com.orijua.mitofus.feature.eibar.VelascoNpc;
import net.minecraft.world.entity.monster.Zombie;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcLevelsTest {
    @Test
    void eachNamedNpcGetsItsConfiguredLevel() {
        RuleBalance d = RuleBalance.DEFAULT;
        assertEquals(d.smithLevel(), NpcLevels.levelOf(d, BlacksmithNpc.class).getAsInt());
        assertEquals(d.chiefLevel(), NpcLevels.levelOf(d, VillageChiefNpc.class).getAsInt());
        assertEquals(d.velascoLevel(), NpcLevels.levelOf(d, VelascoNpc.class).getAsInt());
        assertEquals(d.menciaLevel(), NpcLevels.levelOf(d, MenciaNpc.class).getAsInt());
    }

    @Test
    void apprenticesAndMobsAreLeftAlone() {
        assertTrue(NpcLevels.levelOf(RuleBalance.DEFAULT, ApprenticeSmith.class).isEmpty());
        assertTrue(NpcLevels.levelOf(RuleBalance.DEFAULT, Zombie.class).isEmpty());
    }
}
